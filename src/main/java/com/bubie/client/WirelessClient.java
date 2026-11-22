package com.bubie.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Client-only entry point for the Wireless Station item (only ever reached on the client). */
public final class WirelessClient {
    private WirelessClient() {
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TrackerScreen(hand));
    }
}
