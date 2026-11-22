package eu.pb4.lootrpatch.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.lootrpatch.LootrPolymerPatch;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Writes everything the pack needs to show Lootr's containers on a vanilla client.
 *
 * <p>Lootr draws chests, shulkers and pots from its own client renderers and names its own special
 * model types, which an unmodded client does not have. What it does share with vanilla is the chest
 * and shulker box <em>model types</em> and the atlas sprites, so a chest or a shulker box is rebuilt
 * here as the vanilla model pointing at Lootr's sprite: same geometry, same lid angle, same art.
 *
 * <p>Barrels and trophies are different: Lootr ships them as ordinary block models, which every
 * client can bake, so one tiny item definition per look is enough.
 *
 * <p>Only sprites that are certainly in the atlas are named. Lootr's own atlas files list the
 * normal and trapped chest sprites but not the copper ones, so those are added here.
 */
public final class LootrPack {
    private static final Gson GSON = new Gson();

    /**
     * A 64x64 fully transparent PNG, the size of the sprite it replaces, so the chest model's UVs
     * line up with nothing at all.
     */
    private static final byte[] BLANK = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAJ0lEQVR42u3BAQ0AAADCoPdPbQ43oAAAAAAAAAAAAAAAAAAAAIB3A0BAAAGveg7oAAAAAElFTkSuQmCC");

    /** The one chest sprite the carrier asks for, and the only one that is blanked. */
    private static final String CARRIER_SPRITE = "assets/minecraft/textures/entity/chest/copper_exposed.png";

    /** The sprite name the copied shulker box art takes, below the shulker sheet's own directory. */
    private static final String SHULKER_SPRITE_NAME = "lootr_shulker_box";

    /** Where the copied shulker box art goes: the directory the shulker sheet already reads. */
    private static final String SHULKER_SPRITE = "assets/minecraft/textures/entity/shulker/" + SHULKER_SPRITE_NAME;


    /** The copper chest sprites Lootr's own atlas file does not register. */
    private static final List<String> COPPER_SPRITES = List.of(
            "copper", "copper_opened",
            "copper_exposed", "copper_exposed_opened",
            "copper_weathered", "copper_weathered_opened",
            "copper_oxidized", "copper_oxidized_opened");


    private LootrPack() {
    }

    public static void write(ResourcePackBuilder builder) {
        builder.addData(CARRIER_SPRITE, BLANK);
        writeAtlas(builder, COPPER_SPRITES);

        var models = 0;
        for (var chest : LootrBlocks.CHESTS) {
            for (var opened : new boolean[]{false, true}) {
                for (var step = 0; step <= PolyLootrChest.OPEN_STEPS; step++) {
                    builder.addStringData(
                            "assets/%s/items/%s.json".formatted(LootrPolymerPatch.ASSETS,
                                    PolyLootrChest.modelPath(chest.path(), opened, step)),
                            GSON.toJson(chestModel(chest.texture(opened), step)));
                    models++;
                }
            }
        }

        for (var barrel : PolyLootrBarrel.MODELS) {
            builder.addStringData(
                    "assets/%s/items/%s.json".formatted(LootrPolymerPatch.ASSETS,
                            PolyLootrBarrel.modelPath(barrel)),
                    GSON.toJson(blockModel("lootr:block/" + barrel)));
            models++;
        }

        builder.addStringData(
                "assets/%s/items/%s.json".formatted(LootrPolymerPatch.ASSETS, PolyLootrTrophy.MODEL),
                GSON.toJson(blockModel("lootr:block/trophy")));
        models++;

        models += writeShulkers(builder);
        models += writeFrames(builder);
        models += VanillaExposedCopperChests.write(builder);

        LootrPolymerPatch.LOGGER.info("Generated {} Lootr container models for the Polymer pack", models);
    }

    /**
     * Adds the copper chest sprites to the chest atlas.
     *
     * <p>Atlas files are merged across packs, so this only adds the sprites Lootr leaves out; the
     * normal and trapped ones already arrive with Lootr's assets. Block textures need nothing here:
     * vanilla's block atlas takes everything below a pack's {@code textures/block} by directory, so
     * the planks the frame models name arrive with Lootr's own assets.
     */
    private static void writeAtlas(ResourcePackBuilder builder, List<String> sprites) {
        var sources = new JsonArray();
        for (var sprite : sprites) {
            var source = new JsonObject();
            source.addProperty("type", "minecraft:single");
            source.addProperty("resource", "lootr:entity/chest/" + sprite);
            sources.add(source);
        }

        var atlas = new JsonObject();
        atlas.add("sources", sources);
        builder.addStringData("assets/minecraft/atlases/chests.json", GSON.toJson(atlas));
    }

