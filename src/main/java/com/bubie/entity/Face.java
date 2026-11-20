package com.bubie.entity;

/** The expression on the Bubie's screen. The renderer picks textures/entity/face/<name>.png. */
public enum Face {
    IDLE, HAPPY, WORK, ERROR, SLEEP, HURT,
    /** Playing music: happy eyes over an equalizer. */
    MUSIC;

    public static Face byId(int id) {
        Face[] values = values();
        return id >= 0 && id < values.length ? values[id] : IDLE;
    }

    public String texture() {
        return name().toLowerCase();
    }
}
