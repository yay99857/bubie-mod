package com.bubie.entity;

/** How the Miner works: exposed ore veins in the area, or branch tunnels at the bottom of it. */
public enum MineMode {
    VEIN, TUNNEL;

    public static MineMode byId(int id) {
        MineMode[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    public String key() {
        return "bubie.mine_mode." + name().toLowerCase();
    }
}