    /**
     * Writes every shulker box look and copies the two sprites they name. @return models written
     *
     * <p>The copies are what lets a shulker box keep Lootr's art: the model names a sprite below
     * {@code entity/shulker}, which is where vanilla's own shulker sprites live, and nothing about
     * it says which mod the pixels came from.
     */
    private static int writeShulkers(ResourcePackBuilder builder) {
        copy(builder, "assets/lootr/textures/entity/shulker_box/normal.png", SHULKER_SPRITE + ".png");
        copy(builder, "assets/lootr/textures/entity/shulker_box/normal_opened.png", SHULKER_SPRITE + "_opened.png");

        var models = 0;
        for (var looted : new boolean[]{false, true}) {
            for (var step = 0; step <= PolyLootrChest.OPEN_STEPS; step++) {
                builder.addStringData(
                        "assets/%s/items/%s.json".formatted(LootrPolymerPatch.ASSETS,
                                PolyLootrShulker.modelPath(looted, step)),
                        GSON.toJson(shulkerModel(looted, step)));
                models++;
            }
        }
        return models;
    }

    /**
     * Writes Lootr's frame border in both of its looks. @return models written
     *
     * <p>The frame stays the client's: its own renderer draws the panel and the item in it exactly as
     * it does for any item frame. The pack only repaints the wood, so one model per look is enough.
     */
    private static int writeFrames(ResourcePackBuilder builder) {
        for (var looted : new boolean[]{false, true}) {
            var model = looted ? "frame_open" : "frame";
            builder.addStringData(
                    "assets/%s/models/block/%s.json".formatted(LootrPolymerPatch.ASSETS, model),
                    GSON.toJson(frameModel(looted ? "lootr:block/silver_planks" : "lootr:block/gold_planks")));
            builder.addStringData(
                    "assets/%s/items/%s.json".formatted(LootrPolymerPatch.ASSETS, model),
                    GSON.toJson(blockModel(LootrPolymerPatch.ASSETS + ":block/" + model)));
        }
        return 2;
    }

    /**
     * One frame border: vanilla's own border geometry, in Lootr's planks.
     *
     * <p>The panel is left out on purpose. The client draws the panel and the item pinned to it from
     * the frame itself, with the item a thirty-second of a block in front of the panel, so a second
     * panel here would be a surface right behind the item, fighting the one vanilla draws. The wood
     * is what the pack replaces, so the wood is all this model carries.
     */
    private static JsonObject frameModel(String wood) {
        var textures = new JsonObject();
        textures.addProperty("particle", wood);
        textures.addProperty("wood", wood);

        var json = new JsonObject();
        json.add("textures", textures);
        json.add("elements", frameBorder());
        return json;
    }

    /** Vanilla's frame border, copied element for element from its own frame template. */
    private static JsonArray frameBorder() {
        return GSON.fromJson("""
                [
                  {
                    "from": [2, 2, 15], "to": [14, 3, 16],
                    "faces": {
                      "down":  { "uv": [2, 0, 14, 1], "texture": "#wood" },
                      "up":    { "uv": [2, 15, 14, 16], "texture": "#wood" },
                      "north": { "uv": [2, 13, 14, 14], "texture": "#wood" },
                      "south": { "uv": [2, 13, 14, 14], "texture": "#wood" },
                      "west":  { "uv": [15, 13, 16, 14], "texture": "#wood" },
                      "east":  { "uv": [0, 13, 1, 14], "texture": "#wood" }
                    }
                  },
                  {
                    "from": [2, 13, 15], "to": [14, 14, 16],
                    "faces": {
                      "down":  { "uv": [2, 0, 14, 1], "texture": "#wood" },
                      "up":    { "uv": [2, 15, 14, 16], "texture": "#wood" },
                      "north": { "uv": [2, 2, 14, 3], "texture": "#wood" },
                      "south": { "uv": [2, 2, 14, 3], "texture": "#wood" },
                      "west":  { "uv": [15, 2, 16, 3], "texture": "#wood" },
                      "east":  { "uv": [0, 2, 1, 3], "texture": "#wood" }
                    }
                  },
                  {
                    "from": [2, 3, 15], "to": [3, 13, 16],
                    "faces": {
                      "north": { "uv": [13, 3, 14, 13], "texture": "#wood" },
                      "south": { "uv": [2, 3, 3, 13], "texture": "#wood" },
                      "west":  { "uv": [15, 3, 16, 13], "texture": "#wood" },
                      "east":  { "uv": [0, 3, 1, 13], "texture": "#wood" }
                    }
                  },
                  {
                    "from": [13, 3, 15], "to": [14, 13, 16],
                    "faces": {
                      "north": { "uv": [2, 3, 3, 13], "texture": "#wood" },
                      "south": { "uv": [13, 3, 14, 13], "texture": "#wood" },
                      "west":  { "uv": [15, 3, 16, 13], "texture": "#wood" },
                      "east":  { "uv": [0, 3, 1, 13], "texture": "#wood" }
                    }
                  }
                ]
                """, JsonArray.class);
    }

