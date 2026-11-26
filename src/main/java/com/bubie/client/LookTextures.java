package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.Face;
import com.bubie.entity.Look;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a Bubie's textures from its look, the way the site's renderer does: the body texture is painted in the
 * default ramps (ivory casing, black hoodie, amber light) and every ramp key is swapped for the chosen palette;
 * the face is put together from the site's eye and mouth pieces (textures/face_parts, 96×96 each) on a 3× texture,
 * so the 10×5 screen of the model holds the site's 30×15 screen pixel for pixel.
 */
public final class LookTextures {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation BASE = Bubie.id("textures/entity/bubie.png");
    /** Ramp keys of the base texture (manifest.ts RAMP_KEYS) and the two-tone limbs. */
    private static final int[] SHELL_KEYS = {0xFFF9EE, 0xF3E7D5, 0xDDCBB2, 0xBFA586};
    private static final int[] LIMB_KEYS = {0xF7EEE1, 0xE2D2BC};
    private static final int[] CLOTH_KEYS = {0x4C4547, 0x38333A, 0x29252B, 0x736B6C, 0x1B181E};
    private static final int[] GLOW_KEYS = {0xFFE6B0, 0xF4BF69, 0xC98F3A};

    /** Face texture: 3 texels per model pixel; the screen starts at model pixel (9, 9) of the head front. */
    public static final int FACE_SCALE = 3, FACE_SIZE = 64 * FACE_SCALE, SCREEN_X = 9 * FACE_SCALE, SCREEN_Y = 9 * FACE_SCALE;
    public static final int SCREEN_W = 10 * FACE_SCALE, SCREEN_H = 5 * FACE_SCALE;
    /** Where that screen sits on the site's 96×96 piece canvas. */
    private static final int SITE_X = 33, SITE_Y = 41;

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final Map<String, NativeImage> SOURCES = new HashMap<>();

    private LookTextures() {
    }

    /** The eyes and mouth shown for an expression; IDLE is the owner's choice. */
    public static String[] faceParts(Look look, Face face, boolean blink) {
        String eyes = Look.EYES.get(look.eyes()), mouth = Look.MOUTHS.get(look.mouth());
        return switch (face) {
            case IDLE -> new String[]{blink ? "closed" : eyes, mouth};
            case HAPPY -> new String[]{"happy", "smile"};
            case WORK -> new String[]{"angry", "neutral"};
            case ERROR -> new String[]{"404", "wavy"};
            case SLEEP -> new String[]{"closed", "dot"};
            case HURT -> new String[]{"xx", "open"};
            case MUSIC -> new String[]{"happy", "smile"}; // the mouth is an equalizer: see music()
            case LOST -> new String[]{"question", "none"};
        };
    }

