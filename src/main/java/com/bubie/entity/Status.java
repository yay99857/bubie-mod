package com.bubie.entity;

/** What the Bubie is doing now, shown in the menu, on Jade and (for problems) in a balloon over its head. */
public enum Status {
    IDLE(false), FOLLOWING(false), STAYING(false), WORKING(false), COLLECTING(false), DEPOSITING(false),
    GOING_HOME(false), CHARGING(false), RESTING(false), FLEEING(false), DONE(false), NOTHING_TO_DO(false),
    NO_AREA(true), AREA_TOO_BIG(true), NO_CHEST(true), INVENTORY_FULL(true), CHEST_FULL(true), NO_TOOL(true), TOOL_LOW(true),
    NO_ENERGY(true), NEED_FILLER(true), NO_SEEDS(true),
    FETCHING(false), NO_BLUEPRINT(true), NO_MATERIAL(true),
    CARRYING(false), STUCK(true);

    /** Problems the player must fix: the Bubie shows a 404 face and a balloon. */
    public final boolean problem;

    Status(boolean problem) {
        this.problem = problem;
    }

    public static Status byId(int id) {
        Status[] values = values();
        return id >= 0 && id < values.length ? values[id] : IDLE;
    }

    public String key() {
        return "bubie.status." + name().toLowerCase();
    }
}
