package eu.pb4.lootrpatch;

import eu.pb4.lootrpatch.impl.LootrBlocks;
import eu.pb4.lootrpatch.impl.LootrCreativeTab;
import eu.pb4.lootrpatch.impl.LootrItemFrames;
import eu.pb4.lootrpatch.impl.LootrMinecarts;
import eu.pb4.lootrpatch.impl.LootrPack;
import eu.pb4.lootrpatch.impl.VanillaExposedCopperChests;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import noobanidus.mods.lootr.common.api.LootrRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LootrPolymerPatch implements ModInitializer {
    public static final String MOD_ID = "lootr-polymer-patch";
    /** The patched mod, whose textures and translations the patch draws from. */
    public static final String TARGET_MOD_ID = "lootr";
    /** Namespace for the chest and barrel models this patch generates into the pack. */
    public static final String ASSETS = "lootrpatch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Lootr's own textures are what the client draws a chest, barrel or trophy with, so its
        // assets travel in the pack: without them the display entities would have no art and the
        // chests would keep their modded names.
        if (!PolymerResourcePackUtils.addModAssets(TARGET_MOD_ID)) {
            LOGGER.warn("Lootr assets were not found on the classpath; chests will have no textures or names.");
        }

        // Chest sprites live in a vanilla atlas and the chest model type names a sprite, so the
        // patch adds the copper sprites Lootr does not list, blanks the carrier's sprite and writes
        // one model per chest look and lid position.
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(LootrPack::write);

        // The carrier is a vanilla exposed copper chest and its single sprite is blanked, so single
        // vanilla exposed copper chests have to be drawn again by display entities. Without this the
        // blanked sprite is the only thing they have and they are invisible.
        VanillaExposedCopperChests.register();

        // Lootr's blocks and items are registered by the time this runs, because this mod depends
        // on Lootr. Overlaying them here means no mixin is needed.
        LootrBlocks.register();

        // Lootr's loot frames are entities rather than blocks, so Polymer's block replacement never
        // covered them: an unmodded client was sent a type id it cannot read and dropped the
        // connection. They reach it as vanilla item frames instead.
        LootrItemFrames.register();

        // Lootr's chest minecart is an entity of its own as well, so summoning one sent an
        // unreadable spawn packet and dropped the connection. It reaches clients as a vanilla cart.
        LootrMinecarts.register();

        // Lootr's own tab is in the vanilla registry, which Polymer does not treat as a tab it may
        // define for unmodded clients, so it is registered by hand under its real id.
        LootrCreativeTab.track(LootrRegistry.getTab());
        // A tab icon is an item stack, and item components only exist once the registries are
        // bound, so the client-side icon is swapped in when the server starts.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> LootrCreativeTab.applyClientIcon());

        LOGGER.info("Lootr Polymer patch loaded: loot containers are now server-side only.");
    }
}
