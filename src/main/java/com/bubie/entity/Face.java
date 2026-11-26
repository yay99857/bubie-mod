package com.bubie.entity;

/** The expression on the Bubie's screen. The renderer picks textures/face_parts/*. */
public enum Face {
    IDLE, HAPPY, WORK, ERROR, SLEEP, HURT,
    /** Playing music: happy eyes over an equalizer. */
    MUSIC,
    /** Stuck, lost its way: a question mark on the screen. */
    LOST;

    public static Face byId(int id) {
        Face[] values = values();
        return id >= 0 && id < values.length ? values[id] : IDLE;
    }

    public String texture() {
        return name().toLowerCase();
    }
}
