package com.bubie.compat;

import com.bubie.entity.BubieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Base Blueprint only exists for 1.21.1, so on 1.20.1 there is never a plan to build and the Builder job stays out of
 * the job list. Kept as the one place that would touch Base Blueprint, like on 1.21.1.
 */
public final class BlueprintCompat {
    /** One block of the plan. */
    public record Step(BlockPos pos, Block block) {
    }

    private BlueprintCompat() {
    }

    public static boolean loaded() {
        return false;
    }

    public static boolean isBlueprint(ItemStack stack) {
        return false;
    }

    public static List<Step> plan(BubieEntity bubie) {
        return List.of();
    }
}
