package eu.pb4.lootrpatch.impl;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Client representation of one Lootr block item.
 *
 * <p>The sent item is a vanilla item every client can hold and place; the art comes from the item
 * model override, because the client resolves a model from the item's model component and the
 * plain vanilla item would draw its own.
 *
 * <p>The name stays translatable: Lootr's language files are part of the pack, so
 * {@code block.lootr.chest} turns into "Chest" and is translated like any vanilla item.
 */
public record PolyLootrItem(Item clientItem, Identifier model) implements PolymerItem {
    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return this.clientItem;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context, HolderLookup.Provider lookup) {
        return this.model;
    }

    @Override
    public ItemStack getPolymerItemStack(ItemStack itemStack, TooltipFlag tooltipType, PacketContext context,
                                         HolderLookup.Provider lookup) {
        // Empty slots are encoded through this path too, and an empty stack has no item to
        // convert; handing it to Polymer's converter makes it store air, which its codec rejects.
        if (itemStack.isEmpty()) {
            return itemStack;
        }

        ItemStack out = PolymerItemUtils.createItemStack(itemStack, tooltipType, context, lookup);
        // A block item's description id is block.lootr.<id>, which is exactly what Lootr's
        // language files translate.
        out.set(DataComponents.ITEM_NAME, Component.translatable(itemStack.getItem().getDescriptionId()));
        return out;
    }

    @Override
    public boolean isPolymerBlockInteraction(BlockState state, ServerPlayer player, InteractionHand hand,
                                             ItemStack stack, ServerLevel world, BlockHitResult blockHitResult,
                                             InteractionResult actionResult) {
        return true;
    }

    @Override
    public boolean isIgnoringBlockInteractionPlaySoundExceptedEntity(BlockState state, ServerPlayer player,
                                                                     InteractionHand hand, ItemStack stack,
                                                                     ServerLevel world, BlockHitResult blockHitResult) {
        return true;
    }
}
