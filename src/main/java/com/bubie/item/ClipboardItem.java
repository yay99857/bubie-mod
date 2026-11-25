package com.bubie.item;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.Mode;
import com.bubie.entity.WorkArea;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Bubie Clipboard. Right-click two blocks to mark the corners of a work area; shift + right-click a chest to mark
 * where items go, or a Bubie Station to mark the Bubie's home; then right-click a Bubie to hand it all over.
 * Shift + right-click the air to clear it.
 */
public class ClipboardItem extends Item {
    public ClipboardItem(Properties properties) {
        super(properties);
    }

    public record Selection(@Nullable BlockPos a, @Nullable BlockPos b, @Nullable BlockPos chest, @Nullable BlockPos home,
                            String dimension) {
        @Nullable
        public WorkArea area() {
            return a != null && b != null ? WorkArea.of(a, b) : null;
        }
    }

    public static Selection read(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        return new Selection(pos(tag, "A"), pos(tag, "B"), pos(tag, "Chest"), pos(tag, "Home"), tag.getString("Dim"));
    }

    @Nullable
    private static BlockPos pos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    private static void write(ItemStack stack, Selection s) {
        CompoundTag tag = new CompoundTag();
        if (s.a() != null) tag.putLong("A", s.a().asLong());
        if (s.b() != null) tag.putLong("B", s.b().asLong());
        if (s.chest() != null) tag.putLong("Chest", s.chest().asLong());
        if (s.home() != null) tag.putLong("Home", s.home().asLong());
        tag.putString("Dim", s.dimension());
        if (tag.size() == 1) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        String dim = level.dimension().location().toString();
        Selection s = read(stack);
        if (!dim.equals(s.dimension())) s = new Selection(null, null, null, null, dim);

        if (player.isShiftKeyDown()) {
            if (level.getBlockState(pos).is(Bubie.STATION.get())) {
                write(stack, new Selection(s.a(), s.b(), s.chest(), pos, dim));
                say(player, Component.translatable("bubie.clipboard.home", pos.getX(), pos.getY(), pos.getZ()));
            } else if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, context.getClickedFace()) != null) {
                write(stack, new Selection(s.a(), s.b(), pos, s.home(), dim));
                say(player, Component.translatable("bubie.clipboard.chest", pos.getX(), pos.getY(), pos.getZ()));
            } else {
                say(player, Component.translatable("bubie.clipboard.not_container"));
            }
            return InteractionResult.CONSUME;
        }
        if (s.a() == null || s.b() != null) {
            write(stack, new Selection(pos, null, s.chest(), s.home(), dim));
            say(player, Component.translatable("bubie.clipboard.corner_a", pos.getX(), pos.getY(), pos.getZ()));
        } else {
            Selection done = new Selection(s.a(), pos, s.chest(), s.home(), dim);
            write(stack, done);
            say(player, Component.translatable("bubie.clipboard.corner_b", done.area().describe()));
        }
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 1.2F);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                stack.remove(DataComponents.CUSTOM_DATA);
                say(player, Component.translatable("bubie.clipboard.cleared"));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** Right-click on a Bubie: it takes the area, chest and home on the clipboard (and starts working). */
    public static void applyTo(ItemStack stack, BubieEntity bubie, Player player) {
        Selection s = read(stack);
        String dim = bubie.level().dimension().location().toString();
        if (!dim.equals(s.dimension()) || (s.area() == null && s.chest() == null && s.home() == null)) {
            say(player, Component.translatable("bubie.clipboard.empty"));
            return;
        }
        WorkArea area = s.area();
        if (area != null && area.largestSide() > bubie.maxAreaSide()) {
            say(player, Component.translatable("bubie.clipboard.too_big", area.describe(), bubie.maxAreaSide()));
            return;
        }
        if (area != null) bubie.setArea(area);
        if (s.chest() != null) bubie.setChest(s.chest());
        if (s.home() != null) bubie.setHome(s.home());
        if (area != null || s.home() != null) bubie.setMode(Mode.WORK);
        bubie.showMood(Face.HAPPY, 40);
        bubie.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 1.8F);
        say(player, Component.translatable("bubie.clipboard.applied", bubie.getDisplayName(),
                Component.translatable(bubie.getJob().key())));
    }

    private static void say(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Selection s = read(stack);
        WorkArea area = s.area();
        if (area != null) {
            tooltip.add(Component.translatable("bubie.clipboard.tip_area", area.describe()).withStyle(ChatFormatting.GOLD));
        } else if (s.a() != null) {
            tooltip.add(Component.translatable("bubie.clipboard.tip_half").withStyle(ChatFormatting.GOLD));
        }
        if (s.chest() != null) tooltip.add(Component.translatable("bubie.clipboard.tip_chest",
                s.chest().getX(), s.chest().getY(), s.chest().getZ()).withStyle(ChatFormatting.GRAY));
        if (s.home() != null) tooltip.add(Component.translatable("bubie.clipboard.tip_home",
                s.home().getX(), s.home().getY(), s.home().getZ()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bubie.bubie_clipboard.how1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bubie.bubie_clipboard.how2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bubie.bubie_clipboard.how3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
