package com.bubie.client;

import net.minecraft.world.item.ItemStack;

/** Stand-in: no Base Blueprint, no blueprint for the screenshot builder. */
public final class BuilderShots {
    private BuilderShots() {
    }

    public static ItemStack blueprint() {
        return ItemStack.EMPTY;
    }
}
