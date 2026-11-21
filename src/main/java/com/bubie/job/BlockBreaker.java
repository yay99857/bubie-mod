package com.bubie.job;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.item.ChipType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.UUID;

/**
 * Breaks and places blocks for a Bubie through a fake player that carries the owner's UUID, so block protection
 * (FTB Chunks claims, spawn protection mods, BreakEvent listeners) treats the Bubie as its owner. Drops go straight
 * into the Bubie's storage.
 */
@EventBusSubscriber(modid = Bubie.MOD_ID)
public final class BlockBreaker {
    @Nullable
    private static BubieEntity capturing;

    private BlockBreaker() {
    }

    public static FakePlayer player(BubieEntity bubie, ItemStack inHand) {
        ServerLevel level = (ServerLevel) bubie.level();
        UUID id = bubie.getOwnerUUID() != null ? bubie.getOwnerUUID() : bubie.getUUID();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, "[Bubie]"));
        player.setPos(bubie.getX(), bubie.getY(), bubie.getZ());
        player.setYRot(bubie.getYRot());
        player.setXRot(bubie.getXRot());
        player.setOnGround(true);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, inHand);
        return player;
    }

    private static void clear(FakePlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().clearContent();
    }

    /** Mining progress per tick with this tool, as a player standing where the Bubie stands would get. */
    /** An electric tool (it holds Forge Energy) whose battery is empty. */
    public static boolean outOfEnergy(ItemStack tool) {
        var energy = tool.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        return energy != null && energy.getEnergyStored() == 0;
    }

    public static float progressPerTick(BubieEntity bubie, BlockPos pos, BlockState state, ItemStack tool) {
        FakePlayer player = player(bubie, tool);
        try {
            return state.getDestroyProgress(player, bubie.level(), pos);
        } finally {
            clear(player);
        }
    }

    /**
     * Breaks a block with the given tool (the Bubie's own tool, which takes the damage, or EMPTY for bare hands).
     * Returns false if something (a claim, an event) refused it.
     */
    public static boolean breakBlock(BubieEntity bubie, BlockPos pos, ItemStack tool) {
        boolean ownTool = !tool.isEmpty() && tool == bubie.getTool();
        FakePlayer player = player(bubie, tool);
        boolean ok;
        capturing = bubie;
        try {
            ok = player.gameMode.destroyBlock(pos);
        } finally {
            capturing = null;
            if (ownTool) bubie.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem());
            clear(player);
        }
        if (ok) collectLoose(bubie, new AABB(pos).inflate(2.0));
        return ok;
    }

    /** Right-clicks a block with an empty hand (sweet berry bushes). */
    public static boolean useBlock(BubieEntity bubie, BlockPos pos) {
        FakePlayer player = player(bubie, ItemStack.EMPTY);
        try {
            BlockState state = bubie.level().getBlockState(pos);
            InteractionResult result = state.useWithoutItem(bubie.level(), player,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            collectLoose(bubie, new AABB(pos).inflate(1.5));
            return result.consumesAction();
        } finally {
            clear(player);
        }
    }

    /**
     * Places one item (sapling, seed, cobblestone, torch) at pos, as if clicked onto that spot. The item is used
     * up on success; on failure it goes back into storage.
     */
    public static boolean place(BubieEntity bubie, BlockPos pos, ItemStack one) {
        if (one.isEmpty()) return false;
        FakePlayer player = player(bubie, one);
        try {
            InteractionResult result = one.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
            ItemStack left = player.getMainHandItem();
            if (!left.isEmpty()) {
                ItemStack rest = bubie.insert(left);
                if (!rest.isEmpty()) bubie.spawnAtLocation(rest);
            }
            return result.consumesAction();
        } finally {
            clear(player);
        }
    }

    /** Freshly dropped items around a spot (cane tops, berries) go into storage too. */
    public static void collectLoose(BubieEntity bubie, AABB box) {
        for (ItemEntity item : bubie.level().getEntitiesOfClass(ItemEntity.class, box)) {
            if (!item.isAlive() || item.getAge() > 10) continue;
            ItemStack rest = bubie.insert(processed(bubie, item.getItem()));
            if (rest.isEmpty()) item.discard();
            else item.setItem(rest);
        }
    }

    /** With a Smelting Chip, raw ores come out smelted (for extra energy). */
    public static ItemStack processed(BubieEntity bubie, ItemStack stack) {
        if (bubie.chipCount(ChipType.SMELTING) == 0) return stack;
        if (!stack.is(Tags.Items.RAW_MATERIALS) && !stack.is(Tags.Items.ORES)) return stack;
        ServerLevel level = (ServerLevel) bubie.level();
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)
                .map(holder -> {
                    ItemStack result = holder.value().getResultItem(level.registryAccess()).copy();
                    if (result.isEmpty()) return stack;
                    result.setCount(Math.min(result.getMaxStackSize(), result.getCount() * stack.getCount()));
                    bubie.spendEnergy(100.0 * stack.getCount());
                    return result;
                })
                .orElse(stack);
    }

    @SubscribeEvent
    static void onDrops(BlockDropsEvent event) {
        BubieEntity bubie = capturing;
        if (bubie == null || !(event.getBreaker() instanceof FakePlayer)) return;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            ItemStack rest = bubie.insert(processed(bubie, drop.getItem()));
            if (rest.isEmpty()) it.remove();
            else drop.setItem(rest);
        }
    }
}
