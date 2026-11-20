package com.bubie.entity;

/** What the Bubie is doing at the top level, chosen in its menu. */
public enum Mode {
    FOLLOW, STAY, WORK;

    public static Mode byId(int id) {
        Mode[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    public String key() {
        return "bubie.mode." + name().toLowerCase();
    }
}
