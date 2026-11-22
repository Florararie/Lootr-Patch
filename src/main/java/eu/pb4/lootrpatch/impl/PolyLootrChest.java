package eu.pb4.lootrpatch.impl;

import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Shows one Lootr chest on unmodded clients as an invisible vanilla chest plus a display entity
 * holding Lootr's chest model.
 *
 * <p>Lootr draws its chests in its own block entity renderer, with a sprite per chest type and a
 * second, "opened" sprite for a chest the player has looted. A vanilla client has none of that, so
 * the same art is re-created here with vanilla's chest model type, which takes a sprite, a lid angle
 * and a chest half. The lid animates by stepping that angle, one model per position, the way the
 * sibling chest patches do.
 *
 * <p>Lootr's opened sprite is per player in its own renderer, so it is shown here once the chest has
 * been looted by anyone: one display entity is shared by every player who can see the block, and the
 * chest it draws is the looted one from then on. Everything else - per-player loot, the menu, the
 * advancement, the decay - is unchanged and stays on the server.
 */
public record PolyLootrChest(Block block, LootrBlocks.Chest chest)
        implements PolymerBlock, BlockWithElementHolder {
    /**
     * Rotation of the client model for each {@link #facingIndex} slot, in entity yaw degrees.
     *
     * <p>A chest's facing is the side its lid hinge is on, so its latch points the other way; the
     * model is authored with the latch toward the viewer at yaw 0. Because players expect the latch
     * to face them, the model is turned to look back at the placement direction.
     */
    private static final float[] FACING_YAW = {0f, 90f, 180f, 270f};

    /** Lid positions between closed and fully open, generated into the resource pack per chest. */
    public static final int OPEN_STEPS = 5;

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        // The display entity turns the model, so the carrier only supplies the chest shape and the
        // fluid state, and it is always single so the client asks for the sprite the pack blanks.
        return ChestCarrier.of(state);
    }

    @Override
    public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialState) {
        return true;
    }

    @Override
    public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialState) {
        if (initialState.hasProperty(ChestBlock.TYPE)
                && initialState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            // Lootr only places single chests, so this is only a guard against a future double
            // chest: vanilla draws both halves and the pack only blanks the single sprite.
            return null;
        }
        return new ChestHolder(world, pos, this, initialState);
    }

    /** The generated model id for one look of this chest at one lid position. */
    public static Identifier model(LootrBlocks.Chest chest, boolean opened, int step) {
        return Identifier.fromNamespaceAndPath(LootrPolymerPatch.ASSETS, modelPath(chest.path(), opened, step));
    }

    /** The pack path of the generated item definition, without the namespace. */
    public static String modelPath(String path, boolean opened, int step) {
        return "chest/" + path + (opened ? "_opened" : "") + "/" + step;
    }

    public ItemStack stack(boolean opened, int step) {
        ItemStack stack = new ItemStack(Items.CHEST);
        stack.set(DataComponents.ITEM_MODEL, model(this.chest, opened, step));
        return stack;
    }

    private static int facingIndex(BlockState state) {
        if (!state.hasProperty(ChestBlock.FACING)) {
            return 2;
        }
        return switch (state.getValue(ChestBlock.FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    private static final class ChestHolder extends ElementHolder {
        private final ServerLevel world;
        private final BlockPos pos;
        private final PolyLootrChest chest;
        private final ItemDisplayElement element;
        private Identifier model;
        private boolean opened;
        private int step;
        private float yaw;

        /**
         * Takes the state from the caller instead of reading it: Polymer creates element holders
         * from the chunk constructor while a chunk is being deserialised, and asking the level for
         * a block there re-enters chunk loading. Any stale state is corrected by the first {@link
         * #onTick()}.
         */
        ChestHolder(ServerLevel world, BlockPos pos, PolyLootrChest chest, BlockState state) {
            this.world = world;
            this.pos = pos;
            this.chest = chest;
            this.opened = false;
            this.model = model(chest.chest(), false, 0);
            this.yaw = FACING_YAW[facingIndex(state)];

            this.element = new ItemDisplayElement(chest.stack(false, 0));
            this.element.setItemDisplayContext(ItemDisplayContext.NONE);
            this.element.setDisplaySize(2f, 2f);
            this.element.setViewRange(1.5f);
            this.element.setTeleportDuration(0);
            this.element.setInvisible(true);
            this.element.setYaw(this.yaw);
            this.addElement(this.element);
        }

        /**
         * Follows the chest every tick: the lid steps one model position per tick toward the
         * current opener count, which is the same five-tick ramp vanilla uses, and the sprite
         * switches once the chest has been looted.
         */
        @Override
        protected void onTick() {
            if (!this.world.isLoaded(this.pos)) {
                return;
            }

            BlockState state = this.world.getBlockState(this.pos);
            if (state.getBlock() != this.chest.block()) {
                return;
            }

            boolean opened = this.isLooted();
            int target = this.isOpen() ? OPEN_STEPS : 0;
            int step = this.step;
            if (step != target) {
                step += Integer.signum(target - step);
            }

            Identifier model = model(this.chest.chest(), opened, step);
            if (step != this.step || opened != this.opened || !model.equals(this.model)) {
                this.step = step;
                this.opened = opened;
                this.model = model;
                this.element.setItem(this.chest.stack(opened, step));
            }

            float yaw = FACING_YAW[facingIndex(state)];
            if (yaw != this.yaw) {
                this.yaw = yaw;
                this.element.setYaw(yaw);
            }
        }

        private boolean isLooted() {
            return LootrOpenState.isLooted(this.world, this.pos);
        }

        /**
         * Whether any player currently has the chest open.
         *
         * <p>Asked through {@link LootrOpenState} so the lid and the looted look are read the same
         * way for every container here. Lootr's own renderer animates from the same opener count.
         */
        private boolean isOpen() {
            return LootrOpenState.isOpen(this.world, this.pos);
        }
    }
}
