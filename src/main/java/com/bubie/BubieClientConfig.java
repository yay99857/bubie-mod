package com.bubie;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/bubie-client.toml): choices of each player on their own computer. */
public final class BubieClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BUILD_GHOSTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        BUILD_GHOSTS = b.comment("Show what a Bubie Builder still has to place as see-through blocks, so you can build along:",
                        "aim at one to see which block goes there. Off if it clashes with a shader pack.")
                .define("buildGhosts", true);
        SPEC = b.build();
    }

    private BubieClientConfig() {
    }
}
