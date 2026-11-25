package com.bubie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** An upgrade chip: right-click a Bubie with it, or put it in one of the four chip slots. */
public class ChipItem extends Item {
    private final ChipType type;

    public ChipItem(ChipType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ChipType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.bubie." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.bubie.chip.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
