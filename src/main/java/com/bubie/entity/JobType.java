package com.bubie.entity;

/** The job a Bubie does in WORK mode. */
public enum JobType {
    LUMBERJACK, DIGGER, QUARRY, FARMER, MINER, COLLECTOR,
    /** Builds a Base Blueprint plan block by block (needs the Base Blueprint mod). */
    BUILDER,
    /** Carries what is in the containers of its area to its chest (feeds a builder only what its plan needs). */
    COURIER,
    /** Fights the monsters that come into its area, with the weapon in its tool slot; stays out at night. */
    GUARD;

    /** Jobs that need a Program Chip when the server turns on advancedJobsNeedChip. */
    public boolean advanced() {
        return this == MINER || this == BUILDER || this == COURIER || this == GUARD;
    }

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
