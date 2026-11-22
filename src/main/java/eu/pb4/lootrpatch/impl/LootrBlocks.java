package eu.pb4.lootrpatch.impl;

import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

/**
 * The Lootr blocks this patch virtualises, found by id.
 *
 * <p>Everything is looked up by id rather than by class, so a Lootr version bump that keeps its
 * ids cannot break the patch; the only mod type it names is the block entity interface that says
 * whether a container has been looted.
 */
public final class LootrBlocks {
    /**
     * A Lootr chest block and the chest material Lootr paints it with.
     *
     * <p>The chest model type names its art the way vanilla's own chest item does: relative to the
     * chest sprite directory, so {@code normal} means {@code entity/chest/normal} in the chest
     * atlas. Writing the directory out here would be read as a path below it and find nothing.
     */
    public record Chest(String path, String material) {
        /** @param opened the looted look, which Lootr ships as a separate sprite */
        public String texture(boolean opened) {
            return "lootr:" + this.material + (opened ? "_opened" : "");
        }
    }

    public static final List<Chest> CHESTS = List.of(
            new Chest("chest", "normal"),
            new Chest("trapped_chest", "trapped"),
            new Chest("copper_chest", "copper"),
            new Chest("exposed_copper_chest", "copper_exposed"),
            new Chest("weathered_copper_chest", "copper_weathered"),
            new Chest("oxidized_copper_chest", "copper_oxidized"));

    /** The vanilla item each Lootr chest item is sent as. */
    private static final List<Item> CHEST_BASES = List.of(
            Items.CHEST, Items.TRAPPED_CHEST, Items.COPPER_CHEST,
            Items.EXPOSED_COPPER_CHEST, Items.WEATHERED_COPPER_CHEST, Items.OXIDIZED_COPPER_CHEST);

    private LootrBlocks() {
    }

    public static void register() {
        var virtualised = new ArrayList<Block>();

        for (var i = 0; i < CHESTS.size(); i++) {
            var chest = CHESTS.get(i);
            Block block = block(chest.path());
            if (block == null) {
                LootrPolymerPatch.LOGGER.error("Lootr has no block lootr:{}, skipping it", chest.path());
                continue;
            }

            var poly = new PolyLootrChest(block, chest);
            // One overlay for the client block state and one for the display entity drawing it.
            PolymerBlock.registerOverlay(block, poly);
            BlockWithElementHolder.registerOverlay(block, poly);
            virtualised.add(block);

            item(chest.path(), CHEST_BASES.get(i), PolyLootrChest.model(chest, false, 0));
        }

        registerBarrel(virtualised);
        registerTrophy(virtualised);
        registerShulker(virtualised);
        registerSimple(virtualised, "decorated_pot", Items.DECORATED_POT, "minecraft:decorated_pot");
        registerSimple(virtualised, "suspicious_sand", Items.SUSPICIOUS_SAND, "minecraft:suspicious_sand");
        registerSimple(virtualised, "suspicious_gravel", Items.SUSPICIOUS_GRAVEL, "minecraft:suspicious_gravel");

        // The inventory block is a technical block with no model; a barrier is the closest thing a
        // vanilla client can be sent for it, and it only shows up in creative or via the command.
        registerSimple(virtualised, "inventory", Items.BARRIER, "minecraft:barrier");

        hideBlockEntities(virtualised);

        LootrPolymerPatch.LOGGER.info("Virtualised {} Lootr blocks", virtualised.size());
    }

    private static void registerBarrel(List<Block> virtualised) {
        Block block = block("barrel");
        if (block == null) {
            LootrPolymerPatch.LOGGER.error("Lootr has no block lootr:barrel, skipping it");
            return;
        }

        var poly = new PolyLootrBarrel(block);
        PolymerBlock.registerOverlay(block, poly);
        BlockWithElementHolder.registerOverlay(block, poly);
        virtualised.add(block);
        item("barrel", Items.BARREL, PolyLootrBarrel.model(PolyLootrBarrel.MODELS.getFirst()));
    }

