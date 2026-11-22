package eu.pb4.lootrpatch.impl;

import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Shows the Lootr trophy on unmodded clients as a barrier plus a display entity holding its model.
 *
 * <p>The trophy is a small statue with a direction, and Lootr ships it as a plain block model, so
 * the model can be drawn as an item like the barrel's. The barrier is the carrier because a trophy
 * is not a container and has no shape a client could be given; the trade-off is that its collision
 * is a full block instead of the statue's own box.
 */
public record PolyLootrTrophy(Block block) implements PolymerBlock, BlockWithElementHolder {
    /** Item definition holding the trophy block model. */
    public static final String MODEL = "trophy";

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
        return new TrophyHolder(world, pos, this, initialState);
    }

    /** The item definition holding the trophy block model. */
    public static Identifier itemModel() {
        return Identifier.fromNamespaceAndPath(LootrPolymerPatch.ASSETS, MODEL);
    }

    /** The yaw Lootr's own blockstate gives each facing. */
    private static float yaw(Direction facing) {
        return switch (facing) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    private static final class TrophyHolder extends ElementHolder {
        private final ServerLevel world;
        private final BlockPos pos;
        private final PolyLootrTrophy trophy;
        private final ItemDisplayElement element;
        private Direction facing;

        TrophyHolder(ServerLevel world, BlockPos pos, PolyLootrTrophy trophy, BlockState state) {
            this.world = world;
            this.pos = pos;
            this.trophy = trophy;
            this.facing = facing(state);

            ItemStack stack = new ItemStack(Items.CHEST);
            stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(
                    LootrPolymerPatch.ASSETS, MODEL));

            this.element = new ItemDisplayElement(stack);
            this.element.setItemDisplayContext(ItemDisplayContext.NONE);
            this.element.setDisplaySize(1f, 1f);
            this.element.setViewRange(1.5f);
            this.element.setTeleportDuration(0);
            this.element.setInvisible(true);
            // An item holds a block model centred on itself, so the model already sits in the
            // block and needs no offset.
            this.element.setYaw(yaw(this.facing));
            this.addElement(this.element);
        }

        @Override
        protected void onTick() {
            if (!this.world.isLoaded(this.pos)) {
                return;
            }

            BlockState state = this.world.getBlockState(this.pos);
            if (state.getBlock() != this.trophy.block()) {
                return;
            }

            Direction facing = facing(state);
            if (facing != this.facing) {
                this.facing = facing;
                this.element.setYaw(yaw(facing));
            }
        }

        private static Direction facing(BlockState state) {
            return state.hasProperty(HorizontalDirectionalBlock.FACING)
                    ? state.getValue(HorizontalDirectionalBlock.FACING)
                    : Direction.NORTH;
        }
    }
}
