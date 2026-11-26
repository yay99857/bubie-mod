package com.bubie;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client config (config/bubie-client.toml): choices of each player on their own computer. */
public final class BubieClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BUILD_GHOSTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        BUILD_GHOSTS = b.comment("Show what a Bubie Builder still has to place as see-through blocks, so you can build along:",
                        "aim at one to see which block goes there. Off if it clashes with a shader pack.")
                .define("buildGhosts", true);
        SPEC = b.build();
    }

    private BubieClientConfig() {
    }
}
