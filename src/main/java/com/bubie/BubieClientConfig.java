package com.bubie;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client config (config/bubie-client.toml): choices of each player on their own computer. */
public final class BubieClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DJ_PLAY_LINKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        DJ_PLAY_LINKS = b.comment("Play the audio links of Bubie DJs near you. They are downloaded from the address the",
                        "Bubie's owner typed, which sees your IP. Off: you only hear music discs.")
                .define("djPlayLinks", true);
        SPEC = b.build();
    }

    private BubieClientConfig() {
    }
}