    /**
     * One vanilla shulker box model at one lid position, naming a copy of Lootr's sprite.
     *
     * <p>The model type resolves its texture inside the shulker sheet, so the name given here is the
     * sprite's name below {@code entity/shulker}, the way vanilla's own {@code shulker_purple} is.
     */
    static JsonObject shulkerModel(boolean looted, int step) {
        var inner = new JsonObject();
        inner.addProperty("type", "minecraft:shulker_box");
        inner.addProperty("texture", "minecraft:" + SHULKER_SPRITE_NAME + (looted ? "_opened" : ""));
        inner.addProperty("openness", step / (float) PolyLootrChest.OPEN_STEPS);

        var model = new JsonObject();
        model.addProperty("type", "minecraft:special");
        model.addProperty("base", "minecraft:item/purple_shulker_box");
        model.add("model", inner);
        // A shulker box's model is authored in block entity space, flipped and a sixteenth below the
        // block origin. Vanilla carries this transformation on its own shulker box item for exactly
        // that reason; without it a display shows the raw model, upside down and off centre.
        model.add("transformation", shulkerTransformation());

        var json = new JsonObject();
        json.add("model", model);
        return json;
    }

    /**
     * The transform vanilla puts on one of its shulker box items, copied from its own definition.
     *
     * <p>The half turn about X stands the model up, the scale is vanilla's guard against a gap
     * between the box and its lid, and the translation is in sixteenths of a block, which is the
     * nudge that puts the box on the block origin.
     */
    private static JsonObject shulkerTransformation() {
        var transformation = new JsonObject();
        transformation.add("left_rotation", floats(1f, 0f, 0f, 0f));
        transformation.add("right_rotation", floats(0f, 0f, 0f, 1f));
        transformation.add("scale", floats(0.9995f, 0.9995f, 0.9995f));
        transformation.add("translation", floats(0.5f, 1.4995f, 0.5f));
        return transformation;
    }

    private static JsonArray floats(float... values) {
        var array = new JsonArray();
        for (var value : values) {
            array.add(value);
        }
        return array;
    }

    /** Copies one of Lootr's own files into the pack: only its bytes know the art. */
    private static void copy(ResourcePackBuilder builder, String from, String to) {
        try (var stream = LootrPack.class.getResourceAsStream("/" + from)) {
            if (stream == null) {
                LootrPolymerPatch.LOGGER.error(
                        "Lootr's {} was not found on the classpath; the shulker box will have no art", from);
                return;
            }
            builder.addData(to, stream.readAllBytes());
        } catch (IOException e) {
            LootrPolymerPatch.LOGGER.error("Could not read Lootr's {}", from, e);
        }
    }

    /**
     * One vanilla chest model at one lid position, naming a Lootr chest material.
     *
     * <p>The model type takes its shape from {@code chest_type} but its art only from {@code
     * texture}, which it resolves below the {@code entity/chest} sprite directory, exactly as
     * vanilla's own {@code minecraft:normal} does.
     */
    static JsonObject chestModel(String texture, int step) {
        var inner = new JsonObject();
        inner.addProperty("type", "minecraft:chest");
        inner.addProperty("texture", texture);
        inner.addProperty("openness", step / (float) PolyLootrChest.OPEN_STEPS);
        inner.addProperty("chest_type", "single");

        var model = new JsonObject();
        model.addProperty("type", "minecraft:special");
        // The vanilla chest's own transform set, so the same model also works as an item icon.
        model.addProperty("base", "minecraft:item/chest");
        model.add("model", inner);

        var json = new JsonObject();
        json.add("model", model);
        return json;
    }

    /** An item definition that simply points at an existing block model. */
    static JsonObject blockModel(String model) {
        var inner = new JsonObject();
        inner.addProperty("type", "minecraft:model");
        inner.addProperty("model", model);

        var json = new JsonObject();
        json.add("model", inner);
        return json;
    }

    static Gson gson() {
        return GSON;
    }
}
