package com.bubie.item;

import com.bubie.Bubie;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Wireless Station: a handheld linked to one Bubie Station (shift + right-click it). Right-click anywhere and it
 * shows that Station's tracker: where each Bubie living there is and what it is doing, however far away.
 */
public class WirelessStationItem extends Item {
    private static final String KEY = "Station";

    /** The Station a handheld is linked to. */
    public record Link(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public WirelessStationItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static Link link(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return null;
        CompoundTag root = custom.copyTag();
        if (!root.contains(KEY)) return null;
        CompoundTag tag = root.getCompoundOrEmpty(KEY);
        Identifier dimension = Identifier.tryParse(tag.getStringOr("Dim", ""));
        if (dimension == null) return null;
        return new Link(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(tag.getLongOr("Pos", 0L)));
    }

    private static void link(ItemStack stack, Level level, BlockPos station) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", level.dimension().identifier().toString());
        tag.putLong("Pos", station.asLong());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, tag));
    }

    /** The one in either hand that is linked, main hand first. */
    @Nullable
    public static Link held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WirelessStationItem) {
                Link link = link(stack);
                if (link != null) return link;
            }
        }
        return null;
    }

    /** Shift + right-click a Station: link to it. Any other block: the same as clicking the air. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isShiftKeyDown() || !level.getBlockState(pos).is(Bubie.STATION.get())) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            link(context.getItemInHand(), level, pos);
            player.sendOverlayMessage(Component.translatable("bubie.wireless.linked", pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GREEN));
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (link(stack) == null) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("bubie.wireless.unlinked").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) com.bubie.client.WirelessClient.open(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        Link link = link(stack);
        if (link != null) {
            tooltip.accept(Component.translatable("bubie.ui.tracker.station", link.pos().getX(), link.pos().getY(), link.pos().getZ())
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.accept(Component.translatable("item.bubie.wireless_station.how1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.bubie.wireless_station.how2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
