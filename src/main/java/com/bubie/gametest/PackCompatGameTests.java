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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import static com.bubie.gametest.BubieGameTests.FIELD;
import static com.bubie.gametest.BubieGameTests.floor;

/**
 * Bubie with the mods of the Bubie Pack (and others people mix in): storage it unloads into, crops it farms, ores it
 * mines, power for its station. A test whose mod is missing passes at once, so these run in any setup; to really
 * exercise them, run tools/prepare-pack-test.sh and ./gradlew runPackGameTest.
 */
@GameTestHolder(Bubie.MOD_ID)
@PrefixGameTestTemplate(false)
public class PackCompatGameTests {
    private static final BlockPos BOX = new BlockPos(2, 1, 2);

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static boolean skip(GameTestHelper helper, String mod) {
        if (ModList.get().isLoaded(mod)) return false;
        helper.succeed();
        return true;
    }

    private static int count(GameTestHelper helper, BlockPos rel, Item item) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(rel), null);
        int n = 0;
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) if (handler.getStackInSlot(i).is(item)) n += handler.getStackInSlot(i).getCount();
        }
        return n;
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

    @GameTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoSophisticatedBarrel(GameTestHelper helper) {
        unloadsInto(helper, "sophisticatedstorage", "sophisticatedstorage:barrel");
    }

    @GameTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoSophisticatedChest(GameTestHelper helper) {
        unloadsInto(helper, "sophisticatedstorage", "sophisticatedstorage:chest");
    }

    @GameTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoFunctionalStorageDrawer(GameTestHelper helper) {
        unloadsInto(helper, "functionalstorage", "functionalstorage:oak_1");
    }

    @GameTest(template = FIELD, timeoutTicks = 400)
    public static void unloadsIntoCreateVault(GameTestHelper helper) {
        unloadsInto(helper, "create", "create:item_vault");
    }

    @GameTest(template = FIELD, timeoutTicks = 400)
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

    @GameTest(template = FIELD, timeoutTicks = 800)
    public static void farmsMysticalAgriculture(GameTestHelper helper) {
        farms(helper, "mysticalagriculture", "mysticalagriculture:inferium_crop", "mysticalagriculture:inferium_essence");
    }

    @GameTest(template = FIELD, timeoutTicks = 800)
    public static void farmsFarmersDelightCabbage(GameTestHelper helper) {
        farms(helper, "farmersdelight", "farmersdelight:cabbages", "farmersdelight:cabbage");
    }

    @GameTest(template = FIELD, timeoutTicks = 800)
    public static void minesAllTheOres(GameTestHelper helper) {
        if (skip(helper, "alltheores")) return;
        floor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(7, 1, 7), block("alltheores:tin_ore"));
        BubieGameTests.bubie(helper, new BlockPos(4, 1, 7), JobType.MINER, new ItemStack(Items.STONE_PICKAXE),
                new BlockPos(5, 0, 5), new BlockPos(9, 2, 9));
        helper.succeedWhen(() -> helper.assertTrue(BubieGameTests.inChest(helper, item("alltheores:raw_tin")) >= 1, "raw tin in the chest"));
    }

    @GameTest(template = FIELD, timeoutTicks = 400)
    public static void minesWithAMekanismPickaxe(GameTestHelper helper) {
        if (skip(helper, "mekanismtools")) return;
        floor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.DIAMOND_ORE);
        BubieGameTests.bubie(helper, new BlockPos(4, 1, 7), JobType.MINER, new ItemStack(item("mekanismtools:osmium_pickaxe")),
                new BlockPos(5, 0, 5), new BlockPos(9, 2, 9));
        helper.succeedWhen(() -> helper.assertTrue(BubieGameTests.inChest(helper, Items.DIAMOND) >= 1, "a diamond in the chest"));
    }

    /** Any FE cable (Mekanism, Flux Networks, Powah, Pipez, Voltaic) fills the station the same way: its energy capability. */
    @GameTest(template = FIELD, timeoutTicks = 200)
    public static void stationChargesFromFE(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        BlockPos station = new BlockPos(7, 1, 7);
        helper.setBlock(station, Bubie.STATION.get());
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(station), Direction.UP);
        helper.assertTrue(energy != null && energy.canReceive(), "the station takes FE");
        int got = 0;
        for (int i = 0; i < 50; i++) got += energy.receiveEnergy(100_000, false); // a cable pushes every tick
        helper.assertTrue(got > 0, "the station took some FE");
        final int stored = got;
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(7, 1, 5));
        bubie.setHome(helper.absolutePos(station));
        bubie.setMode(Mode.STAY);
        bubie.setEnergy(0);
        helper.succeedWhen(() -> {
            helper.assertTrue(bubie.getEnergy() >= 2000, "charged from FE, energy " + bubie.getEnergy());
            helper.assertTrue(energy.getEnergyStored() < stored, "the station spent FE");
        });
    }

    /** Pipes and hoppers: coal goes to the fuel slot first; logs never do. */
    @GameTest(template = FIELD, timeoutTicks = 40)
    public static void stationTakesFuelFromPipes(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        BlockPos pos = new BlockPos(7, 1, 7);
        helper.setBlock(pos, Bubie.STATION.get());
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(handler != null, "the station has an item handler");
        net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.COAL, 10), false);
        net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.OAK_LOG, 10), false);
        StationBlockEntity station = helper.getBlockEntity(pos);
        helper.assertTrue(station.items().getStackInSlot(StationBlockEntity.FUEL).is(Items.COAL), "coal in the fuel slot");
        helper.assertTrue(handler.extractItem(0, 1, true).isEmpty(), "fuel can't be pulled out by pipes");
        int logs = 0;
        for (int i = 0; i < StationBlockEntity.STORAGE; i++) if (station.items().getStackInSlot(i).is(Items.OAK_LOG)) logs += station.items().getStackInSlot(i).getCount();
        helper.assertTrue(logs == 10, "logs in storage, not burnt");
        helper.succeed();
    }

    /** Mods that capture mobs (Ender IO soul vials and others) must leave Bubies alone. */
    @GameTest(template = FIELD, timeoutTicks = 20)
    public static void cannotBeCaptured(GameTestHelper helper) {
        helper.assertTrue(Bubie.BUBIE_ENTITY.get().is(net.neoforged.neoforge.common.Tags.EntityTypes.CAPTURING_NOT_SUPPORTED),
                "bubie:bubie is in #c:capturing_not_supported");
        helper.succeed();
    }
}
