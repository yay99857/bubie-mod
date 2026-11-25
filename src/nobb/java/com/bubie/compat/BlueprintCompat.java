package com.bubie.compat;

import com.bubie.entity.BubieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Stand-in for builds made without a Base Blueprint for this Minecraft version: the integration is off, so the
 * Builder job never gets a plan. The real class is src/main/java/com/bubie/compat/BlueprintCompat.java.
 */
public final class BlueprintCompat {
    public static final String MOD_ID = "baseblueprint";

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

    public static void handOver(BubieEntity bubie, Player player, ItemStack stack) {
    }

    public static Component handOver(BubieEntity bubie, Player player, ItemStack stack, BlockPos origin) {
        return Component.empty();
    }

    public static List<Step> plan(BubieEntity bubie) {
        return List.of();
    }

    public static List<Step> ghost(BubieEntity bubie) {
        return List.of();
    }
}
