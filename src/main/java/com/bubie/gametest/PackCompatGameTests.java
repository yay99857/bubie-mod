package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.block.StationBlockEntity;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import com.bubie.data.Inv;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import static com.bubie.gametest.BubieGameTests.FIELD;
import static com.bubie.gametest.BubieGameTests.floor;

/**
 * Bubie with the mods of the Bubie Pack (and others people mix in): storage it unloads into, crops it farms, ores it
 * mines, power for its station. A test whose mod is missing passes at once, so these run in any setup; to really
 * exercise them, run tools/prepare-pack-test.sh and ./gradlew runPackGameTest.
 */
public class PackCompatGameTests {
    private static final BlockPos BOX = new BlockPos(2, 1, 2);

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static boolean skip(GameTestHelper helper, String mod) {
        if (ModList.get().isLoaded(mod)) return false;
        helper.succeed();
        return true;
    }

    private static int count(GameTestHelper helper, BlockPos rel, Item item) {
        ResourceHandler<ItemResource> handler = Inv.at(helper.getLevel(), helper.absolutePos(rel), null);
        int n = 0;
        if (handler != null) {
            for (int i = 0; i < handler.size(); i++) if (Inv.get(handler, i).is(item)) n += Inv.get(handler, i).getCount();
        }
        return n;
    }

    /**
     * Inventory Tweaks sorts the open menu's slots that are not the player's (middle click): the Bubie's storage, never
     * its tool, fuel and chips. Runs the mod's own sort on a Bubie menu.
     */
    @BubieTest(template = FIELD)
    public static void inventoryTweaksLeavesTheEquipmentAlone(GameTestHelper helper) throws Exception {
        if (skip(helper, "invtweaks")) return;
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(5, 1, 5));
        ItemStack chip = new ItemStack(Bubie.CHIPS.get(com.bubie.item.ChipType.SPEED).get());
        bubie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        bubie.extra().setItem(com.bubie.entity.BubieEntity.FUEL_SLOT, new ItemStack(Items.REDSTONE, 4));
        bubie.extra().setItem(1, chip.copy());
        // storage out of order, with a gap, so a sort has work to do
        bubie.storage().setItem(3, new ItemStack(Items.DIRT, 10));
        bubie.storage().setItem(5, new ItemStack(Items.COBBLESTONE, 7));
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        var menu = new com.bubie.menu.BubieMenu(1, player.getInventory(), bubie);
        player.containerMenu = menu;
        try {
            Class.forName("invtweaks.util.Sorting")
                    .getMethod("executeSort", net.minecraft.world.entity.player.Player.class, boolean.class, String.class)
                    .invoke(null, player, false, "com.bubie.client.BubieScreen");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.assertTrue(bubie.getMainHandItem().is(Items.IRON_PICKAXE), "the tool stayed, found " + bubie.getMainHandItem());
        helper.assertTrue(bubie.extra().getItem(com.bubie.entity.BubieEntity.FUEL_SLOT).is(Items.REDSTONE), "the fuel stayed");
        helper.assertTrue(bubie.extra().getItem(1).is(chip.getItem()), "the chip stayed");
        helper.assertTrue(!bubie.storage().getItem(0).isEmpty(), "the storage was sorted to the front");
        for (int i = 0; i < bubie.storage().getContainerSize(); i++) {
            ItemStack s = bubie.storage().getItem(i);
            helper.assertTrue(!s.is(Items.IRON_PICKAXE) && !s.is(Items.REDSTONE) && !s.is(chip.getItem()), "equipment moved into storage slot " + i);
        }
        helper.succeed();
    }

