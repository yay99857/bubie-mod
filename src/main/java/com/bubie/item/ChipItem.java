package com.bubie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bubie." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bubie.chip.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
