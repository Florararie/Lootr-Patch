package eu.pb4.lootrpatch.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import noobanidus.mods.lootr.common.api.data.ILootrContainerInstance;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;

/**
 * What Lootr's server-side block entity knows about a container, read the same way for every
 * container this patch draws.
 *
 * <p>It lives in one place because the two open signals are easy to confuse, and a display driven
 * by the wrong one shows the wrong thing. {@link ILootrContainerInstance#getActualOpeners()} is the
 * record of everyone who has ever opened the container: it stays populated for good, so a lid
 * driven by it opens once and never closes. That is what the lid used to follow. Lootr's physical
 * opener count is the live one - its chest increments it in {@code startOpen} and decrements it in
 * {@code stopOpen}, and its own renderer animates from the same count - so it is what
 * {@link #isOpen} reads.
 *
 * <p>Vanilla's own viewer count is the fallback for containers Lootr does not track, and it is
 * correct for the vanilla chests the carrier hands over to.
 */
final class LootrOpenState {
    private LootrOpenState() {
    }

    /**
     * Whether any player currently has the container open.
     *
     * <p>Lootr reports {@code -1} for a container type it does not count, which is why the test is
     * a strict comparison rather than "not zero".
     */
    static boolean isOpen(ServerLevel world, BlockPos pos) {
        var blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ILootrContainerInstance lootr) {
            return lootr.getPhysicalOpenerCount() > 0;
        }
        return blockEntity instanceof ChestBlockEntity chest
                && !chest.getEntitiesWithContainerOpen().isEmpty();
    }

    /** Lootr remembers in its block entity whether its container has been looted. */
    static boolean isLooted(ServerLevel world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof ILootrBlockEntity lootr && lootr.hasBeenOpened();
    }
}
