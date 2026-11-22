package eu.pb4.lootrpatch.impl;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * The vanilla block an unmodded client is sent in place of a Lootr chest.
 *
 * <p>A client takes collision, the hitbox and fluid rendering from the block state it is sent, and
 * the only vanilla states with a chest's own 14x14x14 box are chests. The sibling patches already
 * spend two of them: the Iron Chests patch uses copper chests and the More Chest Variants patch
 * uses trapped chests, so this one takes <b>exposed copper chests</b>, which neither touches. Its
 * single sprite is blanked in the pack and single exposed copper chests are redrawn by {@link
 * VanillaExposedCopperChests}, exactly as the sibling patches redraw theirs.
 *
 * <p>Waxed and unwaxed exposed copper chests share one sprite, so both blocks are redrawn. The
 * half sprites are left alone, so double chests are still drawn by vanilla and nothing here draws
 * over them.
 *
 * <p>The type is always {@link ChestType#SINGLE}, which keeps the client's own chest entity asking
 * for the single sprite this patch blanks. Waterlogged is copied from the real chest so a loot chest
 * under water still shows water.
 */
public final class ChestCarrier {
    /** The dry carrier. */
    public static final BlockState STATE = create(false);

    /** The same carrier for a chest that is under water. */
    private static final BlockState WATERLOGGED = create(true);

    private static BlockState create(boolean waterlogged) {
        return Blocks.EXPOSED_COPPER_CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.SINGLE)
                .setValue(ChestBlock.WATERLOGGED, waterlogged);
    }

    /**
     * The carrier for one chest, waterlogged exactly when that chest is.
     *
     * <p>Lootr's chests extend {@link ChestBlock} and so carry its waterlogged property; the check
     * is only for a future version that reworks the block without it.
     */
    public static BlockState of(BlockState chest) {
        return chest.hasProperty(ChestBlock.WATERLOGGED) && chest.getValue(ChestBlock.WATERLOGGED)
                ? WATERLOGGED
                : STATE;
    }

    private ChestCarrier() {
    }
}
