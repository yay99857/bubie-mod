package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import com.bubie.entity.WorkArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** DEV ONLY (./gradlew runShots): builds scenes with Bubies at work and saves screenshots to run/screenshots. */
@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class ShotDirector {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final boolean LOGOS = Boolean.getBoolean("bubie.logos");
    private static final boolean ON = Boolean.getBoolean("bubie.shots") || LOGOS;
    private static int cx = 3000, cz = 3000, g;

    private record Step(int delay, Runnable action) {
    }

    private static final List<Step> steps = new ArrayList<>();
    private static int index = -1, timer;

    private ShotDirector() {
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
        p.setDeltaMovement(0, 0, 0);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "bubie_" + name + ".png", mc.getMainRenderTarget(), c -> LOG.info("shot {}", name));
    }

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(cx + x, g + y, cz + z);
    }

    private static BubieEntity spawn(ServerPlayer p, int x, int z, float yaw) {
        ServerLevel level = p.serverLevel();
        BubieEntity b = Bubie.BUBIE_ENTITY.get().create(level);
        b.moveTo(cx + x + 0.5, g, cz + z + 0.5, yaw, 0);
        b.yHeadRot = yaw;
        b.yBodyRot = yaw;
        b.setOwnerUUID(p.getUUID());
        b.setEnergy(40000);
        level.addFreshEntity(b);
        return b;
    }

    private static void worker(ServerPlayer p, int x, int z, JobType job, ItemStack tool, BlockPos a, BlockPos b, BlockPos chest) {
        BubieEntity bubie = spawn(p, x, z, 0);
        bubie.setItemSlot(EquipmentSlot.MAINHAND, tool);
        bubie.setJob(job);
        bubie.setArea(WorkArea.of(a, b));
        p.serverLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        bubie.setChest(chest);
        bubie.setMode(Mode.WORK);
    }

    private static void flatten(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                for (int y = g - 8; y <= g + 30; y++) {
                    m.set(cx + x, y, cz + z);
                    Block b = y >= g ? Blocks.AIR : y == g - 1 ? Blocks.GRASS_BLOCK : y > g - 4 ? Blocks.DIRT : Blocks.STONE;
                    level.setBlock(m, b.defaultBlockState(), 2);
                }
            }
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(cx - 50, g - 10, cz - 50, cx + 50, g + 40, cz + 50),
                e -> !(e instanceof Player))) e.discard();
    }

    private static void scenes(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        // portrait: three Bubies by the camera
        BubieEntity a = spawn(p, -1, 0, 0);
        a.setMode(Mode.STAY);
        BubieEntity b = spawn(p, 0, 0, 0);
        b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        b.setMode(Mode.STAY);
        b.setMode(Mode.FOLLOW);
        BubieEntity c = spawn(p, 1, 0, 0);
        c.showMood(Face.HAPPY, 100000);

        // lumberjack: a few oaks to the east
        for (int[] t : new int[][]{{12, -2}, {16, 2}, {12, 5}}) {
            cmd(p, "place feature minecraft:oak " + (cx + t[0]) + " " + g + " " + (cz + t[1]));
        }
        worker(p, 9, 1, JobType.LUMBERJACK, new ItemStack(Items.IRON_AXE), at(10, 0, -4), at(19, 10, 7), at(8, 0, 4));

        // farm to the west: ripe wheat around a water source
        for (int x = -16; x <= -10; x++) {
            for (int z = -3; z <= 3; z++) {
                BlockPos soil = at(x, -1, z);
                if (x == -13 && z == 0) {
                    level.setBlockAndUpdate(soil, Blocks.WATER.defaultBlockState());
                    continue;
                }
                level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState());
                level.setBlockAndUpdate(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            }
        }
        worker(p, -9, 0, JobType.FARMER, ItemStack.EMPTY, at(-16, 0, -3), at(-10, 1, 3), at(-9, 0, 4));

        // quarry to the south
        worker(p, 0, 12, JobType.QUARRY, new ItemStack(Items.IRON_PICKAXE), at(-2, -4, 14), at(3, -1, 19), at(-4, 0, 13));

        // a line-up of Bubies dressed up in the editor
        com.bubie.entity.Look[] looks = {
                new com.bubie.entity.Look(1, 3, 2, 6, 1), new com.bubie.entity.Look(4, 10, 3, 7, 7),
                new com.bubie.entity.Look(2, 4, 7, 2, 8), new com.bubie.entity.Look(0, 0, 0, 0, 0),
                new com.bubie.entity.Look(7, 5, 6, 3, 4), new com.bubie.entity.Look(6, 7, 0, 1, 2),
                new com.bubie.entity.Look(3, 2, 4, 14, 5)};
        for (int i = 0; i < looks.length; i++) {
            BubieEntity d = spawn(p, -6 + i * 2, -20, 0);
            d.setOwnerUUID(null);
            d.setLook(looks[i]);
            d.setNoAi(true); // posing for the photo
        }

        // a builder raising a base from its chest (when Base Blueprint is installed)
        if (com.bubie.compat.BlueprintCompat.loaded()) {
            BlockPos chestPos = at(-9, 0, -9);
            level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                for (int i = 0; i < 4; i++) chest.setItem(i, new ItemStack(Items.SPRUCE_PLANKS, 64));
            }
            BubieEntity builder = spawn(p, -14, -14, 0);
            builder.setChest(chestPos);
            ItemStack plan = BuilderShots.blueprint();
            p.setPos(cx - 14 + 0.5, g, cz - 6 + 0.5);
            com.bubie.compat.BlueprintCompat.handOver(builder, p, plan);
        }

        // station with a Bubie at home
        level.setBlockAndUpdate(at(4, 0, -6), Bubie.STATION.get().defaultBlockState());
        BubieEntity home = spawn(p, 4, -5, 0);
        home.setHome(at(4, 0, -6));
        home.setMode(Mode.STAY);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (index < 0) {
            mc.options.pauseOnLostFocus = false; // a recording must not stop when another window is in front
            build();
            index = 0;
            timer = 100;
        }
        if (index >= steps.size()) return;
        if (--timer > 0) return;
        steps.get(index).action().run();
        index++;
        if (index < steps.size()) timer = steps.get(index).delay();
    }

    private static void add(int wait, Runnable r) {
        steps.add(new Step(wait, r));
    }

    /** -PdjShots=<link>: the DJ scene only, playing a disc and then that link. */
    private static final String DJ_LINK = System.getProperty("bubie.djshots");

    private static void djScene(ServerPlayer p) {
        BubieEntity dj = spawn(p, 0, 0, 0);
        dj.setMode(Mode.STAY);
        dj.disc().setItem(0, new ItemStack(Items.MUSIC_DISC_PIGSTEP));
        if (DJ_LINK != null && !DJ_LINK.isBlank() && !DJ_LINK.equals("true")) dj.dj().addLink(DJ_LINK);
        dj.dj().toggle();
        BubieEntity friend = spawn(p, 2, 1, 150);
        friend.setLook(new com.bubie.entity.Look(4, 10, 3, 7, 7));
        friend.setMode(Mode.STAY);
    }

    private static void djSteps(Minecraft mc) {
        add(200, () -> server(ShotDirector::flatten));
        add(40, () -> server(ShotDirector::djScene));
        add(20, () -> mc.options.hideGui = true);
        add(20, () -> server(p -> look(p, cx + 1.2, g + 0.4, cz + 3.4, cx + 1.0, g + 0.55, cz + 0.5)));
        add(40, () -> shot("dj_face"));
        add(7, () -> shot("dj_face2"));
        add(5, () -> mc.options.hideGui = false);
        add(5, () -> server(p -> look(p, cx + 0.5, g + 0.6, cz + 2.4, cx + 0.5, g + 0.5, cz + 0.5)));
        add(20, () -> shot("dj_title"));
        add(5, () -> server(p -> {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BubieEntity b = p.serverLevel().getEntitiesOfClass(BubieEntity.class, new AABB(at(0, 0, 0)).inflate(0.6)).stream().findFirst().orElse(null);
            if (b != null) b.interact(p, InteractionHand.MAIN_HAND);
        }));
        add(20, () -> BubieScreen.selectTab(3));
        add(20, () -> shot("menu_dj"));
        add(5, () -> {
            if (mc.screen instanceof BubieScreen screen && screen.getMenu().bubie() != null) {
                mc.setScreen(new DjLinkScreen(screen, screen.getMenu().bubie()));
            }
        });
        add(20, () -> shot("dj_add_link"));
        add(5, () -> mc.setScreen(null));
        // skip the disc: the link plays (the log shows whether it streamed and decoded)
        add(20, () -> server(p -> p.serverLevel().getEntitiesOfClass(BubieEntity.class, new AABB(at(0, 0, 0)).inflate(0.6))
                .forEach(b -> {
                    b.dj().next();
                    LOG.info("DJ now plays {}", b.getDjTrack());
                })));
        add(400, () -> server(p -> p.serverLevel().getEntitiesOfClass(BubieEntity.class, new AABB(at(0, 0, 0)).inflate(0.6))
                .forEach(b -> LOG.info("DJ after 20 s plays {}", b.getDjTrack()))));
        add(20, () -> LOG.info("dj shots done"));
        add(20, () -> Minecraft.getInstance().stop());
    }

    private static void build() {
        Minecraft mc = Minecraft.getInstance();
        if (LOGOS) {
            add(20, () -> mc.options.hideGui = true);
            add(10, () -> mc.setScreen(new LogoScreen(LogoScreen.Kind.MOD)));
            add(30, () -> shot("logo_mod"));
            add(10, () -> mc.setScreen(new LogoScreen(LogoScreen.Kind.PACK)));
            add(30, () -> shot("logo_pack"));
            add(20, () -> Minecraft.getInstance().stop());
            return;
        }
        add(1, () -> server(p -> {
            for (String c : List.of("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "time set 6000", "weather clear", "gamemode creative", "kill @e[type=bubie:bubie]"))
                cmd(p, c);
            var found = p.serverLevel().findClosestBiome3d(bio -> bio.is(net.minecraft.world.level.biome.Biomes.PLAINS),
                    new BlockPos(6000, 64, 6000), 6400, 32, 64);
            if (found != null) {
                cx = found.getFirst().getX();
                cz = found.getFirst().getZ();
            }
            cmd(p, "forceload add " + (cx - 48) + " " + (cz - 48) + " " + (cx + 48) + " " + (cz + 48));
            p.teleportTo(p.serverLevel(), cx, 200, cz, 0, 0);
        }));
        if (DJ_LINK != null) {
            djSteps(mc);
            return;
        }
        add(200, () -> server(ShotDirector::flatten));
        add(40, () -> server(ShotDirector::scenes));
        add(20, () -> mc.options.hideGui = true);
        // portrait, close up
        add(20, () -> server(p -> look(p, cx + 0.5, g + 0.3, cz + 3.6, cx + 0.5, g + 0.55, cz + 0.5)));
        add(60, () -> shot("portrait"));
        add(5, () -> server(p -> look(p, cx + 2.6, g + 0.6, cz + 2.4, cx + 0.5, g + 0.5, cz + 0.5)));
        add(60, () -> shot("portrait_side"));
        // at work
        add(5, () -> server(p -> look(p, cx + 6, g + 2.5, cz + 7, cx + 11, g + 1, cz + 1)));
        add(120, () -> shot("lumberjack"));
        add(5, () -> server(p -> look(p, cx - 6, g + 5, cz + 9, cx - 13, g, cz)));
        add(120, () -> shot("farmer"));
        add(5, () -> server(p -> look(p, cx + 0.5, g + 6, cz + 9, cx + 0.5, g - 2, cz + 16)));
        add(300, () -> shot("quarry"));
        add(5, () -> server(p -> look(p, cx + 6, g + 2, cz - 3, cx + 4, g + 0.5, cz - 5.5)));
        add(40, () -> shot("station"));
        add(5, () -> server(p -> look(p, cx - 7, g + 5, cz - 5, cx - 14, g + 1, cz - 14)));
        add(60, () -> shot("builder"));
        // the menu, one shot per tab
        add(5, () -> mc.options.hideGui = false);
        add(5, () -> server(p -> {
            look(p, cx + 0.5, g, cz + 2.5, cx + 0.5, g + 0.5, cz + 0.5);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BubieEntity b = p.serverLevel().getEntitiesOfClass(BubieEntity.class, new AABB(at(0, 0, 0)).inflate(0.5)).stream().findFirst().orElse(null);
            if (b != null) {
                b.insert(new ItemStack(Items.OAK_LOG, 37));
                b.insert(new ItemStack(Items.WHEAT_SEEDS, 12));
                b.extra().setItem(0, new ItemStack(Items.REDSTONE, 9));
                b.extra().setItem(1, new ItemStack(Bubie.CHIPS.get(com.bubie.item.ChipType.SPEED).get()));
                b.interact(p, InteractionHand.MAIN_HAND);
            }
        }));
        add(30, () -> shot("menu_job"));
        add(5, () -> BubieScreen.selectTab(1));
        add(10, () -> shot("menu_area"));
        add(5, () -> BubieScreen.selectTab(2));
        add(10, () -> shot("menu_status"));
        add(5, () -> {
            if (mc.screen instanceof BubieScreen screen && screen.getMenu().bubie() != null) {
                LookScreen editor = new LookScreen(screen, screen.getMenu().bubie());
                mc.setScreen(editor);
                editor.setLook(new com.bubie.entity.Look(1, 3, 2, 6, 1));
            }
        });
        add(40, () -> shot("editor"));
        add(5, () -> {
            if (mc.screen instanceof LookScreen editor) editor.setLook(new com.bubie.entity.Look(4, 10, 3, 7, 7));
        });
        add(30, () -> shot("editor_mint"));
        add(5, () -> {
            if (mc.screen != null) mc.screen.onClose();
        });
        add(5, () -> BubieScreen.selectTab(0));
        add(5, () -> mc.setScreen(null));
        // the station's menu, with the Bubie that lives there
        add(5, () -> server(p -> {
            BlockPos st = at(4, 0, -6);
            look(p, cx + 4.5, g, cz - 3.5, cx + 4.5, g + 0.5, cz - 5.5);
            if (p.serverLevel().getBlockEntity(st) instanceof net.minecraft.world.MenuProvider provider) {
                p.openMenu(provider, buf -> buf.writeBlockPos(st));
            }
        }));
        add(40, () -> shot("menu_station"));
        add(5, () -> mc.setScreen(null));
        // the line-up
        add(10, () -> {
            mc.options.hideGui = true;
            server(p -> look(p, cx + 0.5, g + 0.9, cz - 14.5, cx + 0.5, g + 0.5, cz - 19.5));
        });
        add(60, () -> shot("lineup"));
        // night: the faces glow
        add(10, () -> {
            mc.options.hideGui = true;
            server(p -> cmd(p, "time set 18000"));
        });
        add(10, () -> server(p -> look(p, cx + 0.5, g + 0.4, cz + 3.4, cx + 0.5, g + 0.6, cz + 0.5)));
        add(80, () -> shot("night"));
        add(10, () -> server(p -> look(p, cx + 0.5, g + 0.9, cz - 14.5, cx + 0.5, g + 0.5, cz - 19.5)));
        add(60, () -> shot("lineup_night"));
        add(20, () -> LOG.info("shots done"));
        add(20, () -> Minecraft.getInstance().stop());
    }
}
