package com.bubie.item;

/** Upgrade chips that go in a Bubie's four upgrade slots. */
public enum ChipType {
    /** +50% mining and +20% walking speed per chip. */
    SPEED,
    /** +9 storage slots per chip (two chips: 36). */
    STORAGE,
    /** Work areas up to one more "areaSize" per chip. */
    RANGE,
    /** Picks up items from 5 blocks away instead of 1. */
    MAGNET,
    /** Raw ores come out smelted, for extra energy. */
    SMELTING,
    /** 40% less energy per chip. */
    EFFICIENCY,
    /** Unlocks the advanced jobs (Miner, Builder, Courier, Guard) when the server config asks for it. */
    PROGRAM;

    public String id() {
        return "chip_" + name().toLowerCase();
    }
}
