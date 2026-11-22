package eu.pb4.lootrpatch.impl;

import com.google.gson.JsonObject;
import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the chest carrier from being seen while vanilla exposed copper chests stay visible.
 *
 * <p>A client builds the chest block entity for a chest state itself, from the block update that
 * places it, and the chest renderer draws a chest from that entity. The only way to hide the
 * carrier's own chest is therefore to blank the sprite it asks for, {@code
 * textures/entity/chest/copper_exposed.png}.
 *
 * <p>Blanking that sprite would hide single vanilla exposed copper chests too, so they are redrawn
 * here: both the waxed and the unwaxed block get a display entity at the loot chest's own
 * proportions, drawn with Lootr's exposed copper sprite, which is the same weathered copper chest
 * with Lootr's gold trim. The half sprites are left alone, so a double exposed copper chest is
 * still drawn by vanilla and nothing here draws over it: placing the second half of a chest does
 * not change the block, so the display entity stays and has to take itself away.
 *
 * <p>Only {@link BlockWithElementHolder} is registered for these blocks, never a {@link
 * eu.pb4.polymer.core.api.block.PolymerBlock}: remapping the state of a vanilla chest would feed
 * its own state back into the carrier.
 */
public final class VanillaExposedCopperChests {
    /**
     * The chest material vanilla exposed copper chests are redrawn with.
     *
     * <p>Named relative to the {@code entity/chest} sprite directory, as the chest model type
     * expects, which is also how Lootr's own chest item names it.
     */
    private static final String MATERIAL = "lootr:copper_exposed";

    /** The vanilla item models that name the blanked sprite. */
    private static final String[] ITEM_MODELS = {
            "assets/minecraft/items/exposed_copper_chest.json",
            "assets/minecraft/items/waxed_exposed_copper_chest.json"};

    private VanillaExposedCopperChests() {
    }

    public static void register() {
        var overlay = new Overlay();
        BlockWithElementHolder.registerOverlay(net.minecraft.world.level.block.Blocks.EXPOSED_COPPER_CHEST, overlay);
        BlockWithElementHolder.registerOverlay(net.minecraft.world.level.block.Blocks.WAXED_EXPOSED_COPPER_CHEST, overlay);
        LootrPolymerPatch.LOGGER.info("Vanilla exposed copper chests are now drawn by display entities");
    }

    /** Writes the lid models and points the vanilla items at the same sprite. @return models written */
    public static int write(ResourcePackBuilder builder) {
        // The chest geometry is the vanilla one, so each lid position is the same special chest
        // model with a different openness, exactly like the loot chests use.
        for (var step = 0; step <= PolyLootrChest.OPEN_STEPS; step++) {
            var path = "assets/%s/items/%s.json".formatted(LootrPolymerPatch.ASSETS, modelPath(step));
            builder.addStringData(path, LootrPack.gson().toJson(LootrPack.chestModel(MATERIAL, step)));
        }

        // The item models name the blanked sprite, so they are pointed at the same chest art the
        // display entities draw.
        var model = LootrPack.gson().toJson(itemModel());
        for (var path : ITEM_MODELS) {
            builder.addStringData(path, model);
        }

        return PolyLootrChest.OPEN_STEPS + 1;
    }

    /** The item definition for one lid position of a redrawn vanilla exposed copper chest. */
    static Identifier model(int step) {
        return Identifier.fromNamespaceAndPath(LootrPolymerPatch.ASSETS, modelPath(step));
    }

    private static String modelPath(int step) {
        return "vanilla/copper_exposed_" + step;
    }

    private static JsonObject itemModel() {
        var inner = new JsonObject();
        inner.addProperty("type", "minecraft:chest");
        inner.addProperty("texture", MATERIAL);
        inner.addProperty("openness", 0.0f);
        inner.addProperty("chest_type", "single");

        var model = new JsonObject();
        model.addProperty("type", "minecraft:special");
        // The vanilla exposed copper chest base keeps its own item transforms.
        model.addProperty("base", "minecraft:item/exposed_copper_chest");
        model.add("model", inner);

        var json = new JsonObject();
        json.add("model", model);
        return json;
    }

    private static ItemStack stack(int step) {
        ItemStack stack = new ItemStack(Items.EXPOSED_COPPER_CHEST);
        stack.set(DataComponents.ITEM_MODEL, model(step));
        return stack;
    }

    private static int facingIndex(BlockState state) {
        if (!state.hasProperty(ChestBlock.FACING)) {
            return 2;
        }
        return switch (state.getValue(ChestBlock.FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** The holder half of the override, kept separate from the block mapping on purpose. */
    private static final class Overlay implements BlockWithElementHolder {
        @Override
        public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
            if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                // Only the single sprite is blanked, so vanilla still draws both halves of a double.
                return null;
            }
            return new Holder(world, pos, state);
        }

        @Override
        public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
            return true;
        }
    }

    private static final class Holder extends ElementHolder {
        private static final float[] FACING_YAW = {0f, 90f, 180f, 270f};

        private final ServerLevel world;
        private final BlockPos pos;
        private final ItemDisplayElement element;
        private int step;
        private float yaw;
        /** Whether the display is currently drawing, false while the chest is a half of a double. */
        private boolean shown = true;

        Holder(ServerLevel world, BlockPos pos, BlockState state) {
            this.world = world;
            this.pos = pos;
            this.yaw = FACING_YAW[facingIndex(state)];

            this.element = new ItemDisplayElement(stack(0));
            this.element.setItemDisplayContext(ItemDisplayContext.NONE);
            this.element.setDisplaySize(2f, 2f);
            this.element.setViewRange(1.5f);
            this.element.setTeleportDuration(0);
            this.element.setInvisible(true);
            this.element.setYaw(this.yaw);
            this.addElement(this.element);
        }

        @Override
        protected void onTick() {
            if (!this.world.isLoaded(this.pos)) {
                return;
            }

            BlockState state = this.world.getBlockState(this.pos);
            // A single vanilla exposed copper chest can grow into a double one. Polymer keeps this
            // holder then, because the block itself did not change, and each half draws its own art
            // from the sprites that were never blanked, so the display has to stop drawing a whole
            // chest over them.
            if (isHalf(state)) {
                if (this.shown) {
                    this.shown = false;
                    this.step = 0;
                    this.element.setItem(ItemStack.EMPTY);
                }
                return;
            }
            if (!this.shown) {
                this.shown = true;
                this.element.setItem(stack(0));
            }

            int target = LootrOpenState.isOpen(this.world, this.pos) ? PolyLootrChest.OPEN_STEPS : 0;
            if (this.step != target) {
                this.step += Integer.signum(target - this.step);
                this.element.setItem(stack(this.step));
            }

            float yaw = FACING_YAW[facingIndex(state)];
            if (yaw != this.yaw) {
                this.yaw = yaw;
                this.element.setYaw(yaw);
            }
        }

        /** Vanilla copper chests form doubles; only the single sprite is blanked. */
        private static boolean isHalf(BlockState state) {
            return state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
        }
    }
}
