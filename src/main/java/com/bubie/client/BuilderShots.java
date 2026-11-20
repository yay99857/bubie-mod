package com.bubie.client;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** DEV ONLY: the blueprint the screenshot builder gets (uses Base Blueprint classes, so only called when it is loaded). */
public final class BuilderShots {
    private BuilderShots() {
    }

    public static ItemStack blueprint() {
        ItemStack stack = new ItemStack(BaseBlueprint.BLUEPRINT.get());
        stack.set(ModComponents.SETTINGS.get(), new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 7, 7,
                BlueprintSettings.Floor.FILLED, 3, true, BlueprintSettings.Roof.NONE, false, BlueprintSettings.Pick.FLOOR));
        stack.set(ModComponents.MATERIAL.get(), Blocks.SPRUCE_PLANKS);
        return stack;
    }
}
