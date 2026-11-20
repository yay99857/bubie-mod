package com.bubie.entity;

/** The job a Bubie does in WORK mode. */
public enum JobType {
    LUMBERJACK, DIGGER, QUARRY, FARMER, MINER, COLLECTOR;

    public static JobType byId(int id) {
        JobType[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    public String key() {
        return "bubie.job." + name().toLowerCase();
    }

    public String descriptionKey() {
        return key() + ".desc";
    }
}
