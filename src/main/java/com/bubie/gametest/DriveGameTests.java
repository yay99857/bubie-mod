package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.item.ChipType;
import com.bubie.item.DriveItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Drives: the Bubie's cargo lives on its Hard Drive or Solid-State Drive. */
@GameTestHolder(Bubie.MOD_ID)
@PrefixGameTestTemplate(false)
public class DriveGameTests {
    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : stacks) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** A new Bubie has a Hard Drive: 18 slots. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 20)
    public static void newBubieHasAHardDrive(GameTestHelper helper) {
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(7, 1, 7));
        helper.assertTrue(bubie.drive().getItem(0).is(Bubie.HARD_DRIVE.get()), "a Hard Drive in its drive slot");
        helper.assertTrue(bubie.getUnlockedSlots() == 18, "18 slots, got " + bubie.getUnlockedSlots());
        helper.succeed();
    }

    /** A Bubie saved before drives: its Storage Chips become a Solid-State Drive holding what it carried. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 20)
    public static void oldBubieGetsADrive(GameTestHelper helper) {
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(7, 1, 7));
        bubie.insert(new ItemStack(Items.DIAMOND, 5));
        bubie.extra().setItem(2, new ItemStack(Bubie.CHIPS.get(ChipType.STORAGE).get()));
        bubie.extra().setItem(3, new ItemStack(Bubie.CHIPS.get(ChipType.SPEED).get()));
        CompoundTag old = new CompoundTag();
        bubie.writeBubieData(old);
        old.remove("Drive"); // as saved by 1.8.0 and older

        BubieEntity loaded = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(9, 1, 7));
        loaded.readBubieData(old);
        ItemStack drive = loaded.drive().getItem(0);
        helper.assertTrue(drive.is(Bubie.SOLID_STATE_DRIVE.get()), "a Storage Chip became a Solid-State Drive, got " + drive);
        helper.assertTrue(loaded.getUnlockedSlots() == 36, "36 slots, got " + loaded.getUnlockedSlots());
        helper.assertTrue(loaded.extra().getItem(2).isEmpty(), "the Storage Chip is gone from its chip slot");
        helper.assertTrue(loaded.chipCount(ChipType.SPEED) == 1, "the other chips stay");
        helper.assertTrue(loaded.count(Items.DIAMOND) == 5, "it still carries the diamonds");
        helper.assertTrue(count(DriveItem.contents(drive), Items.DIAMOND) == 5, "and they are on the drive");

        CompoundTag noChips = new CompoundTag();
        BubieEntity plain = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(11, 1, 7));
        plain.writeBubieData(noChips);
        noChips.remove("Drive");
        plain.readBubieData(noChips);
        helper.assertTrue(plain.drive().getItem(0).is(Bubie.HARD_DRIVE.get()), "no Storage Chip: a Hard Drive");
        helper.succeed();
    }

    /** Taking the drive out takes the cargo; with no drive it carries one slot; a drive put in brings its cargo back. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 20)
    public static void theDriveCarriesTheCargo(GameTestHelper helper) {
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(7, 1, 7));
        bubie.insert(new ItemStack(Items.DIAMOND, 3));
        bubie.insert(new ItemStack(Items.OAK_LOG, 20));
        ItemStack out = bubie.drive().removeItem(0, 1);
        helper.assertTrue(out.is(Bubie.HARD_DRIVE.get()), "the drive came out");
        helper.assertTrue(!bubie.hasStoredItems(), "the cargo left with it");
        helper.assertTrue(bubie.getUnlockedSlots() == 1, "no drive: one slot, got " + bubie.getUnlockedSlots());
        List<ItemStack> on = DriveItem.contents(out);
        helper.assertTrue(count(on, Items.DIAMOND) == 3 && count(on, Items.OAK_LOG) == 20, "the cargo is on the drive: " + on);

        ItemStack loose = bubie.insert(new ItemStack(Items.COBBLESTONE, 2));
        helper.assertTrue(loose.isEmpty() && bubie.count(Items.COBBLESTONE) == 2, "one slot still carries something");
        helper.assertTrue(!bubie.insert(new ItemStack(Items.DIRT)).isEmpty(), "but only one slot");

        bubie.drive().setItem(0, out);
        helper.assertTrue(bubie.getUnlockedSlots() == 18, "18 slots again");
        helper.assertTrue(bubie.count(Items.DIAMOND) == 3 && bubie.count(Items.OAK_LOG) == 20, "the cargo came back");
        helper.assertTrue(bubie.count(Items.COBBLESTONE) == 2, "and what it carried meanwhile joined it");
        helper.assertTrue(count(DriveItem.contents(bubie.drive().getItem(0)), Items.COBBLESTONE) == 2, "on the drive too");

        // Taking the very last item of a stack via takeOne flushes the empty slot onto the drive immediately
        ItemStack one = bubie.takeOne(s -> s.is(Items.DIAMOND));
        ItemStack two = bubie.takeOne(s -> s.is(Items.DIAMOND));
        ItemStack three = bubie.takeOne(s -> s.is(Items.DIAMOND));
        helper.assertTrue(one.getCount() == 1 && two.getCount() == 1 && three.getCount() == 1, "took all three diamonds");
        ItemStack pulled = bubie.drive().removeItem(0, 1);
        helper.assertTrue(count(DriveItem.contents(pulled), Items.DIAMOND) == 0, "no phantom diamond left on the drive");
        helper.succeed();
    }

    /** The Bubie recipe takes an empty Hard Drive only: nothing on a drive gets lost in crafting. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 20)
    public static void bubieRecipeTakesAnEmptyHardDrive(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(Bubie.id("bubie")).orElse(null);
        helper.assertTrue(holder != null && holder.value() instanceof CraftingRecipe, "the Bubie recipe is loaded");
        // the grid built from the recipe itself, so it holds for the pack's recipe (voltaic ingots) too
        helper.assertTrue(holder.value() instanceof net.minecraft.world.item.crafting.ShapedRecipe, "a shaped recipe");
        var shaped = (net.minecraft.world.item.crafting.ShapedRecipe) holder.value();
        ItemStack empty = new ItemStack(Bubie.HARD_DRIVE.get());
        ItemStack full = new ItemStack(Bubie.HARD_DRIVE.get());
        net.minecraft.world.SimpleContainer cargo = new net.minecraft.world.SimpleContainer(18);
        cargo.setItem(0, new ItemStack(Items.DIAMOND));
        DriveItem.store(full, cargo);
        boolean takesADrive = false;
        for (ItemStack drive : new ItemStack[]{empty, full}) {
            List<ItemStack> grid = new ArrayList<>();
            for (var ingredient : shaped.getIngredients()) {
                ItemStack[] options = ingredient.getItems();
                if (ingredient.isEmpty() || options.length == 0) grid.add(ItemStack.EMPTY);
                else if (options[0].is(Bubie.HARD_DRIVE.get())) {
                    grid.add(drive);
                    takesADrive = true;
                } else grid.add(options[0]);
            }
            boolean matches = shaped.matches(CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid), helper.getLevel());
            helper.assertTrue(matches == (drive == empty), (drive == empty ? "an empty" : "a full") + " Hard Drive "
                    + (matches ? "fits" : "does not fit"));
        }
        helper.assertTrue(takesADrive, "the Bubie recipe takes a Hard Drive");
        helper.succeed();
    }
}
