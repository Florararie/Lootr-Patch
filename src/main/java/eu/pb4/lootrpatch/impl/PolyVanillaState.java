package eu.pb4.lootrpatch.impl;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Shows a Lootr block as its vanilla counterpart, with no display entity at all.
 *
 * <p>This is for blocks a client can draw from its block state. Lootr's brushables are the vanilla
 * suspicious sand and gravel with a loot table, and their dust is a block state property, so mapping
 * the state across keeps the shape and the brushed dust. Lootr's decorated pot is a vanilla pot in
 * everything but its art, so it is sent as a vanilla pot too and the client's own pot renderer draws
 * it: the same shape, the same facing, water behind it when it is waterlogged, and a real pot rather
 * than a barrier. Only the contents stay on the server.
 *
 * <p>Lootr's shulker box is deliberately not here: its whole appearance comes from the mod's block
 * entity renderer, and a client with no block entity draws nothing at all, so it needs the display
 * stand-in in {@link PolyLootrShulker} instead.
 */
public record PolyVanillaState(String path, Block block) implements PolymerBlock {
    public static PolyVanillaState of(String path, Block block) {
        return new PolyVanillaState(path, block);
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return switch (this.path) {
            case "suspicious_sand" -> brushable(Blocks.SUSPICIOUS_SAND, state);
            case "suspicious_gravel" -> brushable(Blocks.SUSPICIOUS_GRAVEL, state);
            case "decorated_pot" -> pot(state);
            // The inventory block has no model of its own, so a barrier is what is left.
            default -> Blocks.BARRIER.defaultBlockState();
        };
    }

    /**
     * A pot keeps the way it faces and the water it sits in, which is everything a vanilla client
     * needs to draw it. A vanilla pot's other states are its sherd pattern, which lives in the block
     * entity, and its crack overlay, which only a broken vanilla pot wears.
     */
    private static BlockState pot(BlockState state) {
        BlockState out = Blocks.DECORATED_POT.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            out = out.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            out = out.setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
        }
        return out;
    }

    /** A brushable keeps how far it has been brushed, which is the only state it has. */
    private static BlockState brushable(Block vanilla, BlockState state) {
        BlockState out = vanilla.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.DUSTED)) {
            out = out.setValue(BlockStateProperties.DUSTED, state.getValue(BlockStateProperties.DUSTED));
        }
        return out;
    }
}
