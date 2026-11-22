package eu.pb4.lootrpatch.impl;

import eu.pb4.polymer.virtualentity.api.data.DisplayEntityData;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;

/**
 * Shows each player their own model on one shared display entity.
 *
 * <p>Lootr picks its looted sprite per player in its own renderer: a container is gold to a player
 * who has not opened it and silver to one who has. A display entity carries a single item, and its
 * tracker sends that item to everyone watching, so the item is never set through the tracker here.
 * It is sent as one player's data packet whenever that player's model changes instead, which is how
 * one display shows two players two different looks.
 */
final class PlayerDisplay {
    private final ItemDisplayElement element;
    private final Map<UUID, Identifier> shown = new HashMap<>();

    /**
     * @param element the display whose item is sent per player. The item it was constructed with is
     *                consumed here, so the shared tracker never repeats that one model to everyone and
     *                undoes the per-player sends.
     */
    PlayerDisplay(ItemDisplayElement element) {
        this.element = element;
        element.getSyncedData().getDirtyEntries();
    }

    /** Whether this player still has to be shown a model. */
    boolean stale(ServerPlayer player, Identifier model) {
        return !model.equals(this.shown.get(player.getUUID()));
    }

    /** Shows one player a model from now on. */
    void show(ServerPlayer player, Identifier model, ItemStack stack) {
        this.shown.put(player.getUUID(), model);
        player.connection.send(this.packet(stack));
    }

    /** The same, for a player who is being spawned: the packet joins their spawn bundle instead. */
    void show(ServerPlayer player, Identifier model, ItemStack stack,
              Consumer<Packet<ClientGamePacketListener>> bundle) {
        this.shown.put(player.getUUID(), model);
        bundle.accept(this.packet(stack));
    }

    /** Forgets the players who stopped watching, so the map cannot grow without bound. */
    void retain(Collection<ServerGamePacketListenerImpl> watchers) {
        if (this.shown.isEmpty()) {
            return;
        }

        Set<UUID> present = new HashSet<>();
        for (var watcher : watchers) {
            present.add(watcher.getPlayer().getUUID());
        }
        this.shown.keySet().retainAll(present);
    }

    /** One player's view of the display's item: the single display field that is per player. */
    private Packet<ClientGamePacketListener> packet(ItemStack stack) {
        return new ClientboundSetEntityDataPacket(this.element.getEntityId(),
                List.of(SynchedEntityData.DataValue.create(DisplayEntityData.Item.ITEM, stack)));
    }
}
