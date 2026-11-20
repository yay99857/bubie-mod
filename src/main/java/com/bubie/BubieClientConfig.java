package com.bubie;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/bubie-client.toml): choices of each player on their own computer. */
public final class BubieClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DJ_PLAY_LINKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        DJ_PLAY_LINKS = b.comment("Play the audio links of Bubie DJs near you. They are downloaded from the address the",
                        "Bubie's owner typed, which sees your IP. Off: you only hear music discs.")
                .define("djPlayLinks", true);
        SPEC = b.build();
    }

    private BubieClientConfig() {
    }
}
