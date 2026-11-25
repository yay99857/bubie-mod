package com.bubie.client.trailer;

import com.bubie.Bubie;
import com.bubie.BubieConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.JobType;
import com.bubie.entity.Look;
import com.bubie.entity.MineMode;
import com.bubie.entity.Mode;
import com.bubie.entity.WorkArea;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DEV ONLY (./gradlew runTrailer): plays the trailer's story in a built set and records each shot to
 * runs/trailer/out/shots/NN_name.mp4. The game clock runs at {@link #RATE} ticks a second while recording and a frame
 * is taken every 2/3 of a tick, so the footage is a smooth 30 fps however slowly the shaders render. The camera is a
 * free rig; the player (the account's own skin) is walked by a fake keyboard. -PtrailerShot=name records one shot.
 */
@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class TrailerDirector {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MODE = System.getProperty("bubie.trailer");
    private static final int FPS = 30, RATE = 3, WARMUP = 50;
    private static final float TICKS_PER_FRAME = 20F / FPS;

    private static int cx, cz, g;

    // ------------------------------------------------------------------ shots

    /** A camera move: position and the point looked at, from start to end, eased. */
    private record Cam(Vec3 from, Vec3 lookFrom, Vec3 to, Vec3 lookTo) {
    }

    private static final class Shot {
        final String name;
        final int ticks;
        Cam cam;
        @Nullable
        Vec3 orbitCenter;
        double orbitRadius, orbitFrom, orbitTo, orbitHeight;
        @Nullable
        Vec3 walkFrom, walkTo;
        Consumer<ServerPlayer> setup = p -> {
        };
        /** Called on the server every tick of the shot, with the tick number. */
        TickAction during = (p, t) -> {
        };
        int warmup = WARMUP;

        Shot(String name, int seconds10) {
            this.name = name;
            this.ticks = seconds10 * 2; // tenths of a second → ticks
        }
    }

    private interface TickAction {
        void run(ServerPlayer p, int t);
    }

    private static final List<Shot> SHOTS = new ArrayList<>();

    private static Vec3 v(double x, double y, double z) {
        return new Vec3(cx + x + 0.5, g + y, cz + z + 0.5);
    }

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(cx + x, g + y, cz + z);
    }

    private static void buildShots() {
        SHOTS.clear();
        // 1. Morning: you walk into the clearing and switch on your first Bubie.
        Shot arrive = new Shot("arrive", 60);
        arrive.cam = new Cam(v(2.6, 1.6, -21.5), v(0, 1.2, -13), v(1.4, 1.75, -12.6), v(0, 0.5, -6));
        arrive.walkFrom = v(0, 0, -18.5);
        arrive.walkTo = v(0, 0, -9.2);
        arrive.setup = p -> cmd(p, "time set 1500");
        arrive.during = (p, t) -> {
            if (t == 50) {
                BubieEntity b = spawn(p, at(0, 0, -6), 180);
                b.setMode(Mode.STAY);
                b.showMood(Face.HAPPY, 400);
                p.level().sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 0.4, b.getZ(), 12, 0.25, 0.2, 0.25, 0.02);
                p.level().playSound(null, b.blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
                        net.minecraft.sounds.SoundSource.NEUTRAL, 1F, 1.4F);
            }
        };
        SHOTS.add(arrive);

        // 2. Hello: the screen lights up with a smile, it waves.
        Shot hello = new Shot("hello", 35);
        hello.cam = new Cam(v(0.9, 0.95, -8.8), v(0, 0.6, -6), v(0.4, 0.8, -8.0), v(0, 0.6, -6));
        hello.setup = p -> heroBubie(p).ifPresent(b -> {
            pose(b, 180);
            b.showMood(Face.HAPPY, 400);
        });
        SHOTS.add(hello);

        // 3. Lumberjack fells a tree.
        Shot lumber = new Shot("lumber", 70);
        // one tree right in front of the camera (trunk at 12, 4), the Bubie next to it
        lumber.cam = new Cam(v(7.5, 1.5, 0.8), v(12, 2.2, 4), v(8.6, 1.0, 2.0), v(11.6, 1.2, 4));
        lumber.setup = p -> {
            hidePlayer(p);
            BubieConfig.BREAK_SPEED.set(0.9); // the felling happens on camera
            BubieEntity b = worker(p, at(11, 0, 4), JobType.LUMBERJACK, new ItemStack(Items.IRON_AXE),
                    at(10, 0, 2), at(14, 12, 6), at(8, 0, -1));
            b.setLook(new Look(0, 0, 0, 0, 0));
        };
        lumber.warmup = 4;
        SHOTS.add(lumber);

        // 4. Stone and ores: a quarry on top of the rock, a miner on its ore face.
        Shot mine = new Shot("mine", 70);
        mine.cam = new Cam(v(-4.5, 1.8, -5.5), v(-11, 1.6, 0), v(-5, 3.2, 4.5), v(-12.5, 2.6, 0));
        mine.setup = p -> {
            hidePlayer(p);
            BubieConfig.BREAK_SPEED.set(2.2);
            worker(p, at(-19, 7, 0), JobType.QUARRY, new ItemStack(Items.DIAMOND_PICKAXE), at(-22, 4, -4), at(-16, 6, 4), at(-9, 0, 9))
                    .setLook(new Look(1, 3, 2, 6, 1));
            BubieEntity miner = worker(p, at(-9, 0, 0), JobType.MINER, new ItemStack(Items.DIAMOND_PICKAXE),
                    at(-13, 0, -6), at(-10, 5, 6), at(-9, 0, 9));
            miner.setMineMode(MineMode.VEIN);
            miner.setLook(new Look(4, 10, 3, 7, 7));
        };
        mine.warmup = 30;
        SHOTS.add(mine);

        // 5. A courier carries the logs home.
        Shot courier = new Shot("courier", 70);
        courier.cam = new Cam(v(3.5, 3.2, -3.5), v(7, 0.4, 1), v(0.5, 2.6, 5.5), v(2.5, 0.3, 9.5));
        courier.setup = p -> {
            hidePlayer(p);
            BubieEntity c = worker(p, at(8, 0, 1), JobType.COURIER, ItemStack.EMPTY, at(7, 0, -2), at(9, 1, 0), at(2, 0, 10));
            c.setLook(new Look(2, 4, 7, 2, 8));
        };
        courier.warmup = 20;
        SHOTS.add(courier);

        // 6. A builder raises the base from its chest.
        Shot build = new Shot("build", 75);
        build.cam = new Cam(v(-5.5, 4, 11), v(0, 1, 16), v(-6.5, 2.6, 19.5), v(0, 1.4, 16));
        build.setup = p -> {
            BlockPos chestPos = at(5, 0, 12);
            p.level().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            if (p.level().getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                for (int i = 0; i < 4; i++) chest.setItem(i, new ItemStack(Items.SPRUCE_PLANKS, 64));
            }
            if (com.bubie.compat.BlueprintCompat.loaded()) {
                BubieEntity b = spawn(p, at(0, 0, 16), 0);
                b.setChest(chestPos);
                b.setLook(new Look(7, 5, 6, 3, 4));
                p.teleportTo(p.level(), cx + 0.5, g, cz + 24.5, 180, 0); // the door faces the camera side
                com.bubie.compat.BlueprintCompat.handOver(b, p, com.bubie.client.BuilderShots.blueprint());
            }
            hidePlayer(p);
        };
        build.warmup = 90;
        SHOTS.add(build);

        // 7. The crew, dressed up.
        Shot crew = new Shot("crew", 45);
        crew.cam = new Cam(v(-7, 1.0, -22.5), v(-5, 0.6, -26), v(7, 1.0, -22.5), v(5, 0.6, -26));
        crew.setup = TrailerDirector::hidePlayer;
        SHOTS.add(crew);

        // 8. Dusk: you walk to the hill with three Bubies; one already plays music.
        Shot dusk = new Shot("dusk", 60);
        dusk.cam = new Cam(v(5, 2.2, 24), v(17, 1, 19), v(9, 1.6, 25.5), v(25, 1.4, 19));
        dusk.walkFrom = v(12, 0, 19);
        dusk.walkTo = v(24.5, 0, 19);
        dusk.setup = p -> {
            cmd(p, "time set 12600");
            p.teleportTo(p.level(), cx + 12.5, g, cz + 19.5, -90, 0);
            followers(p);
        };
        dusk.warmup = 60;
        SHOTS.add(dusk);

        // 9. Into the cave: their faces glow in the dark.
        Shot cave = new Shot("cave", 60);
        cave.cam = new Cam(v(38, 1.3, 19.2), v(28, 0.8, 19), v(36.5, 1.1, 19.4), v(30, 0.5, 19));
        cave.walkFrom = v(25, 0, 19);
        cave.walkTo = v(34, 0, 19);
        cave.setup = p -> {
            cmd(p, "time set 14000");
            p.teleportTo(p.level(), cx + 25.5, g, cz + 19.5, -90, 0);
            double[][] behind = {{24.0, 18.6}, {23.2, 20.2}, {22.4, 19.2}};
            List<BubieEntity> crew1 = crewOf(p);
            for (int i = 0; i < crew1.size() && i < behind.length; i++) crew1.get(i).teleportTo(cx + behind[i][0], g, cz + behind[i][1]);
        };
        cave.warmup = 40;
        SHOTS.add(cave);

        // 10. The party: everyone dances, notes fly.
        Shot party = new Shot("party", 80);
        party.orbitCenter = v(38, 0.7, 19);
        party.orbitRadius = 4.4;
        party.orbitFrom = 150;
        party.orbitTo = 215;
        party.orbitHeight = 0.9;
        party.setup = p -> {
            p.teleportTo(p.level(), cx + 36.0, g, cz + 23.6, -150, 0);
            List<BubieEntity> crew2 = crewOf(p);
            double[][] spots = {{38, 17.2}, {39.3, 19.5}, {38, 21.8}};
            for (int i = 0; i < crew2.size() && i < spots.length; i++) {
                BubieEntity b = crew2.get(i);
                b.setMode(Mode.STAY);
                b.teleportTo(cx + spots[i][0] + 0.5, g, cz + spots[i][1] + 0.5);
                pose(b, 90 + (i - 1) * 25);
            }
        };
        party.warmup = 40;
        SHOTS.add(party);
    }

    // ------------------------------------------------------------------ set and actors

    private static void cmd(ServerPlayer p, String c) {
        p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER).withSuppressedOutput(), c);
    }

    private static BubieEntity spawn(ServerPlayer p, BlockPos pos, float yaw) {
        ServerLevel level = p.level();
        BubieEntity b = Bubie.BUBIE_ENTITY.get().create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        b.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        b.yHeadRot = yaw;
        b.yBodyRot = yaw;
        b.setOwnerUUID(p.getUUID());
        b.setEnergy(40000);
        b.addTag("trailer");
        level.addFreshEntity(b);
        return b;
    }

    private static BubieEntity worker(ServerPlayer p, BlockPos pos, JobType job, ItemStack tool, BlockPos a, BlockPos b, BlockPos chest) {
        BubieEntity bubie = spawn(p, pos, 0);
        bubie.setItemSlot(EquipmentSlot.MAINHAND, tool);
        bubie.setJob(job);
        bubie.setArea(WorkArea.of(a, b));
        if (!p.level().getBlockState(chest).is(Blocks.CHEST)) p.level().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        bubie.setChest(chest);
        bubie.setMode(Mode.WORK);
        return bubie;
    }

    private static java.util.Optional<BubieEntity> heroBubie(ServerPlayer p) {
        return p.level().getEntitiesOfClass(BubieEntity.class, new AABB(at(0, 0, -6)).inflate(1.5)).stream().findFirst();
    }

    private static void followers(ServerPlayer p) {
        Look[] looks = {Look.DEFAULT, new Look(1, 3, 2, 6, 1), new Look(4, 10, 3, 7, 7)};
        int[][] spots = {{10, 17}, {9, 21}, {8, 19}};
        for (int i = 0; i < looks.length; i++) {
            BubieEntity b = spawn(p, at(spots[i][0], 0, spots[i][1]), -90);
            b.setLook(looks[i]);
            b.addTag("crew");
            b.setMode(Mode.FOLLOW);
            if (i == 0) {
                b.disc().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_PIGSTEP));
                b.dj().toggle();
            }
        }
    }

    /** Stands still facing that way (no AI turning its head), still showing faces and dancing. */
    private static void pose(BubieEntity b, float yaw) {
        b.setNoAi(true);
        b.setYRot(yaw);
        b.yRotO = yaw;
        b.setYHeadRot(yaw);
        b.yHeadRotO = yaw;
        b.yBodyRot = yaw;
        b.yBodyRotO = yaw;
    }

    private static List<BubieEntity> crewOf(ServerPlayer p) {
        return p.level().getEntitiesOfClass(BubieEntity.class, new AABB(at(0, 0, 0)).inflate(64), b -> b.getTags().contains("crew"));
    }

    private static void hidePlayer(ServerPlayer p) {
        p.teleportTo(p.level(), cx + 0.5, g - 30, cz + 0.5, 0, 0); // under the set: keeps it loaded, out of sight
    }

    private static void flatten(ServerPlayer p) {
        ServerLevel level = p.level();
        g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -46; x <= 50; x++) {
            for (int z = -46; z <= 46; z++) {
                for (int y = g - 34; y <= g + 30; y++) {
                    m.set(cx + x, y, cz + z);
                    Block b = y >= g ? Blocks.AIR : y == g - 1 ? Blocks.GRASS_BLOCK : y > g - 4 ? Blocks.DIRT : Blocks.STONE;
                    level.setBlock(m, b.defaultBlockState(), 2);
                }
            }
        }
        // the hiding spot for the player under the set
        for (int y = g - 31; y <= g - 28; y++) level.setBlock(new BlockPos(cx, y, cz), Blocks.AIR.defaultBlockState(), 2);
        for (Entity e : level.getEntities((Entity) null, new AABB(cx - 60, g - 40, cz - 60, cx + 60, g + 40, cz + 60),
                e -> !(e instanceof Player))) e.discard();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    private static void buildSet(ServerPlayer p) {
        ServerLevel level = p.level();
        RandomSource r = RandomSource.create(42);
        // a ring of trees around the set, with grass and flowers
        for (int i = 0; i < 70; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 30 + r.nextDouble() * 14;
            int x = (int) (Math.cos(a) * d), z = (int) (Math.sin(a) * d);
            if (x > 20 && z > 0) continue; // the hill side stays open
            cmd(p, "place feature minecraft:" + (r.nextInt(3) == 0 ? "birch" : "oak") + " " + (cx + x) + " " + g + " " + (cz + z));
        }
        for (int i = 0; i < 260; i++) {
            int x = r.nextInt(80) - 40, z = r.nextInt(80) - 40;
            BlockPos pos = at(x, 0, z);
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) continue;
            if (Math.abs(x) < 4 && z > -22 && z < -4) continue; // the path to the clearing
            Block plant = switch (r.nextInt(9)) {
                case 0 -> Blocks.POPPY;
                case 1 -> Blocks.DANDELION;
                case 2 -> Blocks.CORNFLOWER;
                case 3 -> Blocks.OXEYE_DAISY;
                default -> Blocks.SHORT_GRASS;
            };
            set(level, pos, plant.defaultBlockState());
        }
        // lumber grove
        for (int[] t : new int[][]{{12, -4}, {16, 0}, {12, 4}, {19, -5}, {20, 4}}) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, at(t[0] + dx, 0, t[1] + dz), Blocks.AIR.defaultBlockState());
            cmd(p, "place feature minecraft:oak " + (cx + t[0]) + " " + g + " " + (cz + t[1]));
        }
        level.setBlockAndUpdate(at(8, 0, -1), Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(at(8, 0, -1)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_LOG, 24));
            chest.setItem(1, new ItemStack(Items.OAK_SAPLING, 6));
        }
        level.setBlockAndUpdate(at(2, 0, 10), Blocks.CHEST.defaultBlockState());
        // the rock: a lumpy boulder of stone and andesite, ores all over its skin
        Block[] ores = {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE,
                Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.EMERALD_ORE};
        for (int x = -25; x <= -11; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 0; y <= 8; y++) {
                    double bump = Math.sin(x * 0.9) * 0.08 + Math.cos(z * 1.3) * 0.08 + Math.sin((x + z) * 0.5) * 0.06;
                    double d = Math.pow((x + 18) / 7.0, 2) + Math.pow(z / 7.5, 2) + Math.pow(y / 6.5, 2);
                    if (d > 1 + bump) continue;
                    boolean skin = Math.pow((x + 18) / 6.0, 2) + Math.pow(z / 6.5, 2) + Math.pow(y / 5.5, 2) > 1 + bump;
                    Block b = r.nextInt(4) == 0 ? Blocks.ANDESITE : r.nextInt(9) == 0 ? Blocks.COBBLESTONE : Blocks.STONE;
                    if (skin && r.nextInt(4) == 0) b = ores[r.nextInt(ores.length)];
                    set(level, at(x, y, z), b.defaultBlockState());
                }
            }
        }
        for (int i = 0; i < 18; i++) { // moss and grass creeping up its foot
            BlockPos pos = at(-25 + r.nextInt(15), 0, -8 + r.nextInt(17));
            if (level.getBlockState(pos).is(Blocks.STONE)) set(level, pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
        // the base site
        for (int x = -4; x <= 4; x++) for (int z = 12; z <= 20; z++) set(level, at(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
        // the line-up of looks
        Look[] looks = {new Look(1, 3, 2, 6, 1), new Look(4, 10, 3, 7, 7), new Look(2, 4, 7, 2, 8), Look.DEFAULT,
                new Look(7, 5, 6, 3, 4), new Look(6, 7, 0, 1, 2), new Look(3, 2, 4, 14, 5)};
        for (int i = 0; i < looks.length; i++) {
            BubieEntity d = spawn(p, at(-6 + i * 2, 0, -26), 0);
            d.setOwnerUUID(null);
            d.setLook(looks[i]);
            d.setNoAi(true);
            if (i % 3 == 1) d.showMood(Face.HAPPY, 100000);
        }
        buildHill(level, r);
    }

    /** A grassy hill with a tunnel into a lit chamber full of ores and amethyst. */
    private static void buildHill(ServerLevel level, RandomSource r) {
        double hx = 37, hz = 19;
        for (int x = 24; x <= 50; x++) {
            for (int z = 6; z <= 32; z++) {
                double d = Math.sqrt((x - hx) * (x - hx) / (13.0 * 13.0) + (z - hz) * (z - hz) / (13.0 * 13.0));
                if (d > 1) continue;
                int h = (int) Math.round(10 * Math.cos(d * Math.PI / 2));
                for (int y = 0; y <= h; y++) {
                    Block b = y == h ? Blocks.GRASS_BLOCK : y > h - 3 ? Blocks.DIRT : r.nextInt(5) == 0 ? Blocks.ANDESITE : Blocks.STONE;
                    set(level, at(x, y, z), b.defaultBlockState());
                }
            }
        }
        Block[] ores = {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE,
                Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_GOLD_ORE};
        // tunnel along +x, 3 wide and 4 high
        for (int x = 23; x <= 33; x++) for (int z = 18; z <= 20; z++) for (int y = 0; y <= 3; y++) set(level, at(x, y, z), Blocks.AIR.defaultBlockState());
        // chamber: a squashed dome
        for (int x = 31; x <= 44; x++) {
            for (int z = 12; z <= 26; z++) {
                for (int y = 0; y <= 7; y++) {
                    double d = Math.pow((x - 37.5) / 6.5, 2) + Math.pow((z - 19) / 7.0, 2) + Math.pow(y / 7.0, 2);
                    if (d < 1) set(level, at(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        // walls: deepslate low, ores, amethyst and glow lichen
        for (int x = 22; x <= 46; x++) {
            for (int z = 10; z <= 28; z++) {
                for (int y = -1; y <= 9; y++) {
                    BlockPos pos = at(x, y, z);
                    if (level.getBlockState(pos).isAir()) continue;
                    boolean nearAir = false;
                    for (Direction dir : Direction.values()) if (level.getBlockState(pos.relative(dir)).isAir() && x > 23) nearAir = true;
                    if (!nearAir) continue;
                    BlockState here = level.getBlockState(pos);
                    if ((here.is(Blocks.GRASS_BLOCK) || here.is(Blocks.DIRT)) && y >= 0) continue;
                    if (y <= 0) set(level, pos, (r.nextInt(3) == 0 ? Blocks.GRAVEL : Blocks.DEEPSLATE).defaultBlockState());
                    else if (r.nextInt(5) == 0) set(level, pos, ores[r.nextInt(ores.length)].defaultBlockState());
                    else if (r.nextInt(14) == 0) set(level, pos, Blocks.AMETHYST_BLOCK.defaultBlockState());
                }
            }
        }
        // clusters, lichen, lanterns, torches
        for (int i = 0; i < 400; i++) {
            BlockPos pos = at(30 + r.nextInt(16), 1 + r.nextInt(7), 11 + r.nextInt(17));
            if (!level.getBlockState(pos).isAir()) continue;
            for (Direction dir : Direction.values()) {
                BlockState wall = level.getBlockState(pos.relative(dir.getOpposite()));
                if (wall.isAir() || !wall.isSolid()) continue;
                if (wall.is(Blocks.AMETHYST_BLOCK) && r.nextInt(2) == 0) {
                    set(level, pos, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, dir));
                } else if (r.nextInt(6) == 0) {
                    set(level, pos, Blocks.GLOW_LICHEN.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(dir.getOpposite()), true));
                }
                break;
            }
        }
        for (int[] l : new int[][]{{35, 16}, {40, 22}, {37, 19}, {41, 15}}) {
            int top = 6;
            while (top > 2 && level.getBlockState(at(l[0], top + 1, l[1])).isAir()) top++;
            set(level, at(l[0], top, l[1]), Blocks.CHAIN.defaultBlockState());
            set(level, at(l[0], top - 1, l[1]), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        for (int x = 25; x <= 32; x += 3) {
            set(level, at(x, 2, 17), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
            set(level, at(x + 1, 2, 21), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
        }
        set(level, at(39, 1, 15), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true)
                .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3));
    }

    // ------------------------------------------------------------------ driving

    private enum Phase { WAIT_WORLD, PREPARE, WARMUP, RECORD, DONE }

    private static Phase phase = Phase.WAIT_WORLD;
    private static int timer, shotIndex = -1, shotTick;
    private static float nextFrame;
    @Nullable
    private static ArmorStand rig;
    @Nullable
    private static Recorder recorder;
    private static final PuppetInput PUPPET = new PuppetInput();

    /** A keyboard that walks the player toward a point. */
    private static final class PuppetInput extends Input {
        boolean walking;

        @Override
        public void tick(boolean slow, float factor) {
            forwardImpulse = walking ? 1.0F : 0.0F;
            up = walking;
            leftImpulse = 0;
            jumping = false;
            shiftKeyDown = false;
        }
    }

    private static void server(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) action.accept(p);
        });
    }

    private static boolean selected(Shot shot) {
        return MODE.equals("all") || MODE.equals(shot.name);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (MODE == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        switch (phase) {
            case WAIT_WORLD -> {
                mc.options.pauseOnLostFocus = false;
                BubieUi.hideHud(mc, true);
                mc.player.input = PUPPET;
                phase = Phase.PREPARE;
                timer = 0;
                server(p -> {
                    // the director sets the speed of everything; Bubies chop a bit faster for the camera
                    BubieConfig.BREAK_SPEED.set(2.2);
                    for (String c : List.of("gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                            "gamerule doMobSpawning false", "gamerule doFireTick false", "gamerule randomTickSpeed 0",
                            "difficulty peaceful", "time set 1500", "weather clear", "gamemode creative", "tick rate 20",
                            "kill @e[type=bubie:bubie]")) cmd(p, c);
                    var found = p.level().findClosestBiome3d(b -> b.is(net.minecraft.world.level.biome.Biomes.FLOWER_FOREST)
                            || b.is(net.minecraft.world.level.biome.Biomes.FOREST), new BlockPos(9000, 64, 9000), 6400, 32, 64);
                    if (found != null) {
                        cx = found.getFirst().getX();
                        cz = found.getFirst().getZ();
                    } else {
                        cx = 9000;
                        cz = 9000;
                    }
                    cmd(p, "forceload add " + (cx - 64) + " " + (cz - 64) + " " + (cx + 64) + " " + (cz + 64));
                    p.teleportTo(p.level(), cx, 200, cz, 0, 0);
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                });
            }
            case PREPARE -> {
                timer++;
                if (timer == 200) server(TrailerDirector::flatten);
                if (timer == 240) {
                    buildShots();
                    server(p -> {
                        buildSet(p);
                        p.getAbilities().flying = false;
                        p.onUpdateAbilities();
                    });
                }
                if (timer == 320) nextShot(mc);
            }
            case WARMUP -> {
                Shot shot = SHOTS.get(shotIndex);
                moveCamera(shot, 0);
                if (--timer > 0) return;
                startRecording(mc, shot);
            }
            case RECORD -> {
                Shot shot = SHOTS.get(shotIndex);
                shotTick++;
                int t = shotTick;
                server(p -> shot.during.run(p, t));
                moveCamera(shot, shotTick);
                walk(mc, shot);
                if (shotTick >= shot.ticks) {
                    stopRecording(shot);
                    nextShot(mc);
                }
            }
            case DONE -> {
            }
        }
    }

    private static void nextShot(Minecraft mc) {
        do {
            shotIndex++;
        } while (shotIndex < SHOTS.size() && !selected(SHOTS.get(shotIndex)));
        if (shotIndex >= SHOTS.size()) {
            phase = Phase.DONE;
            LOG.info("Trailer: all shots recorded");
            server(p -> cmd(p, "tick rate 20"));
            mc.stop();
            return;
        }
        Shot shot = SHOTS.get(shotIndex);
        server(p -> {
            cmd(p, "tick rate 20");
            shot.setup.accept(p);
            if (shot.walkFrom != null) {
                Vec3 f = shot.walkFrom, to = shot.walkTo;
                float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - f.x), to.z - f.z));
                p.teleportTo(p.level(), f.x, f.y, f.z, yaw, 0);
            }
        });
        if (rig == null) {
            rig = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, mc.level);
            rig.setInvisible(true);
        }
        mc.setCameraEntity(rig);
        PUPPET.walking = false;
        moveCamera(shot, 0);
        moveCamera(shot, 0);
        timer = shot.warmup;
        phase = Phase.WARMUP;
        LOG.info("Trailer: shot {} ({} ticks)", shot.name, shot.ticks);
    }

    private static void startRecording(Minecraft mc, Shot shot) {
        server(p -> cmd(p, "tick rate " + RATE));
        var target = mc.getMainRenderTarget();
        try {
            File out = new File(mc.gameDirectory, String.format("out/shots/%02d_%s.mp4", shotIndex + 1, shot.name));
            recorder = new Recorder(out, target.width, target.height, FPS);
        } catch (IOException e) {
            LOG.error("Trailer: can't start ffmpeg", e);
            phase = Phase.DONE;
            return;
        }
        shotTick = 0;
        nextFrame = 0;
        PUPPET.walking = shot.walkFrom != null;
        phase = Phase.RECORD;
    }

    private static void stopRecording(Shot shot) {
        PUPPET.walking = false;
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.zza = 0;
        if (recorder != null) {
            int frames = recorder.frames();
            recorder.finish();
            recorder = null;
            LOG.info("Trailer: shot {} recorded, {} frames", shot.name, frames);
        }
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Post event) {
        if (phase != Phase.RECORD || recorder == null) return;
        Minecraft mc = Minecraft.getInstance();
        float now = shotTick + mc.getTimer().getGameTimeDeltaPartialTick(false);
        if (now < nextFrame) return;
        recorder.capture(mc.getMainRenderTarget());
        nextFrame += TICKS_PER_FRAME;
        if (nextFrame < now - 2) nextFrame = now; // never try to catch up a whole stall
    }

    /** The player walks toward the shot's end point (the fake keyboard pushes forward; we steer). */
    private static void walk(Minecraft mc, Shot shot) {
        if (shot.walkTo == null || mc.player == null) return;
        Vec3 to = shot.walkTo;
        double dx = to.x - mc.player.getX(), dz = to.z - mc.player.getZ();
        if (dx * dx + dz * dz < 0.3) {
            PUPPET.walking = false;
            mc.player.zza = 0;
            return;
        }
        // with a free camera the player ignores its keyboard: push it ourselves
        mc.player.zza = PUPPET.walking ? 1.0F : 0.0F;
        mc.player.xxa = 0;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(4);
    }

    private static double ease(double t) {
        return t * t * (3 - 2 * t);
    }

    private static void moveCamera(Shot shot, int tick) {
        if (rig == null) return;
        double t = ease(Mth.clamp(tick / (double) shot.ticks, 0, 1));
        Vec3 pos, look;
        if (shot.orbitCenter != null) {
            double a = Math.toRadians(Mth.lerp(t, shot.orbitFrom, shot.orbitTo));
            pos = shot.orbitCenter.add(Math.cos(a) * shot.orbitRadius, shot.orbitHeight, Math.sin(a) * shot.orbitRadius);
            look = shot.orbitCenter;
        } else {
            Cam c = shot.cam;
            pos = c.from().lerp(c.to(), t);
            look = c.lookFrom().lerp(c.lookTo(), t);
        }
        double dx = look.x - pos.x, dy = look.y - pos.y, dz = look.z - pos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        rig.xo = rig.getX();
        rig.yo = rig.getY();
        rig.zo = rig.getZ();
        rig.xOld = rig.xo;
        rig.yOld = rig.yo;
        rig.zOld = rig.zo;
        rig.yRotO = rig.getYRot();
        rig.xRotO = rig.getXRot();
        rig.yHeadRotO = rig.yHeadRot;
        // keep the angle continuous (no spin when crossing ±180)
        float last = rig.getYRot();
        yaw = last + Mth.wrapDegrees(yaw - last);
        rig.setPos(pos.x, pos.y - rig.getEyeHeight(), pos.z);
        rig.setYRot(yaw);
        rig.setXRot(pitch);
        rig.yHeadRot = yaw;
        if (tick == 0) {
            rig.xo = rig.xOld = rig.getX();
            rig.yo = rig.yOld = rig.getY();
            rig.zo = rig.zOld = rig.getZ();
            rig.yRotO = rig.yHeadRotO = yaw;
            rig.xRotO = pitch;
        }
    }
}