    public static ResourceLocation body(Look look) {
        String key = "body_" + look.shell() + "_" + look.cloth() + "_" + look.glow();
        return CACHE.computeIfAbsent(key, k -> {
            NativeImage base = source(BASE);
            if (base == null) return BASE;
            NativeImage img = new NativeImage(base.getWidth(), base.getHeight(), true);
            img.copyFrom(base);
            int[] shell = Look.SHELLS.get(look.shell()).ramp();
            int[] cloth = Look.CLOTHS.get(look.cloth()).ramp();
            int[] glow = Look.GLOWS.get(look.glow()).ramp();
            Map<Integer, Integer> swap = new HashMap<>();
            for (int i = 0; i < SHELL_KEYS.length; i++) swap.put(SHELL_KEYS[i], shell[i]);
            swap.put(LIMB_KEYS[0], shell[1]);
            swap.put(LIMB_KEYS[1], shell[2]);
            for (int i = 0; i < CLOTH_KEYS.length; i++) swap.put(CLOTH_KEYS[i], cloth[i]);
            for (int i = 0; i < GLOW_KEYS.length; i++) swap.put(GLOW_KEYS[i], glow[i]);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    if ((abgr >>> 24) == 0) continue;
                    Integer to = swap.get(rgb(abgr));
                    if (to != null) img.setPixelRGBA(x, y, abgr(to, abgr >>> 24));
                }
            }
            return register(k, img);
        });
    }

    public static ResourceLocation face(Look look, Face face, boolean blink) {
        String[] parts = faceParts(look, face, blink);
        String key = "face_" + parts[0] + "_" + parts[1] + "_" + look.glow();
        return CACHE.computeIfAbsent(key, k -> {
            NativeImage img = new NativeImage(FACE_SIZE, FACE_SIZE, true); // all transparent black: adds nothing
            int[] glow = Look.GLOWS.get(look.glow()).ramp();
            stamp(img, Bubie.id("textures/face_parts/eyes_" + parts[0] + ".png"), glow);
            stamp(img, Bubie.id("textures/face_parts/mouth_" + parts[1] + ".png"), glow);
            return register(k, img);
        });
    }

    /** Equalizer frames: bar heights (0-4) of the 9 bars, on the 4 free rows under the eyes. */
    public static final int MUSIC_FRAMES = 8;
    private static final int[][] EQ = {
            {2, 3, 4, 2, 1, 3, 4, 2, 1}, {3, 4, 2, 1, 2, 4, 3, 1, 2}, {4, 2, 1, 3, 4, 2, 1, 3, 3},
            {2, 1, 3, 4, 3, 1, 2, 4, 2}, {1, 3, 4, 2, 2, 3, 4, 2, 1}, {3, 4, 2, 1, 4, 2, 1, 3, 4},
            {4, 2, 1, 3, 3, 4, 2, 1, 3}, {2, 3, 4, 4, 1, 2, 3, 4, 2}};

    /** Happy eyes over a dancing equalizer, in the Bubie's light colour. */
    public static ResourceLocation music(Look look, int frame) {
        int f = Math.floorMod(frame, MUSIC_FRAMES);
        String key = "music_" + f + "_" + look.glow();
        return CACHE.computeIfAbsent(key, k -> {
            NativeImage img = new NativeImage(FACE_SIZE, FACE_SIZE, true);
            int[] glow = Look.GLOWS.get(look.glow()).ramp();
            stamp(img, Bubie.id("textures/face_parts/eyes_happy.png"), glow);
            for (int bar = 0; bar < EQ[f].length; bar++) {
                int x = SCREEN_X + 7 + bar * 2;
                for (int h = 0; h < EQ[f][bar]; h++) {
                    int y = SCREEN_Y + 13 - h;
                    img.setPixelRGBA(x, y, abgr(h == EQ[f][bar] - 1 ? glow[0] : glow[1], 255));
                }
            }
            return register(k, img);
        });
    }

    private static void stamp(NativeImage img, ResourceLocation part, int[] glow) {
        NativeImage src = source(part);
        if (src == null) return;
        for (int y = 0; y < SCREEN_H; y++) {
            for (int x = 0; x < SCREEN_W; x++) {
                int abgr = src.getPixelRGBA(SITE_X + x, SITE_Y + y);
                if ((abgr >>> 24) == 0) continue;
                int c = rgb(abgr);
                int to = c == GLOW_KEYS[0] ? glow[0] : c == GLOW_KEYS[2] ? glow[2] : glow[1];
                img.setPixelRGBA(SCREEN_X + x, SCREEN_Y + y, abgr(to, 255));
            }
        }
    }

    private static NativeImage source(ResourceLocation id) {
        return SOURCES.computeIfAbsent(id.toString(), k -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(id)) {
                return NativeImage.read(in);
            } catch (Exception e) {
                LOG.warn("Bubie: could not read {}", id, e);
                return null;
            }
        });
    }

    private static ResourceLocation register(String key, NativeImage img) {
        ResourceLocation id = Bubie.id("look/" + key);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
        return id;
    }

    /** NativeImage pixels are 0xAABBGGRR. */
    private static int rgb(int abgr) {
        return (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
    }

    private static int abgr(int rgb, int alpha) {
        return alpha << 24 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | (rgb >> 16 & 0xFF);
    }

    /** After a resource reload (F3+T) the textures are rebuilt from the new files. */
    public static void clear() {
        CACHE.clear();
        SOURCES.values().forEach(img -> {
            if (img != null) img.close();
        });
        SOURCES.clear();
    }
}
