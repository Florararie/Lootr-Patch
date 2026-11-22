package eu.pb4.lootrpatch.impl;

import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Shows a Lootr barrel on unmodded clients as a barrier plus a display entity holding Lootr's model.
 *
 * <p>A barrel is a full block, so unlike a chest it does not need the chest's inset box: a barrier
 * collides exactly like the barrel does, and it draws nothing on its own. What a vanilla client
 * cannot see is Lootr's barrel art, because Lootr's blockstate picks between four custom block
 * models - unopened and opened, each with the lid closed or open - and the loader-specific blockstate
 * type it uses is not something the client can read.
 *
 * <p>Those four models are ordinary block models, though, so the display entity draws each of them
 * directly. An item draws a block model centred on itself, so the model already sits in the
 * barrel's own block and only the facing has to be applied.
 */
public record PolyLootrBarrel(Block block) implements PolymerBlock, BlockWithElementHolder {
    /**
     * The four models Lootr uses, named as they are in its assets.
     *
     * <p>The closed and open lid are separate models rather than an animated angle, so following
     * the block's {@code open} property is enough to show a barrel opening.
     */
    public static final List<String> MODELS = List.of(
            "barrel_unopened", "barrel_unopened_open", "opened_barrel", "opened_barrel_open");

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return Blocks.BARRIER.defaultBlockState();
    }

    @Override
    public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialState) {
        return true;
    }

    @Override
    public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialState) {
        return new BarrelHolder(world, pos, this, initialState);
    }

    /** The item definition id for one of {@link #MODELS}. */
    public static Identifier model(String model) {
        return Identifier.fromNamespaceAndPath(LootrPolymerPatch.ASSETS, modelPath(model));
    }

    public static String modelPath(String model) {
        return "barrel/" + model;
    }

    private static String modelName(boolean opened, boolean open) {
        if (opened) {
            return open ? "opened_barrel_open" : "opened_barrel";
        }
        return open ? "barrel_unopened_open" : "barrel_unopened";
    }

    /**
     * Yaw that turns the lid toward one of the four horizontal facings.
     *
     * <p>Item models are drawn turned around relative to block models, so these are the yaws a
     * chest uses for the same facing, not the angles Lootr's blockstate applies to its model.
     */
    private static float yaw(Direction facing) {
        return switch (facing) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    /**
     * Pitch that lays the lid toward a horizontal facing, and leaves it on top for a barrel placed
     * upright or hanging under a block.
     *
     * <p>The sign is negative because a positive pitch tips the model's top away from the player,
     * which leaves the lid on the far side of the barrel.
     */
    private static float pitch(Direction facing) {
        return switch (facing) {
            case UP, DOWN -> 0f;
            default -> -90f;
        };
    }

    /** A half turn about the side axis, which is how a barrel hanging under a block is drawn. */
    private static final Quaternionf FLIPPED = new Quaternionf().rotateX((float) Math.PI);
    private static final Quaternionf UPRIGHT = new Quaternionf();

    private static final class BarrelHolder extends ElementHolder {
        private final ServerLevel world;
        private final BlockPos pos;
        private final PolyLootrBarrel barrel;
        private final ItemDisplayElement element;
        private final PlayerDisplay models;
        private boolean open;
        private Direction facing;
        private int skyLight = -1;
        private int blockLight = -1;

        BarrelHolder(ServerLevel world, BlockPos pos, PolyLootrBarrel barrel, BlockState state) {
            this.world = world;
            this.pos = pos;
            this.barrel = barrel;
            this.facing = facing(state);
            this.open = open(state);

            this.element = new ItemDisplayElement(stack(modelName(false, this.open)));
            this.element.setItemDisplayContext(ItemDisplayContext.NONE);
            this.element.setDisplaySize(1f, 1f);
            this.element.setViewRange(1.5f);
            this.element.setTeleportDuration(0);
            this.element.setInvisible(true);
            this.applyRotation();
            this.addElement(this.element);
            this.models = new PlayerDisplay(this.element);
        }

        @Override
        protected void onTick() {
            if (!this.world.isLoaded(this.pos)) {
                return;
            }

            BlockState state = this.world.getBlockState(this.pos);
            if (state.getBlock() != this.barrel.block()) {
                return;
            }

            this.applyBrightness();

            for (var watcher : this.getWatchingPlayers()) {
                ServerPlayer player = watcher.getPlayer();
                boolean looted = LootrOpenState.isLootedBy(this.world, this.pos, player);
                this.show(player, looted, open(state));
                if (!looted) {
                    UnopenedParticles.sparkle(this.world, player, this.motePoint(), 0.25);
                }
            }
            this.models.retain(this.getWatchingPlayers());

            Direction facing = facing(state);
            if (facing != this.facing) {
                this.facing = facing;
                this.applyRotation();
            }
        }

        /**
         * Lights the display from the space around the barrel instead of from the barrel's own block.
         *
         * <p>A client draws a display entity with the light of the block it stands in, and this one
         * always stands inside the barrel, which is a solid block. A solid block has no light inside
         * it, and a vanilla client is sent those light values with the chunk and never revises them
         * for a block that does not change, so a barrel that was placed while its own cell still
         * counted as air comes back from a rejoin in the dark and only lights up once some other
         * block update makes the client rebuild the light around it.
         *
         * <p>Taking the brightest of the six neighbouring cells is how bright the space a barrel
         * stands in is, which is the light a player expects a barrel to be drawn with. The brightness
         * is part of the display's synced data, so it is already correct when the entity is spawned,
         * and it only costs a packet when the light around the barrel actually changes.
         */
        private void applyBrightness() {
            int sky = 0;
            int block = 0;
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = this.pos.relative(direction);
                sky = Math.max(sky, this.world.getBrightness(LightLayer.SKY, neighbour));
                block = Math.max(block, this.world.getBrightness(LightLayer.BLOCK, neighbour));
            }
            if (sky != this.skyLight || block != this.blockLight) {
                this.skyLight = sky;
                this.blockLight = block;
                this.element.setBrightness(new Brightness(block, sky));
            }
        }

        private void applyRotation() {
            this.element.setYaw(yaw(this.facing));
            this.element.setPitch(pitch(this.facing));
            this.element.setLeftRotation(this.facing == Direction.DOWN ? FLIPPED : UPRIGHT);
        }

        /**
         * Gives one player the barrel as they should see it: silver once they have looted it, gold
         * until then, with the lid as open as everyone sees it.
         */
        private void show(ServerPlayer player, boolean looted, boolean open) {
            String name = modelName(looted, open);
            Identifier model = model(name);
            if (this.models.stale(player, model)) {
                this.models.show(player, model, stack(name));
            }
        }

        /** Where Lootr's unlooted motes start: across the lid, a little above the barrel. */
        private Vec3 motePoint() {
            return new Vec3(this.pos.getX() + 0.5, this.pos.getY() + 1.1, this.pos.getZ() + 0.5);
        }

        /** Sends a player who has just been given the barrel their own look with its spawn packet. */
        @Override
        protected void startWatchingExtraPackets(ServerGamePacketListenerImpl watcher,
                                                 Consumer<Packet<ClientGamePacketListener>> bundle) {
            ServerPlayer player = watcher.getPlayer();
            boolean looted = LootrOpenState.isLootedBy(this.world, this.pos, player);
            String name = modelName(looted, open(this.world.getBlockState(this.pos)));
            this.models.show(player, model(name), stack(name), bundle);
        }

        private static Direction facing(BlockState state) {
            return state.hasProperty(BarrelBlock.FACING) ? state.getValue(BarrelBlock.FACING) : Direction.UP;
        }

        private static boolean open(BlockState state) {
            return state.hasProperty(BarrelBlock.OPEN) && state.getValue(BarrelBlock.OPEN);
        }

        private static ItemStack stack(String model) {
            ItemStack stack = new ItemStack(Items.BARREL);
            stack.set(DataComponents.ITEM_MODEL, PolyLootrBarrel.model(model));
            return stack;
        }
    }
}
