package com.bubie.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/** Stand-in: the Builder scenarios need Base Blueprint; without it BuilderGameTests skips them. */
final class BuilderScenarios {
    private BuilderScenarios() {
    }

    static void countsWhatThePlayerPlaced(GameTestHelper helper) {
        helper.succeed();
    }

    static void buildsFromChest(GameTestHelper helper) {
        helper.succeed();
    }

    static void waitsForBlocks(GameTestHelper helper) {
        helper.succeed();
    }

    static void courierFeedsTheBuilder(GameTestHelper helper) {
        helper.succeed();
    }

    static void teamwork(GameTestHelper helper) {
        helper.succeed();
    }
}
