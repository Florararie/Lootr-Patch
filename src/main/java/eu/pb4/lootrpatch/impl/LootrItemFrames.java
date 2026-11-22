package eu.pb4.lootrpatch.impl;

import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import noobanidus.mods.lootr.common.api.data.entity.ILootrEntity;
import org.joml.Quaternionf;

/**
 * Shows Lootr's loot frames to unmodded clients as vanilla item frames wearing Lootr's own frame.
 *
 * <p>Lootr converts the item frames it finds in structures - by default including the one holding
 * the End ship's elytra - into {@code lootr:item_frame} entities, which hand out their loot when a
 * player takes the item out of them. Entity types travel as registry ids inside the spawn packet,
 * and a vanilla client has no entry for a type it does not know, so it cannot read that packet at
 * all: it drops the connection with a "Network Protocol Error". Blocks never did this because the
 * patch replaces them with vanilla blocks; entities were reaching the client untouched.
 *
 * <p>The client-visible type is reported as {@code minecraft:item_frame}, so the frame is spawned as
 * a vanilla frame at the same place, facing and rotation, holding the same item. It stays a Lootr
 * frame on the server, so it is still a loot container and still gives out its loot.
 *
 * <p>What a vanilla frame draws is vanilla's wooden frame, and Lootr's frames are not wooden: Lootr
 * paints vanilla's own frame geometry with its {@code gold_planks}, and with {@code silver_planks}
 * once a player has looted the frame. A vanilla client picks a frame model from the frame's glow and
 * map states only, so there is no per-frame texture to ask it for, and repainting the shared frame
 * model would repaint every player-built frame in the world too.
 *
 * <p>So the client keeps its frame: its own renderer draws the panel the item is pinned to and the
 * item itself, exactly as it would for any item frame, which is what keeps the frame's background
 * behind the item and the item in front of it correct at every angle. A display attached to the
 * frame entity paints only the border on top of the wood, in Lootr's planks. The border is vanilla's
 * own geometry and uvs, placed by the same offset and rotations vanilla's frame renderer uses, and
 * lifted a little in front of the wood so the gold, not the wood, is what the depth test keeps.
 *
 * <p>Lootr's frame also defines two synched flags of its own, for the refreshing and decaying
 * animations its own client draws. A vanilla data tracker indexes its entries by id without
 * checking the length, so those ids are removed here instead of being sent to a client that has no
 * slot for them.
 */
public final class LootrItemFrames {
    /**
     * Ids taken by a vanilla item frame when Polymer cannot instantiate one to read them: the entity
     * flags through to the rotation, which is ten entries.
     */
    private static final int FALLBACK_VANILLA_DATA_IDS = 10;

    /**
     * The half turn an item display draws a block model with.
     *
     * <p>A display draws a block model as an item, and an item's model faces the other way from the
     * same model in block space. Vanilla's frame rotation describes the block, so it is turned half
     * a turn about the frame's own axis, which is what puts the face the player should see outward.
     */
    private static final float DISPLAY_TURN = 180f;

    /**
     * How far vanilla's frame renderer moves the frame model out of the entity it belongs to.
     *
     * <p>A frame entity sits at the back of its frame and vanilla moves the model to the face of the
     * block it hangs on; the display does the same, so both end up in the same plane.
     */
    private static final double FRAME_DEPTH = 0.46875;

    /**
     * How far the border is drawn in front of the wooden border it covers.
     *
     * <p>Vanilla draws a frame's own model with a forward depth bias, so wood that shares its exact
     * plane would win the depth test at some angles. This is small enough to be invisible and large
     * enough that the gold always does. The panel and the item are untouched by it: the pack's body
     * has no panel, and the item never overlaps the border.
     */
    private static final double BORDER_LIFT = 0.02;

    private static final Identifier ID = Identifier.fromNamespaceAndPath(LootrPolymerPatch.TARGET_MOD_ID, "item_frame");

    /** Ids a vanilla item frame uses for its synched data, or null when they could not be read. */
    private static Set<Integer> vanillaDataIds;
    private static boolean lookupTried;

    private LootrItemFrames() {
    }

