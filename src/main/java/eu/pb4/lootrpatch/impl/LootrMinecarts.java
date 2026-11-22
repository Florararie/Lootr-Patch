package eu.pb4.lootrpatch.impl;

import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import noobanidus.mods.lootr.common.api.LootrRegistry;

/**
 * Shows Lootr's chest minecart to unmodded clients as vanilla's own chest minecart.
 *
 * <p>Lootr's cart is an entity type of its own, and an entity type travels as a registry id inside
 * the spawn packet. A vanilla client has no entry for a type it does not know, so the packet cannot
 * be read at all and the client drops the connection: summoning one kicks every unmodded player who
 * can see it.
 *
 * <p>It is therefore reported as {@code minecraft:chest_minecart}, and the client draws the cart and
 * the chest on it with its own renderer - the one it uses for a vanilla chest minecart. That is the
 * whole point: the chest is part of the cart's model, so it has vanilla's size, it sits where
 * vanilla puts it, and it turns, tilts and moves with the cart instead of being a second entity that
 * has to be told where the cart went. The cart stays Lootr's on the server, so it keeps its own
 * loot, its own opening and its own breaking rules.
 */
public final class LootrMinecarts {
    private LootrMinecarts() {
    }

    public static void register() {
        EntityType<?> cart = LootrRegistry.getMinecart();
        if (cart == null) {
            LootrPolymerPatch.LOGGER.warn("Lootr's chest minecart entity was not found; it will still drop unmodded clients.");
            return;
        }

        // An unmodded client has no id for lootr:chest_minecart, so the type has to be replaced in
        // the spawn packet before the client is asked to read it.
        registerOverlay(cart);
    }

    @SuppressWarnings("unchecked")
    private static void registerOverlay(EntityType<?> cart) {
        PolymerEntityUtils.registerOverlay((EntityType<Entity>) cart, entity -> OVERLAY);
    }

    /** The replacement itself: the same vehicle, under a type the client can read and draw. */
    private static final PolymerEntity OVERLAY = new PolymerEntity() {
        @Override
        public EntityType<?> getPolymerEntityType(PacketContext context) {
            return EntityType.CHEST_MINECART;
        }
    };
}
