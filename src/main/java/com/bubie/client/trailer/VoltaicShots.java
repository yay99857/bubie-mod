package com.bubie.client.trailer;

import com.bubie.Bubie;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DEV ONLY (./gradlew runVoltaicShots, with the Voltaic Dynamics jar in runs/trailer/mods): builds a small workshop of
 * Voltaic machines wired with its cables, powers it with a combustion generator, and takes screenshots for the mod's
 * page. Blocks are looked up by id, so Bubie never depends on Voltaic.
 */
@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class VoltaicShots {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = Boolean.getBoolean("bubie.voltaicshots");
    private static int cx = 7000, cz = 7000, g;

    private record Step(int delay, Runnable action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, timer;

    private VoltaicShots() {
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("voltaicdynamics", id));
    }

    private static Item item(String ns, String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ns, id));
    }

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(cx + x, g + y, cz + z);
    }

    private static void server(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void cmd(ServerPlayer p, String c) {
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
    }

    private static void look(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - (y + p.getEyeHeight()), dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "voltaic_" + name + ".png", mc.getMainRenderTarget(), c -> LOG.info("shot {}", name));
    }

    /** A machine facing the camera (south). */
    private static void machine(ServerLevel level, BlockPos pos, String id) {
        BlockState s = block(id).defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        level.setBlockAndUpdate(pos, s);
    }

    private static void insert(ServerLevel level, BlockPos pos, ItemStack... stacks) {
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        if (h == null) h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (h == null) return;
        for (ItemStack stack : stacks) {
            ItemStack rest = stack;
            for (int slot = 0; slot < h.getSlots() && !rest.isEmpty(); slot++) rest = h.insertItem(slot, rest, false);
        }
    }

    private static void build(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                for (int y = g - 4; y <= g + 20; y++) {
                    m.set(cx + x, y, cz + z);
                    boolean pad = Math.abs(x) <= 9 && z >= -4 && z <= 4;
                    Block b = y >= g ? Blocks.AIR : y == g - 1 ? (pad ? Blocks.POLISHED_ANDESITE : Blocks.GRASS_BLOCK) : Blocks.DIRT;
                    level.setBlock(m, b.defaultBlockState(), 2);
                }
            }
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(cx - 40, g - 5, cz - 40, cx + 40, g + 30, cz + 40),
                e -> !(e instanceof Player))) e.discard();
        // a back wall with windows, and trees behind it
        for (int x = -9; x <= 9; x++) {
            for (int y = 0; y <= 4; y++) {
                boolean window = y >= 2 && y <= 3 && Math.abs(x) % 4 == 2;
                level.setBlock(at(x, y, -4), (window ? Blocks.GLASS_PANE : y == 4 ? Blocks.STONE_BRICK_SLAB : Blocks.STONE_BRICKS)
                        .defaultBlockState(), 2);
            }
        }
        for (int[] t : new int[][]{{-14, -10}, {-6, -12}, {4, -11}, {12, -9}, {-18, 2}, {17, 3}}) {
            cmd(p, "place feature minecraft:" + (t[0] % 2 == 0 ? "oak" : "birch") + " " + (cx + t[0]) + " " + g + " " + (cz + t[1]));
        }
        // power: a combustion generator with coal, a fluid generator with lava, a thermoelectric between lava and ice
        machine(level, at(-7, 0, -2), "combustion_generator");
        machine(level, at(-5, 0, -2), "fluid_generator");
        machine(level, at(-8, 0, 1), "thermoelectric_generator");
        level.setBlockAndUpdate(at(-9, 0, 1), Blocks.MAGMA_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(at(-7, 0, 1), Blocks.PACKED_ICE.defaultBlockState());
        machine(level, at(-3, 0, -2), "basic_accumulator");
        machine(level, at(-2, 1, -2), "advanced_accumulator");
        level.setBlockAndUpdate(at(-2, 0, -2), Blocks.STONE_BRICKS.defaultBlockState());
        // the processing line
        machine(level, at(1, 0, -2), "electric_furnace");
        machine(level, at(3, 0, -2), "alloy_smelter");
        machine(level, at(5, 0, -2), "pulverizer");
        machine(level, at(7, 0, -2), "fluid_tank");
        // voltaic cable along the back, touching every machine
        for (int x = -7; x <= 7; x++) level.setBlockAndUpdate(at(x, 0, -3), block("voltaic_cable").defaultBlockState());
        // an item cable from a chest of ores into the pulverizer
        level.setBlockAndUpdate(at(6, 0, 1), Blocks.CHEST.defaultBlockState());
        insert(level, at(6, 0, 1), new ItemStack(Items.RAW_IRON, 48), new ItemStack(Items.RAW_COPPER, 32));
        level.setBlockAndUpdate(at(5, 0, 1), block("item_cable").defaultBlockState());
        level.setBlockAndUpdate(at(5, 0, 0), block("item_cable").defaultBlockState());
        level.setBlockAndUpdate(at(5, 0, -1), block("item_cable").defaultBlockState());
        // fuel and work
        insert(level, at(-7, 0, -2), new ItemStack(Items.COAL_BLOCK, 16));
        insert(level, at(1, 0, -2), new ItemStack(Items.RAW_GOLD, 16));
        insert(level, at(3, 0, -2), new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.COPPER_INGOT, 16), new ItemStack(Items.REDSTONE, 16));
        insert(level, at(5, 0, -2), new ItemStack(Items.RAW_IRON, 16));
        IItemHandler fluidGen = level.getCapability(Capabilities.ItemHandler.BLOCK, at(-5, 0, -2), null);
        if (fluidGen != null) fluidGen.insertItem(0, new ItemStack(Items.LAVA_BUCKET), false);
        // blocks set by code skip the step a player's placement runs: let every cable work out its arms now
        for (BlockPos pos : BlockPos.betweenClosed(at(-9, 0, -4), at(9, 1, 4))) {
            BlockState state = level.getBlockState(pos);
            if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("cable")) {
                level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(state, level, pos));
            }
        }
        level.setBlockAndUpdate(at(-1, 0, 2), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(at(0, 0, 2), Blocks.LANTERN.defaultBlockState());
    }

    private static void add(int wait, Runnable r) {
        STEPS.add(new Step(wait, r));
    }

    private static void plan() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        add(1, () -> server(p -> {
            for (String c : List.of("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "time set 2000", "weather clear", "gamemode creative", "difficulty peaceful"))
                cmd(p, c);
            cmd(p, "forceload add " + (cx - 32) + " " + (cz - 32) + " " + (cx + 32) + " " + (cz + 32));
            p.teleportTo(p.serverLevel(), cx, 150, cz, 0, 0);
        }));
        add(160, () -> server(VoltaicShots::build));
        add(200, () -> mc.options.hideGui = true); // let the generators fill the network and the machines start
        add(20, () -> server(p -> look(p, cx + 0.5, g + 3.2, cz + 9.5, cx + 0.5, g + 0.5, cz - 2)));
        add(60, () -> shot("workshop"));
        add(5, () -> server(p -> look(p, cx - 4.5, g + 1.8, cz + 3.0, cx - 6.5, g + 0.5, cz - 1.5)));
        add(50, () -> shot("generators"));
        add(5, () -> server(p -> look(p, cx + 3.5, g + 1.6, cz + 3.2, cx + 3.5, g + 0.5, cz - 1.5)));
        add(50, () -> shot("machines"));
        add(5, () -> server(p -> look(p, cx + 7.5, g + 2.2, cz + 3.5, cx + 5.5, g + 0.3, cz)));
        add(50, () -> shot("cables"));
        // the alloy smelter's screen
        add(5, () -> mc.options.hideGui = false);
        add(5, () -> server(p -> {
            BlockPos smelter = at(3, 0, -2);
            look(p, cx + 3.5, g, cz + 0.5, cx + 3.5, g + 0.5, cz - 1.5);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            BlockState s = p.serverLevel().getBlockState(smelter);
            s.useWithoutItem(p.serverLevel(), p, new BlockHitResult(Vec3.atCenterOf(smelter), Direction.SOUTH, smelter, false));
        }));
        add(40, () -> shot("alloy_smelter"));
        add(5, () -> mc.setScreen(null));
        // night: the lit machines and the lantern
        add(5, () -> {
            mc.options.hideGui = true;
            server(p -> cmd(p, "time set 18000"));
        });
        add(10, () -> server(p -> look(p, cx + 0.5, g + 2.6, cz + 8.5, cx + 0.5, g + 0.5, cz - 2)));
        add(80, () -> shot("night"));
        add(20, () -> LOG.info("voltaic shots done"));
        add(20, () -> mc.stop());
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (index < 0) {
            plan();
            index = 0;
            timer = 100;
        }
        if (index >= STEPS.size() || --timer > 0) return;
        STEPS.get(index).action().run();
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
