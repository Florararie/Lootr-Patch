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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Shows Lootr's shulker box on unmodded clients as a barrier plus a display entity holding a vanilla
 * shulker box model.
 *
 * <p>The block model Lootr ships for a shulker box has no geometry at all, only a particle texture,
 * because the mod draws the box from its renderer. A client sent the vanilla block and no block
 * entity therefore draws nothing, and a placed shulker is visible only until its chunk reloads.
 * Polymer hides Lootr's block entity, so the vanilla counterpart's entity never arrives either. The
 * barrier collides as a full block and takes the click; the display entity brings geometry of its
 * own.
 *
 * <p>Unlike the pot, which is simply sent as a vanilla pot because vanilla's pot model takes no
 * texture, the shulker box's art does fit a vanilla model. Vanilla's shulker box model type takes a
 * sprite and an {@code openness}, so the pack generates one model per look and lid position pointing
 * at the copy of Lootr's sprite, and the lid rises and falls with Lootr's own opener count, one model
 * position per tick, the way a vanilla shulker box's lid does.
 */
public record PolyLootrShulker(Block block) implements PolymerBlock, BlockWithElementHolder {
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
        return new Holder(world, pos, this.block());
    }

    /** The generated model id for one look of the shulker at one lid position. */
    public static Identifier model(boolean looted, int step) {
        return Identifier.fromNamespaceAndPath(LootrPolymerPatch.ASSETS, modelPath(looted, step));
    }

    /** The pack path of the generated item definition, without the namespace. */
    public static String modelPath(boolean looted, int step) {
        return "shulker/" + (looted ? "opened_" : "") + step;
    }

    /** A stack of the base item, asked to draw one generated model instead of its own. */
    private static ItemStack stack(boolean looted, int step) {
        ItemStack stack = new ItemStack(Items.PURPLE_SHULKER_BOX);
        stack.set(DataComponents.ITEM_MODEL, model(looted, step));
        return stack;
    }

    private static final class Holder extends ElementHolder {
        private final ServerLevel world;
        private final BlockPos pos;
        private final Block block;
        private final ItemDisplayElement element;
        private Identifier model;
        private boolean looted;
        private int step;

        Holder(ServerLevel world, BlockPos pos, Block block) {
            this.world = world;
            this.pos = pos;
            this.block = block;
            this.model = model(false, 0);

            this.element = new ItemDisplayElement(stack(false, 0));
            this.element.setItemDisplayContext(ItemDisplayContext.NONE);
            // A block item is centred on its own origin, so the model already sits in the block and
            // needs neither an offset nor a turn: a shulker box has no facing.
            this.element.setDisplaySize(1f, 1f);
            this.element.setViewRange(1.5f);
            this.element.setTeleportDuration(0);
            this.element.setInvisible(true);
            this.addElement(this.element);
        }

        /**
         * Follows the box every tick: the lid steps one model position per tick toward the current
         * opener count, which is the ramp vanilla uses, and the sprite switches once the box has
         * been looted.
         */
        @Override
        protected void onTick() {
            if (!this.world.isLoaded(this.pos)) {
                return;
            }

            if (this.world.getBlockState(this.pos).getBlock() != this.block) {
                return;
            }

            boolean looted = LootrOpenState.isLooted(this.world, this.pos);
            int target = LootrOpenState.isOpen(this.world, this.pos) ? PolyLootrChest.OPEN_STEPS : 0;
            int step = this.step;
            if (step != target) {
                step += Integer.signum(target - step);
            }

            Identifier model = model(looted, step);
            if (step != this.step || looted != this.looted || !model.equals(this.model)) {
                this.step = step;
                this.looted = looted;
                this.model = model;
                this.element.setItem(stack(looted, step));
            }
        }
    }
}
