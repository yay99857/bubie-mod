package com.bubie.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * How a Bubie looks, from the My Bubie editor on bubie.gg: casing colour, hoodie colour, glow colour, eyes and mouth.
 * Palettes and parts are the site's (bubie/frontend/features/my-bubie/manifest.ts), minus the Plus-only colours.
 * Lists only grow at the end: the synced int stores indices.
 */
public record Look(int shell, int cloth, int glow, int eyes, int mouth) {
    public record Swatch(String id, int[] ramp) {
    }

    private static Swatch s(String id, String... hex) {
        int[] ramp = new int[hex.length];
        for (int i = 0; i < hex.length; i++) ramp[i] = Integer.parseInt(hex[i].substring(1), 16);
        return new Swatch(id, ramp);
    }

    public static final List<Swatch> SHELLS = List.of(
            s("ivory", "#FFF9EE", "#F3E7D5", "#DDCBB2", "#BFA586"),
            s("pink", "#FFEFF4", "#F8CCDB", "#E6A7BF", "#C9849F"),
            s("lavender", "#F4EEFF", "#D9CBF5", "#B9A5E6", "#937CCB"),
            s("baby_blue", "#EEF7FF", "#C8E2F8", "#A2C6E8", "#7DA2C9"),
            s("mint", "#EEFCF8", "#C7EDE4", "#9ED9CF", "#73B5AA"),
            s("peach", "#FFF3EA", "#FBD6BF", "#EDB596", "#CC9172"),
            s("lemon", "#FFFCE8", "#FAEFAE", "#EBD97D", "#C9B55A"),
            s("charcoal", "#8A8590", "#6B6670", "#524E57", "#3C3940"));

    public static final List<Swatch> CLOTHS = List.of(
            s("black", "#4C4547", "#38333A", "#29252B", "#736B6C", "#1B181E"),
            s("charcoal", "#7A7582", "#5B5662", "#3F3B45", "#97919E", "#292630"),
            s("midnight", "#4B4359", "#352F42", "#231E2D", "#71647E", "#151220"),
            s("white", "#FFFFFF", "#ECE8E1", "#C9C2B6", "#FFFFFF", "#8D8278"),
            s("purple", "#7E4EB8", "#5D3591", "#3E2166", "#A169C8", "#1C0D47"),
            s("red", "#E86A6A", "#C94848", "#9C3236", "#ED9283", "#6D1426"),
            s("olive", "#76834F", "#596339", "#3E4626", "#989E6B", "#2F3115"),
            s("mustard", "#F4CF6B", "#E0B14A", "#B88A2E", "#F6E485", "#81470D"),
            s("pink", "#FBD3E0", "#F2AFC6", "#D888A6", "#FCDBE1", "#975177"),
            s("lavender", "#D9CBF5", "#B9A5E6", "#937CCB", "#E5D4F7", "#5A498E"),
            s("sky", "#BFE0F7", "#94C4E8", "#6C9FCB", "#CBECF9", "#3C5A8E"),
            s("mint", "#C7EDE4", "#9ED9CF", "#6FB2A6", "#D1F1E5", "#427D7C"));

    public static final List<Swatch> GLOWS = List.of(
            s("amber", "#FFE6B0", "#F4BF69", "#C98F3A"),
            s("mint", "#D6F3EE", "#9ED9CF", "#5FA79B"),
            s("pink", "#FFD9E6", "#F7A8C4", "#C46F92"),
            s("cyan", "#C8F6FB", "#6FE3F0", "#2FA6B5"),
            s("ice", "#E6F0FF", "#BFD9FF", "#7E9FD1"),
            s("white", "#FFFFFF", "#F4F1EA", "#B9B4AA"),
            s("red", "#FFC2C2", "#FF5D5D", "#B8333D"),
            s("purple", "#E9DBFF", "#C59BFF", "#8A5DD1"));

    public static final List<String> EYES = List.of("dots", "happy", "sleepy", "angry", "sad", "closed", "heart", "wide",
            "stare", "uhoh", "serious", "uuu", "eee", "xx", "qp", "404", "meh", "tt");

    public static final List<String> MOUTHS = List.of("neutral", "smile", "grin", "frown", "angry", "open", "smirk", "cat",
            "dot", "wavy");

    /** The official mascot, like DEFAULT_APPEARANCE on the site. */
    public static final Look DEFAULT = new Look(0, 0, 0, 0, 0);

    public Look {
        shell = clamp(shell, SHELLS.size());
        cloth = clamp(cloth, CLOTHS.size());
        glow = clamp(glow, GLOWS.size());
        eyes = clamp(eyes, EYES.size());
        mouth = clamp(mouth, MOUTHS.size());
    }

    private static int clamp(int v, int size) {
        return v >= 0 && v < size ? v : 0;
    }

    public static int wrap(int v, int size) {
        return Math.floorMod(v, size);
    }

    public int pack() {
        return shell | cloth << 4 | glow << 8 | eyes << 12 | mouth << 17;
    }

    public static Look unpack(int v) {
        return new Look(v & 15, v >> 4 & 15, v >> 8 & 15, v >> 12 & 31, v >> 17 & 15);
    }

    public static Look random(RandomSource r) {
        return new Look(r.nextInt(SHELLS.size()), r.nextInt(CLOTHS.size()), r.nextInt(GLOWS.size()),
                r.nextInt(EYES.size()), r.nextInt(MOUTHS.size()));
    }

    public Look withShell(int v) {
        return new Look(v, cloth, glow, eyes, mouth);
    }

    public Look withCloth(int v) {
        return new Look(shell, v, glow, eyes, mouth);
    }

    public Look withGlow(int v) {
        return new Look(shell, cloth, v, eyes, mouth);
    }

    public Look withEyes(int v) {
        return new Look(shell, cloth, glow, wrap(v, EYES.size()), mouth);
    }

    public Look withMouth(int v) {
        return new Look(shell, cloth, glow, eyes, wrap(v, MOUTHS.size()));
    }

    /** Saved by name, so reordering never changes how a saved Bubie looks. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Shell", SHELLS.get(shell).id());
        tag.putString("Cloth", CLOTHS.get(cloth).id());
        tag.putString("Glow", GLOWS.get(glow).id());
        tag.putString("Eyes", EYES.get(eyes));
        tag.putString("Mouth", MOUTHS.get(mouth));
        return tag;
    }

    public static Look load(CompoundTag tag) {
        return new Look(indexOf(SHELLS, tag.getString("Shell")), indexOf(CLOTHS, tag.getString("Cloth")),
                indexOf(GLOWS, tag.getString("Glow")), EYES.indexOf(tag.getString("Eyes")), MOUTHS.indexOf(tag.getString("Mouth")));
    }

    private static int indexOf(List<Swatch> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        return 0;
    }
}
