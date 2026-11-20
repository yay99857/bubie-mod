package com.bubie.entity;

/** Blocks the Digger takes; stored as a bit mask, all on by default. */
public enum DigFilter {
    SAND, GRAVEL, DIRT, CLAY;

    public static final int ALL = (1 << values().length) - 1;

    public int bit() {
        return 1 << ordinal();
    }

    public String key() {
        return "bubie.filter." + name().toLowerCase();
    }
}
