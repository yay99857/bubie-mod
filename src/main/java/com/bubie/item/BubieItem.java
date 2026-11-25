package com.bubie.item;

import com.bubie.Bubie;
import com.bubie.BubieConfig;
import com.bubie.data.BubieRegistry;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.JobType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * The Bubie as an item: crafted new, or a Bubie that was folded up (shift + right-click) or shut down at 0 health.
 * Click the ground to switch it on; it keeps its inventory, tool, chips, job and area in between.
 */
public class BubieItem extends Item {
    private static final String KEY = "Bubie";

    public BubieItem(Properties properties) {
        super(properties);
    }

    /** Folds a Bubie into an item (does not remove the entity). */
    public static ItemStack capture(BubieEntity bubie) {
        ItemStack stack = new ItemStack(Bubie.BUBIE_ITEM.get());
        CompoundTag data = new CompoundTag();
        bubie.writeBubieData(data);
        CompoundTag root = new CompoundTag();
        root.put(KEY, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        if (bubie.hasCustomName()) stack.set(DataComponents.CUSTOM_NAME, bubie.getCustomName());
        return stack;
    }

    @Nullable
    private static CompoundTag data(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return null;
        CompoundTag root = custom.copyTag();
        return root.contains(KEY) ? root.getCompound(KEY) : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

        BubieRegistry registry = BubieRegistry.get(server.getServer());
        int max = BubieConfig.maxPerPlayer(server.getServer());
        if (registry.count(player.getUUID()) >= max && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("bubie.message.limit", max), true);
            return InteractionResult.FAIL;
        }
        BubieEntity bubie = Bubie.BUBIE_ENTITY.get().create(server);
        if (bubie == null) return InteractionResult.FAIL;

        ItemStack stack = context.getItemInHand();
        CompoundTag data = data(stack);
        if (data != null) {
            bubie.readBubieData(data);
        } else {
            bubie.setEnergy(12000); // a new Bubie comes with two redstone's worth
        }
        bubie.setOwnerUUID(player.getUUID());
        bubie.setHealth(bubie.getMaxHealth());
        if (stack.has(DataComponents.CUSTOM_NAME)) bubie.setCustomName(stack.getHoverName());

        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) pos = pos.relative(context.getClickedFace());
        bubie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
        bubie.yHeadRot = bubie.getYRot();
        bubie.yBodyRot = bubie.getYRot();
        if (!level.noCollision(bubie)) {
            player.displayClientMessage(Component.translatable("bubie.message.no_room"), true);
            return InteractionResult.FAIL;
        }
        server.addFreshEntity(bubie);
        registry.add(player.getUUID(), bubie.getUUID());
        bubie.showMood(Face.HAPPY, 60);
        server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.6F, 1.8F);
        if (!player.getAbilities().instabuild || data != null) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = data(stack);
        if (data == null) {
            tooltip.add(Component.translatable("item.bubie.bubie.new").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.bubie.bubie.off").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("bubie.tooltip.job",
                Component.translatable(JobType.byId(data.getByte("Job")).key())).withStyle(ChatFormatting.GRAY));
        int max = BubieConfig.SPEC.isLoaded() ? BubieConfig.MAX_ENERGY.get() : 48000;
        tooltip.add(Component.translatable("bubie.tooltip.energy",
                Math.round(100.0F * data.getInt("Energy") / max)).withStyle(ChatFormatting.GRAY));
        int items = 0;
        for (Tag t : data.getList("Storage", Tag.TAG_COMPOUND)) items += ((CompoundTag) t).getInt("count");
        if (items > 0) tooltip.add(Component.translatable("bubie.tooltip.items", items).withStyle(ChatFormatting.GRAY));
        ListTag extra = data.getList("Extra", Tag.TAG_COMPOUND);
        int chips = 0;
        for (Tag t : extra) if (((CompoundTag) t).getByte("Slot") > 0) chips++;
        if (chips > 0) tooltip.add(Component.translatable("bubie.tooltip.chips", chips).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
