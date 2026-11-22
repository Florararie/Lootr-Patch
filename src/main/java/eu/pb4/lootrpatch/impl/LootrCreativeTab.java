package eu.pb4.lootrpatch.impl;

import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.core.impl.InternalServerRegistry;
import eu.pb4.polymer.core.mixin.CreativeModeTabAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Makes Lootr's creative tab reach clients that do not have the mod, the same way the sibling
 * patches do it.
 *
 * <p>Polymer iterates every registered tab when it builds the tab packet, but it only sends the
 * <em>definition</em> - the packet that makes the client create the tab at all - for tabs it knows
 * about in {@code PolymerCreativeModeTabUtils.REGISTRY}. Lootr's tab lives in {@code
 * BuiltInRegistries.CREATIVE_MODE_TAB}, not in that registry, so Polymer sends its contents and skips
 * its definition; the client then drops the contents because it has no such tab. Registering the tab
 * under its real id is what makes the button appear.
 *
 * <p>Two details matter. The tab keeps its real id, because a client that <em>does</em> have Lootr
 * already has that tab registered and would grow a duplicate under a patch-owned id. And the icon
 * needs replacing: Polymer writes {@code group.getIconItem()} into the definition with the plain
 * item stack codec, and Lootr's icon is its trophy - an item a vanilla client cannot resolve, so the
 * button would come out blank.
 */
public final class LootrCreativeTab {
    private static final List<CreativeModeTab> TABS = new ArrayList<>();

    private LootrCreativeTab() {
    }

    /** Serves one of Lootr's tabs to Polymer clients. Call once Lootr has registered it. */
    public static void track(CreativeModeTab tab) {
        if (tab == null) {
            LootrPolymerPatch.LOGGER.warn("Lootr has no creative tab yet, so it cannot be served");
            return;
        }

        TABS.add(tab);

        var key = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElse(null);
        if (key == null) {
            LootrPolymerPatch.LOGGER.warn("Lootr's creative tab is not registered, so it cannot be served");
            return;
        }

        // The vanilla registry refuses this id because Lootr already owns it, so the tab goes into
        // the registry Polymer actually reads.
        InternalServerRegistry.ITEM_GROUPS.set(key.identifier(), tab);
        LootrPolymerPatch.LOGGER.info("Serving creative tab {} to Polymer clients", key.identifier());
    }

    /**
     * Replaces a tab icon a vanilla client cannot decode with a chest carrying the loot chest art.
     *
     * <p>Runs on {@code SERVER_STARTED}: item components are not bound during mod initialisation,
     * and reading an item's registry id before that throws.
     */
    public static void applyClientIcon() {
        for (var tab : TABS) {
            var current = tab.getIconItem();
            if (current == null || current.isEmpty() || isClientSafe(current)) {
                continue;
            }

            var replacement = clientIcon(current);
            ((CreativeModeTabAccessor) tab).setIconItemStack(replacement);
            LootrPolymerPatch.LOGGER.info("Creative tab icon {} -> {}",
                    BuiltInRegistries.ITEM.getKey(current.getItem()),
                    BuiltInRegistries.ITEM.getKey(replacement.getItem()));
        }
    }

    private static boolean isClientSafe(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null || id.getNamespace().equals("minecraft");
    }

    /**
     * Lootr's icon is the trophy, which has no vanilla item to stand in for it; the chest is what
     * the tab mostly contains and is a vanilla item, with the loot chest model on top so it still
     * looks like one.
     */
    private static ItemStack clientIcon(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals(LootrPolymerPatch.TARGET_MOD_ID)) {
            return stack;
        }

        Identifier model = PolyLootrChest.model(LootrBlocks.CHESTS.getFirst(), false, 0);
        ItemStack out = new ItemStack(Items.CHEST);
        out.set(DataComponents.ITEM_MODEL, model);
        return out;
    }
}