    public static void register() {
        EntityType<?> frame = BuiltInRegistries.ENTITY_TYPE.getValue(ID);
        if (frame == null) {
            LootrPolymerPatch.LOGGER.warn("Lootr's item frame entity was not found; frames in structures will still drop unmodded clients.");
            return;
        }

        // An unmodded client has no id for lootr:item_frame at all, so the type has to be replaced in
        // the spawn packet before the client is asked to read it.
        registerOverlay(frame);

        // The body is not something the client's frame renderer can be talked into drawing, so every
        // frame gets a display of its own, from the moment it is loaded into a level - which includes
        // the frames that arrive with a structure's chunk.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getType() == frame) {
                EntityAttachment.ofTicking(new FrameHolder(entity), entity);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void registerOverlay(EntityType<?> frame) {
        PolymerEntityUtils.registerOverlay((EntityType<Entity>) frame, entity -> OVERLAY);
    }

    /** The pack's item definition for the frame body, unlooted or as Lootr opens it. */
    public static Identifier model(boolean looted) {
        return Identifier.fromNamespaceAndPath(LootrPolymerPatch.ASSETS, looted ? "frame_open" : "frame");
    }

    private static Set<Integer> vanillaDataIds() {
        if (!lookupTried) {
            lookupTried = true;
            try {
                SynchedEntityData.DataItem<?>[] items = PolymerEntityUtils.getDefaultSynchedEntityData(EntityType.ITEM_FRAME);
                if (items != null) {
                    Set<Integer> ids = new HashSet<>();
                    for (SynchedEntityData.DataItem<?> item : items) {
                        ids.add(item.getAccessor().id());
                    }
                    vanillaDataIds = ids.isEmpty() ? null : ids;
                }
            } catch (RuntimeException e) {
                LootrPolymerPatch.LOGGER.warn("Could not read the vanilla item frame data ids; falling back to their known count.", e);
            }
        }

        return vanillaDataIds;
    }

    /** The replacement itself: a frame that only differs in what the client is told it is. */
    private static final PolymerEntity OVERLAY = new PolymerEntity() {
        @Override
        public EntityType<?> getPolymerEntityType(PacketContext context) {
            return EntityType.ITEM_FRAME;
        }

        @Override
        public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
            Set<Integer> ids = vanillaDataIds();
            if (ids != null) {
                data.removeIf(value -> !ids.contains(value.id()));
            } else {
                data.removeIf(value -> value.id() >= FALLBACK_VANILLA_DATA_IDS);
            }
        }
    };

    private static Direction facing(Entity frame) {
        return frame instanceof HangingEntity hanging ? hanging.getDirection() : Direction.NORTH;
    }

    /** Lootr remembers on the frame itself whether it has been looted. */
    private static boolean isLooted(Entity frame) {
        return frame instanceof ILootrEntity lootr && lootr.hasBeenOpened();
    }

    /** A frame of the base item asked to draw one of the pack's frame bodies instead. */
    private static ItemStack stack(boolean looted) {
        ItemStack stack = new ItemStack(Items.ITEM_FRAME);
        stack.set(DataComponents.ITEM_MODEL, model(looted));
        return stack;
    }

    /** The frame body of one Lootr frame, drawn from an item display that rides the frame entity. */
    private static final class FrameHolder extends ElementHolder {
        private final Entity frame;
        private final ItemDisplayElement element;
        private Direction direction;
        private boolean looted;

        FrameHolder(Entity frame) {
            this.frame = frame;
            this.direction = facing(frame);
            this.looted = isLooted(frame);

            this.element = new ItemDisplayElement(stack(this.looted));
            this.element.setItemDisplayContext(ItemDisplayContext.NONE);
            this.element.setDisplaySize(1f, 1f);
            this.element.setViewRange(1.5f);
            this.element.setTeleportDuration(0);
            this.element.setInvisible(true);
            this.place(this.direction);
            this.addElement(this.element);
        }

        /** Follows the frame's facing and its looted look; its position is the frame's own. */
        @Override
        protected void onTick() {
            if (this.frame.isRemoved()) {
                return;
            }

            Direction direction = facing(this.frame);
            if (direction != this.direction) {
                this.direction = direction;
                this.place(direction);
            }

            boolean looted = isLooted(this.frame);
            if (looted != this.looted) {
                this.looted = looted;
                this.element.setItem(stack(looted));
            }
        }

        /**
         * Puts the body where vanilla's frame renderer puts the frame it draws.
         *
         * <p>The frame entity is at the back of its frame, so the model is moved a hair less than
         * half a block along the facing to reach the face of the block it hangs on. It is then turned
         * the way vanilla turns it: a horizontal frame is turned by its facing's own yaw, and a frame
         * on a ceiling or a floor is tipped a quarter turn either way.
         *
         * <p>The rotation is set as one quaternion rather than as a yaw and a pitch because vanilla
         * tips the frame about the world's X axis <em>after</em> turning it about Y, which makes the
         * two orders differ for a frame on a ceiling or a floor. The half turn {@link #DISPLAY_TURN}
         * goes on the innermost end, in the model's own space, where the display's own flip lives.
         */
        private void place(Direction direction) {
            double depth = FRAME_DEPTH + BORDER_LIFT;
            this.element.setOffset(new Vec3(direction.getStepX() * depth, direction.getStepY() * depth,
                    direction.getStepZ() * depth));

            boolean horizontal = direction.getAxis().isHorizontal();
            float xRot = horizontal ? 0f : -90f * direction.getAxisDirection().getStep();
            float yRot = horizontal ? 180f - direction.toYRot() : 180f;

            this.element.setLeftRotation(new Quaternionf().rotationX((float) Math.toRadians(xRot))
                    .mul(new Quaternionf().rotationY((float) Math.toRadians(yRot + DISPLAY_TURN))));
        }
    }
}
