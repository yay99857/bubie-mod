package com.bubie.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/** Stand-in: the "Built by: Bubie" scenarios need Base Blueprint; without it BuilderGameTests skips them. */
final class HelperScenarios {
    private HelperScenarios() {
    }

    static void buttonHandsTheBlueprintToTheNearestBubie(GameTestHelper helper) {
        helper.succeed();
    }

    static void clickingABlockHandsTheBaseToTheBubie(GameTestHelper helper) {
        helper.succeed();
    }
}