    /** A Quarry holding an Atomic Disassembler with an empty battery says so, instead of "working" and skipping blocks. */
    @BubieTest(template = FIELD, timeoutTicks = 200)
    public static void emptyAtomicDisassemblerIsReported(GameTestHelper helper) {
        if (skip(helper, "mekanism")) return;
        floor(helper, Blocks.DIRT);
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(5, 1, 5));
        bubie.setEnergy(40000);
        ItemStack tool = new ItemStack(item("mekanism:atomic_disassembler"));
        EnergyHandler energy = Inv.energy(tool);
        helper.assertTrue(energy != null && energy.getAmountAsLong() == 0, "a new disassembler starts empty");
        bubie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, tool);
        bubie.setJob(JobType.QUARRY);
        bubie.setArea(WorkArea.of(helper.absolutePos(new BlockPos(3, 0, 3)), helper.absolutePos(new BlockPos(7, 0, 7))));
        bubie.setMode(Mode.WORK);
        helper.succeedWhen(() -> helper.assertTrue(bubie.getStatus() == com.bubie.entity.Status.TOOL_NO_ENERGY,
                "status should be TOOL_NO_ENERGY, is " + bubie.getStatus()));
    }

    /** A Bubie with nothing to do in its area unloads what it carries into the storage block at BOX. */
    private static void unloadsInto(GameTestHelper helper, String mod, String storage) {
        if (skip(helper, mod)) return;
        floor(helper, Blocks.STONE);
        helper.setBlock(BOX, block(storage));
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(5, 1, 5));
        bubie.setEnergy(40000);
        bubie.setJob(JobType.FARMER);
        bubie.setArea(WorkArea.of(helper.absolutePos(new BlockPos(6, 1, 6)), helper.absolutePos(new BlockPos(9, 2, 9))));
        bubie.setChest(helper.absolutePos(BOX));
        bubie.insert(new ItemStack(Items.DIAMOND, 10));
        bubie.setMode(Mode.WORK);
        helper.succeedWhen(() -> helper.assertTrue(count(helper, BOX, Items.DIAMOND) == 10,
                storage + " should hold 10 diamonds, has " + count(helper, BOX, Items.DIAMOND) + "; status " + bubie.getStatus()));
    }

    @BubieTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoSophisticatedBarrel(GameTestHelper helper) {
        unloadsInto(helper, "sophisticatedstorage", "sophisticatedstorage:barrel");
    }

    @BubieTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoSophisticatedChest(GameTestHelper helper) {
        unloadsInto(helper, "sophisticatedstorage", "sophisticatedstorage:chest");
    }

    @BubieTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoFunctionalStorageDrawer(GameTestHelper helper) {
        unloadsInto(helper, "functionalstorage", "functionalstorage:oak_1");
    }

    @BubieTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoCreateVault(GameTestHelper helper) {
        unloadsInto(helper, "create", "create:item_vault");
    }

    @BubieTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoMekanismBin(GameTestHelper helper) {
        unloadsInto(helper, "mekanism", "mekanism:basic_bin");
    }

    /** Ripe modded crops on farmland: harvested into the chest, planted again. */
    private static void farms(GameTestHelper helper, String mod, String crop, String product) {
        if (skip(helper, mod)) return;
        floor(helper, Blocks.STONE);
        Block block = block(crop);
        helper.assertTrue(block instanceof CropBlock, crop + " is a crop");
        CropBlock cropBlock = (CropBlock) block;
        for (int x = 6; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, 0, 8), Blocks.FARMLAND);
            helper.setBlock(new BlockPos(x, 1, 8), cropBlock.getStateForAge(cropBlock.getMaxAge()));
        }
        BubieGameTests.bubie(helper, new BlockPos(4, 1, 8), JobType.FARMER, ItemStack.EMPTY,
                new BlockPos(5, 1, 7), new BlockPos(9, 2, 9));
        Item made = item(product);
        helper.succeedWhen(() -> {
            for (int x = 6; x <= 8; x++) {
                BlockState state = helper.getBlockState(new BlockPos(x, 1, 8));
                helper.assertTrue(state.is(block) && cropBlock.getAge(state) < cropBlock.getMaxAge(), crop + " replanted at " + x);
            }
            helper.assertTrue(BubieGameTests.inChest(helper, made) >= 3, "3 " + product + " in the chest");
        });
    }

    @BubieTest(template = FIELD, timeoutTicks = 800)
    public static void farmsMysticalAgriculture(GameTestHelper helper) {
        farms(helper, "mysticalagriculture", "mysticalagriculture:inferium_crop", "mysticalagriculture:inferium_essence");
    }

    @BubieTest(template = FIELD, timeoutTicks = 800)
    public static void farmsFarmersDelightCabbage(GameTestHelper helper) {
        farms(helper, "farmersdelight", "farmersdelight:cabbages", "farmersdelight:cabbage");
    }

    @BubieTest(template = FIELD, timeoutTicks = 800)
    public static void minesAllTheOres(GameTestHelper helper) {
        if (skip(helper, "alltheores")) return;
        floor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(7, 1, 7), block("alltheores:tin_ore"));
        BubieGameTests.bubie(helper, new BlockPos(4, 1, 7), JobType.MINER, new ItemStack(Items.STONE_PICKAXE),
                new BlockPos(5, 0, 5), new BlockPos(9, 2, 9));
        helper.succeedWhen(() -> helper.assertTrue(BubieGameTests.inChest(helper, item("alltheores:raw_tin")) >= 1, "raw tin in the chest"));
    }

    @BubieTest(template = FIELD, timeoutTicks = 400)
    public static void minesWithAMekanismPickaxe(GameTestHelper helper) {
        if (skip(helper, "mekanismtools")) return;
        floor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.DIAMOND_ORE);
        BubieGameTests.bubie(helper, new BlockPos(4, 1, 7), JobType.MINER, new ItemStack(item("mekanismtools:osmium_pickaxe")),
                new BlockPos(5, 0, 5), new BlockPos(9, 2, 9));
        helper.succeedWhen(() -> helper.assertTrue(BubieGameTests.inChest(helper, Items.DIAMOND) >= 1, "a diamond in the chest"));
    }

    /** Any FE cable (Mekanism, Flux Networks, Powah, Pipez, Voltaic) fills the station the same way: its energy capability. */
    @BubieTest(template = FIELD, timeoutTicks = 200)
    public static void stationChargesFromFE(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        BlockPos station = new BlockPos(7, 1, 7);
        helper.setBlock(station, Bubie.STATION.get());
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(station), Direction.UP);
        helper.assertTrue(energy != null && Inv.insertEnergy(energy, 1, true) > 0, "the station takes FE");
        int got = 0;
        for (int i = 0; i < 50; i++) got += Inv.insertEnergy(energy, 100_000, false); // a cable pushes every tick
        helper.assertTrue(got > 0, "the station took some FE");
        final int stored = got;
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(7, 1, 5));
        bubie.setHome(helper.absolutePos(station));
        bubie.setMode(Mode.STAY);
        bubie.setEnergy(0);
        helper.succeedWhen(() -> {
            helper.assertTrue(bubie.getEnergy() >= 2000, "charged from FE, energy " + bubie.getEnergy());
            helper.assertTrue(energy.getAmountAsLong() < stored, "the station spent FE");
        });
    }

    /** Pipes and hoppers: redstone goes to the fuel slot first; coal and logs never do. */
    @BubieTest(template = FIELD, timeoutTicks = 40)
    public static void stationTakesFuelFromPipes(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        BlockPos pos = new BlockPos(7, 1, 7);
        helper.setBlock(pos, Bubie.STATION.get());
        ResourceHandler<ItemResource> handler = Inv.at(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(handler != null, "the station has an item handler");
        Inv.insert(handler, new ItemStack(Items.REDSTONE, 10), false);
        Inv.insert(handler, new ItemStack(Items.OAK_LOG, 10), false);
        StationBlockEntity station = helper.getBlockEntity(pos, StationBlockEntity.class);
        helper.assertTrue(station.stackIn(StationBlockEntity.FUEL).is(Items.REDSTONE), "redstone in the fuel slot");
        helper.assertTrue(Inv.extract(handler, 0, 1, true).isEmpty(), "fuel can't be pulled out by pipes");
        int logs = 0;
        for (int i = 0; i < StationBlockEntity.STORAGE; i++) if (station.stackIn(i).is(Items.OAK_LOG)) logs += station.stackIn(i).getCount();
        helper.assertTrue(logs == 10, "logs in storage, not burnt");
        helper.succeed();
    }

    /** Mods that capture mobs (Ender IO soul vials and others) must leave Bubies alone. */
    @BubieTest(template = FIELD, timeoutTicks = 20)
    public static void cannotBeCaptured(GameTestHelper helper) {
        helper.assertTrue(Bubie.BUBIE_ENTITY.get().builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.EntityTypes.CAPTURING_NOT_SUPPORTED),
                "bubie:bubie is in #c:capturing_not_supported");
        helper.succeed();
    }

    /** A charged battery in the fuel slot charges the Bubie and stays in the slot. */
    private static void chargesFromBattery(GameTestHelper helper, String mod, String battery) {
        if (skip(helper, mod)) return;
        floor(helper, Blocks.STONE);
        ItemStack stack = new ItemStack(item(battery));
        EnergyHandler cell = Inv.energy(stack);
        helper.assertTrue(cell != null, battery + " stores FE");
        for (int i = 0; i < 200 && cell.getAmountAsLong() < cell.getCapacityAsLong(); i++) Inv.insertEnergy(cell, Integer.MAX_VALUE, false);
        long full = cell.getAmountAsLong();
        helper.assertTrue(full > 0, battery + " took a charge");
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(7, 1, 7));
        bubie.setMode(Mode.STAY);
        bubie.setEnergy(0);
        bubie.extra().setItem(BubieEntity.FUEL_SLOT, stack);
        helper.assertTrue(BubieEntity.isFuel(stack), "the fuel slot takes " + battery);
        helper.succeedWhen(() -> {
            ItemStack in = bubie.extra().getItem(BubieEntity.FUEL_SLOT);
            EnergyHandler now = Inv.energy(in);
            helper.assertTrue(in.is(item(battery)), "the battery stays in the slot");
            helper.assertTrue(bubie.getEnergy() > 0, "charged from the battery, energy " + bubie.getEnergy());
            helper.assertTrue(now != null && now.getAmountAsLong() < full, "the battery gave FE");
        });
    }

    @BubieTest(template = FIELD, timeoutTicks = 100)
    public static void chargesFromVoltaicPocketAccumulator(GameTestHelper helper) {
        chargesFromBattery(helper, "voltaicdynamics", "voltaicdynamics:pocket_accumulator");
    }

    @BubieTest(template = FIELD, timeoutTicks = 100)
    public static void chargesFromMekanismEnergyTablet(GameTestHelper helper) {
        chargesFromBattery(helper, "mekanism", "mekanism:energy_tablet");
    }

    /** Mark a Voltaic pulverizer as the drop-off and the miner feeds it raw ore directly. */
    @BubieTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsOreIntoVoltaicPulverizer(GameTestHelper helper) {
        if (skip(helper, "voltaicdynamics")) return;
        floor(helper, Blocks.STONE);
        helper.setBlock(BOX, block("voltaicdynamics:pulverizer"));
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(5, 1, 5));
        bubie.setEnergy(40000);
        bubie.setJob(JobType.FARMER);
        bubie.setArea(WorkArea.of(helper.absolutePos(new BlockPos(6, 1, 6)), helper.absolutePos(new BlockPos(9, 2, 9))));
        bubie.setChest(helper.absolutePos(BOX));
        bubie.insert(new ItemStack(Items.RAW_IRON, 8));
        bubie.setMode(Mode.WORK);
        helper.succeedWhen(() -> helper.assertTrue(count(helper, BOX, Items.RAW_IRON) == 8,
                "the pulverizer should hold 8 raw iron, has " + count(helper, BOX, Items.RAW_IRON) + "; status " + bubie.getStatus()));
    }

    /** The Bubie, Clipboard and Station recipes load (packs may replace them, the Bubie Pack does with Voltaic items). */
    @BubieTest(template = FIELD, timeoutTicks = 20)
    public static void recipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess();
        for (String id : new String[]{"bubie", "bubie_clipboard", "bubie_station", "hard_drive", "solid_state_drive"}) {
            var holder = recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Bubie.id(id)));
            helper.assertTrue(holder.isPresent(), "recipe bubie:" + id + " is loaded");
            StringBuilder items = new StringBuilder();
            for (var ingredient : holder.get().value().placementInfo().ingredients()) {
                ingredient.items().findFirst().ifPresent(item -> items.append(item.getRegisteredName()).append(' '));
            }
            com.mojang.logging.LogUtils.getLogger().info("Bubie recipe bubie:{} uses {}", id, items.toString().trim());
            helper.assertTrue(!items.isEmpty(), "recipe bubie:" + id + " has ingredients");
        }
        // the chips: crafted, or in the Bubie Pack alloyed in Voltaic's Alloy Smelter
        for (com.bubie.item.ChipType chip : com.bubie.item.ChipType.values()) {
            if (chip == com.bubie.item.ChipType.STORAGE) continue; // not crafted any more: it goes into a Solid-State Drive
            String id = "chip_" + chip.name().toLowerCase();
            var holder = recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Bubie.id(id)));
            helper.assertTrue(holder.isPresent(), "recipe bubie:" + id + " is loaded");
            com.mojang.logging.LogUtils.getLogger().info("Bubie recipe bubie:{} is {}", id,
                    BuiltInRegistries.RECIPE_TYPE.getKey(holder.get().value().getType()));
        }
        helper.succeed();
    }
}
