package com.bubie;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (world/serverconfig/bubie-server.toml): balance and limits. */
public final class BubieConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue REQUIRE_ENERGY;
    public static final ModConfigSpec.IntValue MAX_ENERGY;
    public static final ModConfigSpec.IntValue REDSTONE_ENERGY;
    public static final ModConfigSpec.IntValue FE_PER_ENERGY;
    public static final ModConfigSpec.DoubleValue BREAK_SPEED;
    public static final ModConfigSpec.IntValue AREA_SIZE;
    public static final ModConfigSpec.IntValue SCAN_PER_TICK;
    public static final ModConfigSpec.BooleanValue REST_AT_NIGHT;
    public static final ModConfigSpec.BooleanValue STATION_LOADS_CHUNKS;
    public static final ModConfigSpec.BooleanValue TELEPORT_WHEN_STUCK;
    public static final ModConfigSpec.IntValue HOME_RADIUS;
    public static final ModConfigSpec.BooleanValue DJ_LINKS;
    public static final ModConfigSpec.BooleanValue ADVANCED_JOBS_NEED_CHIP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        MAX_PER_PLAYER = b.comment("How many Bubies each player can have placed in the world at once.")
                .defineInRange("maxPerPlayer", 3, 1, 64);
        REQUIRE_ENERGY = b.comment("Bubies spend energy while working and stop when it runs out. Off: they work forever.")
                .define("requireEnergy", true);
        MAX_ENERGY = b.comment("Battery size, in ticks of work (20 ticks = 1 second).")
                .defineInRange("maxEnergy", 48000, 1000, 10_000_000);
        REDSTONE_ENERGY = b.comment("Bubies run on redstone or FE, not on coal. Energy from one redstone dust in the fuel slot",
                        "(a block of redstone gives 9 times this). 6000 = 5 minutes of work.")
                .defineInRange("redstoneEnergy", 6000, 1, 1_000_000);
        FE_PER_ENERGY = b.comment("FE the Bubie Station spends per unit of Bubie energy.")
                .defineInRange("fePerEnergy", 4, 0, 10_000);
        BREAK_SPEED = b.comment("Mining speed compared with a player using the same tool (1.0 = same speed).")
                .defineInRange("breakSpeed", 0.75, 0.05, 10.0);
        AREA_SIZE = b.comment("Largest work area side, in blocks, without Range Chips. Each chip adds this much again.")
                .defineInRange("areaSize", 32, 4, 256);
        SCAN_PER_TICK = b.comment("Blocks each Bubie may inspect per tick while looking for work (performance).")
                .defineInRange("scanPerTick", 512, 16, 65536);
        REST_AT_NIGHT = b.comment("Bubies with a station go home at night and rest until morning.")
                .define("restAtNight", true);
        STATION_LOADS_CHUNKS = b.comment("A Bubie Station keeps the 3x3 chunks around it loaded, so Bubies work while you are away.")
                .define("stationLoadsChunks", false);
        TELEPORT_WHEN_STUCK = b.comment("A Bubie that cannot walk back to its chest or station (e.g. from the bottom of a sand pit) teleports there.")
                .define("teleportWhenStuck", true);
        HOME_RADIUS = b.comment("Without a marked area, a new Bubie works this many blocks around its chest (or Station).",
                        "Each Bubie can change it in its menu.")
                .defineInRange("homeRadius", 8, 2, 64);
        DJ_LINKS = b.comment("The DJ may play audio links (.ogg, .mp3). Every player near the Bubie downloads them from that",
                        "address, which sees their IP. Off: discs only. Players can also turn links off for themselves (client config).")
                .define("djLinks", true);
        ADVANCED_JOBS_NEED_CHIP = b.comment("The Miner, Builder, Courier and Guard jobs need a Program Chip installed.",
                        "Off: every job is open from the start. Packs can turn it on to pace progression.")
                .define("advancedJobsNeedChip", false);
        SPEC = b.build();
    }

    private BubieConfig() {
    }
}