    private static void registerTrophy(List<Block> virtualised) {
        Block block = block("trophy");
        if (block == null) {
            LootrPolymerPatch.LOGGER.error("Lootr has no block lootr:trophy, skipping it");
            return;
        }

        var poly = new PolyLootrTrophy(block);
        PolymerBlock.registerOverlay(block, poly);
        BlockWithElementHolder.registerOverlay(block, poly);
        virtualised.add(block);
        // A chest is the base item only because it is a block item every client knows how to draw
        // and place; the model override makes the icon the trophy itself.
        item("trophy", Items.CHEST, PolyLootrTrophy.itemModel());
    }

    /**
     * Lootr's shulker box, drawn like the pot but with the mod's own art and its lid animation.
     *
     * <p>Its block model has no more geometry than the pot's, so it is a barrier plus a display
     * entity too. The difference is that a shulker box's art does fit a vanilla model: vanilla's
     * shulker box model type takes a sprite and an openness, so the pack points it at a copy of
     * Lootr's sprite and the lid follows Lootr's opener count.
     */
    private static void registerShulker(List<Block> virtualised) {
        Block block = block("shulker_box");
        if (block == null) {
            LootrPolymerPatch.LOGGER.error("Lootr has no block lootr:shulker_box, skipping it");
            return;
        }

        var poly = new PolyLootrShulker(block);
        PolymerBlock.registerOverlay(block, poly);
        BlockWithElementHolder.registerOverlay(block, poly);
        virtualised.add(block);
        item("shulker_box", Items.PURPLE_SHULKER_BOX, PolyLootrShulker.model(false, 0));
    }

    /**
     * Blocks that are shown as their vanilla counterpart, with no display entity at all.
     *
     * <p>Lootr's brushables already reuse vanilla geometry and vanilla client behaviour, and their
     * only state is how far they have been brushed, so mapping the state across keeps the shape and
     * the dust. Its pot is drawn by the client from its block state as well, so the vanilla pot keeps
     * the shape, the facing and the water; only the art stays vanilla's. The container contents stay
     * on the server.
     */
    private static void registerSimple(List<Block> virtualised, String path, Item base, String clientModel) {
        Block block = block(path);
        if (block == null) {
            LootrPolymerPatch.LOGGER.error("Lootr has no block lootr:{}, skipping it", path);
            return;
        }

        PolymerBlock.registerOverlay(block, PolyVanillaState.of(path, block));
        virtualised.add(block);
        item(path, base, Identifier.parse(clientModel));
    }

    /**
     * Polymerises one Lootr item so a vanilla client can hold and place it.
     *
     * @param model the item model definition the client draws, from the pack
     */
    private static void item(String path, Item base, Identifier model) {
        var id = Identifier.fromNamespaceAndPath(LootrPolymerPatch.TARGET_MOD_ID, path);

        // The item registry is defaulted, so an id with no item resolves to air instead of null.
        // Overlaying air would turn every empty stack in every packet into a Lootr item, and
        // lootr:inventory genuinely has no item.
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            LootrPolymerPatch.LOGGER.debug("Lootr has no item lootr:{}, so it gets none", path);
            return;
        }

        // Lootr's language files are in the pack, so the block item's own description id is what
        // turns into the right name.
        PolymerItem.registerOverlay(BuiltInRegistries.ITEM.getValue(id), new PolyLootrItem(base, model));
    }

    /**
     * Keeps Lootr's block entities on the server.
     *
     * <p>A vanilla client cannot decode a block entity type it does not know, and Lootr's types are
     * not in its registry: placing a chest broadcasts an update packet naming the type, and such a
     * packet would disconnect it. Registering the types with Polymer drops those packets and leaves
     * them out of chunk packets, so the client keeps none of Lootr's block entities.
     */
    private static void hideBlockEntities(List<Block> blocks) {
        var types = new LinkedHashSet<BlockEntityType<?>>();
        for (var type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            for (var block : blocks) {
                if (type.isValid(block.defaultBlockState())) {
                    types.add(type);
                    break;
                }
            }
        }

        types.forEach(PolymerBlockUtils::registerBlockEntity);
        LootrPolymerPatch.LOGGER.info("Hid {} Lootr block entity types from vanilla clients", types.size());
    }

    public static @Nullable Block block(String path) {
        var id = Identifier.fromNamespaceAndPath(LootrPolymerPatch.TARGET_MOD_ID, path);
        // Same defaulted-registry trap as items: an unknown block reads as air, not null.
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
    }
}
