package com.bubie.entity;

import com.bubie.Bubie;
import com.bubie.BubieConfig;
import com.bubie.data.BubieRegistry;
import com.bubie.entity.ai.BubieFleeGoal;
import com.bubie.entity.ai.FollowOwnerGoal;
import com.bubie.entity.ai.RestGoal;
import com.bubie.entity.ai.WorkGoal;
import com.bubie.item.BubieItem;
import com.bubie.item.ChipItem;
import com.bubie.item.ChipType;
import com.bubie.item.ClipboardItem;
import com.bubie.job.JobRunner;
import com.bubie.menu.BubieMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Bubie robot. Everything the client needs to draw it, its menu and its area preview is in synced entity data;
 * the work itself runs on the server in {@link JobRunner}, driven by {@link WorkGoal}.
 */
public class BubieEntity extends PathfinderMob implements OwnableEntity {
    public static final int STORAGE_SLOTS = 36;
    public static final int BASE_STORAGE = 18;
    /** Extra container: slot 0 is fuel, 1-4 are upgrade chips. */
    public static final int FUEL_SLOT = 0;
    public static final int CHIP_SLOTS = 4;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = define(EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> DATA_MODE = define(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_JOB = define(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_MINE_MODE = define(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FILTER = define(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FACE = define(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_STATUS = define(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_UNLOCKED = define(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ENERGY = define(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX_ENERGY = define(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_AREA_MIN = define(EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_AREA_MAX = define(EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_CHEST = define(EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_HOME = define(EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_LOOK = define(EntityDataSerializers.INT);
    /** What a waiting Builder is missing (item and how many), for the balloon, the menu and Jade. */
    private static final EntityDataAccessor<ItemStack> DATA_NEED = define(EntityDataSerializers.ITEM_STACK);
    /** Builder progress: total blocks of the plan (high 16 bits) and blocks still to place (low 16 bits). */
    private static final EntityDataAccessor<Integer> DATA_BUILD = define(EntityDataSerializers.INT);
    /** The Builder's plan, for the client to draw the blocks still to place: the blueprint, where, the door side. */
    private static final EntityDataAccessor<ItemStack> DATA_PLAN = define(EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_PLAN_AT = define(EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Byte> DATA_PLAN_DOOR = define(EntityDataSerializers.BYTE);
    /** The DJ: the track playing ({@link Dj.Track#encode}) and the volume (0-100). */
    private static final EntityDataAccessor<String> DATA_DJ = define(EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> DATA_DJ_VOLUME = define(EntityDataSerializers.BYTE);
    /** How far around its chest (or Station) it works when no area is marked. */
    private static final EntityDataAccessor<Byte> DATA_RADIUS = define(EntityDataSerializers.BYTE);

    private static final ResourceLocation SPEED_CHIP_ID = Bubie.id("speed_chip");

    private static <T> EntityDataAccessor<T> define(net.minecraft.network.syncher.EntityDataSerializer<T> serializer) {
        return SynchedEntityData.defineId(BubieEntity.class, serializer);
    }

    private final SimpleContainer storage = new SimpleContainer(STORAGE_SLOTS);
    private final SimpleContainer extra = new SimpleContainer(1 + CHIP_SLOTS);
    /** The DJ's music disc, in a slot of its own: never stored away in a chest, never taking a storage slot. */
    private final SimpleContainer disc = new SimpleContainer(1);
    private final Map<ChipType, Integer> chips = new EnumMap<>(ChipType.class);
    private final JobRunner runner = new JobRunner(this);
    private final Dj dj = new Dj(this);
    private double energyDebt;
    /** Builder: Bubie's own copy of the blueprint, where the base goes and which side gets the door. */
    private ItemStack buildPlan = ItemStack.EMPTY;
    @Nullable
    private BlockPos buildOrigin;
    private net.minecraft.core.Direction buildDoor = net.minecraft.core.Direction.SOUTH;
    private boolean loading;
    private Face moodFace = Face.IDLE;
    private int moodTicks;
    /** Shows this Bubie's area to its owner for a while ("Show in world" in the menu); client only. */
    public long previewUntil;
    /** While the look editor is open, the look being tried on; client only. */
    @Nullable
    public Look previewLook;

    public BubieEntity(EntityType<? extends BubieEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        setDropChance(EquipmentSlot.MAINHAND, 0);
        extra.addListener(container -> onChipsChanged());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0) // the Guard; a weapon in the tool slot adds its own damage
                .add(Attributes.ATTACK_KNOCKBACK, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_MODE, (byte) Mode.FOLLOW.ordinal());
        builder.define(DATA_JOB, (byte) JobType.LUMBERJACK.ordinal());
        builder.define(DATA_MINE_MODE, (byte) MineMode.VEIN.ordinal());
        builder.define(DATA_FILTER, (byte) DigFilter.ALL);
        builder.define(DATA_FACE, (byte) Face.IDLE.ordinal());
        builder.define(DATA_STATUS, (byte) Status.IDLE.ordinal());
        builder.define(DATA_UNLOCKED, (byte) BASE_STORAGE);
        builder.define(DATA_ENERGY, 0);
        builder.define(DATA_MAX_ENERGY, 48000);
        builder.define(DATA_AREA_MIN, Optional.empty());
        builder.define(DATA_AREA_MAX, Optional.empty());
        builder.define(DATA_CHEST, Optional.empty());
        builder.define(DATA_HOME, Optional.empty());
        builder.define(DATA_LOOK, Look.DEFAULT.pack());
        builder.define(DATA_NEED, ItemStack.EMPTY);
        builder.define(DATA_BUILD, 0);
        builder.define(DATA_PLAN, ItemStack.EMPTY);
        builder.define(DATA_PLAN_AT, Optional.empty());
        builder.define(DATA_PLAN_DOOR, (byte) net.minecraft.core.Direction.SOUTH.get3DDataValue());
        builder.define(DATA_DJ, "");
        builder.define(DATA_DJ_VOLUME, (byte) 70);
        builder.define(DATA_RADIUS, (byte) 8);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BubieFleeGoal(this));
        goalSelector.addGoal(2, new RestGoal(this));
        goalSelector.addGoal(3, new WorkGoal(this));
        goalSelector.addGoal(4, new FollowOwnerGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ---------------------------------------------------------------- synced state

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID owner) {
        entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(getOwnerUUID());
    }

    /** Owners, their FTB Teams teammates, and operators in creative may give orders. */
    public boolean canCommand(Player player) {
        return isOwnedBy(player) || (player.isCreative() && player.hasPermissions(2))
                || (!level().isClientSide && com.bubie.compat.Teams.sameTeam(getOwnerUUID(), player.getUUID()));
    }

    public Mode getMode() {
        return Mode.byId(entityData.get(DATA_MODE));
    }

    public void setMode(Mode mode) {
        if (mode == getMode()) return;
        entityData.set(DATA_MODE, (byte) mode.ordinal());
        getNavigation().stop();
        runner.reset();
    }

    public JobType getJob() {
        return JobType.byId(entityData.get(DATA_JOB));
    }

    public void setJob(JobType job) {
        if (job == getJob()) return;
        entityData.set(DATA_JOB, (byte) job.ordinal());
        runner.reset();
    }

    public MineMode getMineMode() {
        return MineMode.byId(entityData.get(DATA_MINE_MODE));
    }

    public void setMineMode(MineMode mode) {
        entityData.set(DATA_MINE_MODE, (byte) mode.ordinal());
        runner.reset();
    }

    public int getDigFilter() {
        return entityData.get(DATA_FILTER);
    }

    public void setDigFilter(int mask) {
        entityData.set(DATA_FILTER, (byte) (mask & DigFilter.ALL));
        runner.reset();
    }

    /** Client-side display copies (logo renders) set their face directly. */
    public void setDisplayFace(Face face) {
        entityData.set(DATA_FACE, (byte) face.ordinal());
    }

    public Face getFace() {
        return Face.byId(entityData.get(DATA_FACE));
    }

    public Status getStatus() {
        return Status.byId(entityData.get(DATA_STATUS));
    }

    public void setStatus(Status status) {
        entityData.set(DATA_STATUS, (byte) status.ordinal());
    }

    public int getUnlockedSlots() {
        return entityData.get(DATA_UNLOCKED);
    }

    public int getEnergy() {
        return entityData.get(DATA_ENERGY);
    }

    public int getMaxEnergy() {
        return entityData.get(DATA_MAX_ENERGY);
    }

    public void setEnergy(int energy) {
        entityData.set(DATA_ENERGY, Math.max(0, Math.min(getMaxEnergy(), energy)));
    }

    public float energyFraction() {
        return getMaxEnergy() <= 0 ? 0 : (float) getEnergy() / getMaxEnergy();
    }

    public boolean hasEnergy() {
        return !BubieConfig.REQUIRE_ENERGY.get() || getEnergy() > 0;
    }

    /** Spends work energy; fractions add up so efficiency chips work at any rate. */
    public void spendEnergy(double amount) {
        if (!BubieConfig.REQUIRE_ENERGY.get()) return;
        energyDebt += amount * Math.pow(0.6, chipCount(ChipType.EFFICIENCY));
        if (energyDebt >= 1) {
            int whole = (int) energyDebt;
            energyDebt -= whole;
            setEnergy(getEnergy() - whole);
        }
    }

    @Nullable
    public WorkArea getMarkedArea() {
        Optional<BlockPos> min = entityData.get(DATA_AREA_MIN), max = entityData.get(DATA_AREA_MAX);
        return min.isPresent() && max.isPresent() ? new WorkArea(min.get(), max.get()) : null;
    }

    public void setArea(@Nullable WorkArea area) {
        entityData.set(DATA_AREA_MIN, area == null ? Optional.empty() : Optional.of(area.min()));
        entityData.set(DATA_AREA_MAX, area == null ? Optional.empty() : Optional.of(area.max()));
        runner.reset();
    }

    public void setBuildPlan(ItemStack plan, BlockPos origin, net.minecraft.core.Direction door) {
        buildPlan = plan;
        buildOrigin = origin;
        buildDoor = door;
        syncBuildPlan();
        runner.reset();
    }

    private void syncBuildPlan() {
        entityData.set(DATA_PLAN, buildPlan.copy());
        entityData.set(DATA_PLAN_AT, Optional.ofNullable(buildOrigin));
        entityData.set(DATA_PLAN_DOOR, (byte) buildDoor.get3DDataValue());
    }

    /** A new plan of this many blocks: progress starts at zero. */
    public void setBuildTotal(int total) {
        int t = Math.min(0xFFFF, Math.max(0, total));
        entityData.set(DATA_BUILD, t << 16 | t);
    }

    public int getBuildTotal() {
        return entityData.get(DATA_BUILD) >>> 16;
    }

    public int getBuildLeft() {
        return entityData.get(DATA_BUILD) & 0xFFFF;
    }

    public void setBuildLeft(int left) {
        int l = Math.min(0xFFFF, Math.max(0, left));
        int total = Math.max(getBuildTotal(), l); // older saves had no total: the first count becomes it
        int packed = total << 16 | l;
        if (entityData.get(DATA_BUILD) != packed) entityData.set(DATA_BUILD, packed);
    }

    // ---------------------------------------------------------------- DJ

    public Dj dj() {
        return dj;
    }

    public Dj.Track getDjTrack() {
        return Dj.Track.decode(entityData.get(DATA_DJ));
    }

    void setDjTrack(Dj.Track track) {
        entityData.set(DATA_DJ, track.encode());
    }

    public int getDjVolume() {
        return entityData.get(DATA_DJ_VOLUME);
    }

    public void setDjVolume(int volume) {
        entityData.set(DATA_DJ_VOLUME, (byte) Math.max(0, Math.min(100, volume)));
    }

    public ItemStack getNeed() {
        return entityData.get(DATA_NEED);
    }

    public void setNeed(ItemStack need) {
        ItemStack now = entityData.get(DATA_NEED);
        if (!ItemStack.matches(now, need)) entityData.set(DATA_NEED, need.copy());
    }

    /** The status line, with what a waiting Builder is missing. */
    public net.minecraft.network.chat.Component statusText() {
        Status status = getStatus();
        ItemStack need = getNeed();
        if (status == Status.NO_MATERIAL && !need.isEmpty()) {
            return net.minecraft.network.chat.Component.translatable("bubie.status.no_material.item", need.getHoverName(), need.getCount());
        }
        return net.minecraft.network.chat.Component.translatable(status.key());
    }

    public ItemStack getBuildPlan() {
        return level().isClientSide ? entityData.get(DATA_PLAN) : buildPlan;
    }

    @Nullable
    public BlockPos getBuildOrigin() {
        return level().isClientSide ? entityData.get(DATA_PLAN_AT).orElse(null) : buildOrigin;
    }

    public net.minecraft.core.Direction getBuildDoor() {
        return level().isClientSide ? net.minecraft.core.Direction.from3DDataValue(entityData.get(DATA_PLAN_DOOR)) : buildDoor;
    }

    /** How many of an item the Bubie carries. */
    public int count(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) if (storage.getItem(i).is(item)) n += storage.getItem(i).getCount();
        return n;
    }

    /** The marked area, or for surface jobs, the ground around the station. */
    @Nullable
    public WorkArea getWorkArea() {
        if (getJob() == JobType.BUILDER) {
            // the builder works around its plan; the area only feeds the outline and loose-item checks
            BlockPos origin = getBuildOrigin();
            return origin == null ? null : WorkArea.around(origin, 8, 1, 8);
        }
        WorkArea marked = getMarkedArea();
        if (marked != null) return marked;
        BlockPos center = getWorkCenter();
        return center == null ? null : areaAround(center, getWorkRadius(), getJob());
    }

    /** Where it works without a marked area: its chest, or else its Station. */
    @Nullable
    public BlockPos getWorkCenter() {
        return getChest() != null ? getChest() : getHome();
    }

    /**
     * The area a job covers around a centre. Jobs that only take what is theirs (trees, crops, ores, items, monsters,
     * containers) work all around it; the Quarry and the Digger take everything, so they open a pit in front of the
     * chest (the way it faces), from 2 blocks away, and never touch the ground the base stands on.
     */
    public WorkArea areaAround(BlockPos c, int r, JobType job) {
        return switch (job) {
            case LUMBERJACK -> WorkArea.around(c, r, 3, 24);
            case MINER -> WorkArea.around(c, r, 24, 4);
            case QUARRY -> pitInFront(c, r, 16, 1);
            case DIGGER -> pitInFront(c, r, 6, 4);
            default -> WorkArea.around(c, r, 3, 5);
        };
    }

    private WorkArea pitInFront(BlockPos c, int r, int down, int up) {
        net.minecraft.core.Direction front = level().getBlockState(c)
                .getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                .orElse(net.minecraft.core.Direction.NORTH);
        net.minecraft.core.Direction side = front.getClockWise();
        BlockPos near = c.relative(front, 2).relative(side, -r), far = c.relative(front, 1 + 2 * r).relative(side, r);
        return WorkArea.of(near.below(down), far.above(up));
    }

    public int getWorkRadius() {
        return Math.min(entityData.get(DATA_RADIUS), maxWorkRadius());
    }

    public void setWorkRadius(int r) {
        entityData.set(DATA_RADIUS, (byte) Math.max(2, Math.min(maxWorkRadius(), r)));
        runner.reset();
    }

    /** The widest radius whose area still fits the size limit (Range Chips raise it). */
    public int maxWorkRadius() {
        return Math.max(2, Math.min(60, (maxAreaSide() - 1) / 2));
    }

    /** Whether this job is locked until a Program Chip is installed (server config). */
    public boolean jobLocked() {
        return getJob().advanced() && BubieConfig.ADVANCED_JOBS_NEED_CHIP.get() && chipCount(ChipType.PROGRAM) == 0;
    }

    public int maxAreaSide() {
        return BubieConfig.AREA_SIZE.get() * (1 + chipCount(ChipType.RANGE));
    }

    @Nullable
    public BlockPos getChest() {
        return entityData.get(DATA_CHEST).orElse(null);
    }

    public void setChest(@Nullable BlockPos pos) {
        entityData.set(DATA_CHEST, Optional.ofNullable(pos));
    }

    @Nullable
    public BlockPos getHome() {
        return entityData.get(DATA_HOME).orElse(null);
    }

    public void setHome(@Nullable BlockPos pos) {
        entityData.set(DATA_HOME, Optional.ofNullable(pos));
    }

    /** The look to draw: what the editor is trying on, else the saved one. */
    public Look getLook() {
        return previewLook != null ? previewLook : getSavedLook();
    }

    public Look getSavedLook() {
        return Look.unpack(entityData.get(DATA_LOOK));
    }

    public void setLook(Look look) {
        entityData.set(DATA_LOOK, look.pack());
    }

    // ---------------------------------------------------------------- inventory

    public SimpleContainer storage() {
        return storage;
    }

    public SimpleContainer extra() {
        return extra;
    }

    public SimpleContainer disc() {
        return disc;
    }

    public static boolean isDisc(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);
    }

    public JobRunner runner() {
        return runner;
    }

    public ItemStack getTool() {
        return getMainHandItem();
    }

    public int chipCount(ChipType type) {
        return chips.getOrDefault(type, 0);
    }

    private void onChipsChanged() {
        if (loading) return;
        chips.clear();
        for (int i = 1; i <= CHIP_SLOTS; i++) {
            if (extra.getItem(i).getItem() instanceof ChipItem chip) chips.merge(chip.type(), 1, Integer::sum);
        }
        if (level().isClientSide) return;
        int unlocked = Math.min(STORAGE_SLOTS, BASE_STORAGE + 9 * chipCount(ChipType.STORAGE));
        entityData.set(DATA_UNLOCKED, (byte) unlocked);
        // Items left in slots that just got locked move to open slots, or drop.
        for (int i = unlocked; i < STORAGE_SLOTS; i++) {
            ItemStack stack = storage.removeItemNoUpdate(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = insert(stack);
            if (!rest.isEmpty()) spawnAtLocation(rest);
        }
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_CHIP_ID);
            int n = chipCount(ChipType.SPEED);
            if (n > 0) speed.addOrUpdateTransientModifier(
                    new AttributeModifier(SPEED_CHIP_ID, 0.2 * n, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** Puts a stack into the open storage slots; returns what did not fit. */
    public ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        int unlocked = getUnlockedSlots();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < unlocked && !rest.isEmpty(); i++) {
                ItemStack in = storage.getItem(i);
                if (pass == 0 && !in.isEmpty() && ItemStack.isSameItemSameComponents(in, rest)) {
                    int move = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                    if (move > 0) {
                        in.grow(move);
                        rest.shrink(move);
                    }
                } else if (pass == 1 && in.isEmpty()) {
                    storage.setItem(i, rest);
                    rest = ItemStack.EMPTY;
                }
            }
        }
        storage.setChanged();
        return rest;
    }

    public boolean hasFreeSlot() {
        for (int i = 0; i < getUnlockedSlots(); i++) if (storage.getItem(i).isEmpty()) return true;
        return false;
    }

    public boolean hasStoredItems() {
        for (int i = 0; i < getUnlockedSlots(); i++) if (!storage.getItem(i).isEmpty()) return true;
        return false;
    }

    /** Takes one item matching the test from storage, or EMPTY. */
    public ItemStack takeOne(java.util.function.Predicate<ItemStack> test) {
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack in = storage.getItem(i);
            if (!in.isEmpty() && test.test(in)) {
                storage.setChanged();
                return in.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Bubie energy from one item of fuel: redstone dust, or a block of redstone (nine dusts). Coal is not fuel. */
    public static int fuelValue(ItemStack stack) {
        int dust = BubieConfig.REDSTONE_ENERGY.get();
        if (stack.is(net.neoforged.neoforge.common.Tags.Items.DUSTS_REDSTONE)) return dust;
        if (stack.is(net.neoforged.neoforge.common.Tags.Items.STORAGE_BLOCKS_REDSTONE)) return dust * 9;
        return 0;
    }

    /** What the fuel slot takes: anything that burns, or any item that stores FE (batteries, pocket accumulators). */
    public static boolean isFuel(ItemStack stack) {
        if (fuelValue(stack) > 0) return true;
        var energy = stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        return energy != null && energy.canExtract();
    }

    /** Bubie energy per second a battery in the fuel slot can give (as fast as the station). */
    private static final int BATTERY_RATE = 800;

    private void burnFuel() {
        if (!BubieConfig.REQUIRE_ENERGY.get()) return;
        ItemStack fuel = extra.getItem(FUEL_SLOT);
        var battery = fuel.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        if (battery != null && battery.canExtract()) {
            int need = Math.min(BATTERY_RATE, getMaxEnergy() - getEnergy());
            int fePer = Math.max(1, BubieConfig.FE_PER_ENERGY.get());
            if (need <= 0) return;
            int fe = battery.extractEnergy(need * fePer, false);
            if (fe >= fePer) {
                setEnergy(getEnergy() + fe / fePer);
                extra.setChanged();
            }
            return;
        }
        int value = fuelValue(fuel);
        // a block of redstone holds more than a full battery: it goes in once half of it fits
        if (value > 0 && getMaxEnergy() - getEnergy() >= Math.min(value, getMaxEnergy() / 2)) {
            ItemStack remainder = fuel.getCraftingRemainingItem();
            fuel.shrink(1);
            if (fuel.isEmpty() && !remainder.isEmpty()) extra.setItem(FUEL_SLOT, remainder);
            extra.setChanged();
            setEnergy(getEnergy() + value);
        }
    }

    // ---------------------------------------------------------------- mood

    /** Shows a face for a moment (happy after a job is done, for example). */
    public void showMood(Face face, int ticks) {
        moodFace = face;
        moodTicks = ticks;
    }

    /**
     * A DJ plays within 10 blocks (checked once a second). Only a Bubie sitting in Stay mode dances along: one that
     * follows or works keeps at it, with its own face.
     */
    private boolean hearsMusic;

    private void listenForMusic() {
        if (tickCount % 20 != 0) return;
        hearsMusic = getMode() == Mode.STAY && getStatus() != Status.NO_ENERGY && !level().getEntitiesOfClass(BubieEntity.class,
                getBoundingBox().inflate(10), b -> b != this && !b.getDjTrack().isNone()).isEmpty();
    }

    private Face pickFace() {
        if (hurtTime > 0) return Face.HURT;
        if (moodTicks > 0) return moodFace;
        if (!getDjTrack().isNone() || (hearsMusic && getMode() == Mode.STAY)) return Face.MUSIC;
        Status status = getStatus();
        if (status == Status.NO_ENERGY || status == Status.CHARGING || status == Status.RESTING) return Face.SLEEP;
        if (status == Status.STUCK) return Face.LOST;
        if (status.problem) return Face.ERROR;
        if (status == Status.WORKING) return Face.WORK;
        return Face.IDLE;
    }

    // ---------------------------------------------------------------- ticking

    /** Server CPU time spent ticking Bubies (AI, pathfinding, work), for the performance GameTest. */
    public static long profileNanos, profileTicks;

    /**
     * Client only: the player holding the "show my Bubies" key (null when nobody is). Their Bubies glow with an outline
     * that shows through walls; set by the key handler on the client, never on a server.
     */
    @Nullable
    public static volatile UUID highlightFor;
    private static final int HIGHLIGHT_RANGE = 128;

    @Override
    public boolean isCurrentlyGlowing() {
        if (level().isClientSide()) {
            UUID who = highlightFor;
            if (who != null && who.equals(getOwnerUUID())) {
                net.minecraft.world.entity.player.Player player = level().getPlayerByUUID(who);
                if (player != null && player.distanceToSqr(this) < HIGHLIGHT_RANGE * HIGHLIGHT_RANGE) return true;
            }
        }
        return super.isCurrentlyGlowing();
    }

    /** The outline is the amber of its screen light. */
    @Override
    public int getTeamColor() {
        return highlightFor != null && getTeam() == null ? 0xF4BF69 : super.getTeamColor();
    }
    /** Only the performance test's own Bubies are timed. */
    public boolean profiled;

    @Override
    public void tick() {
        if (level().isClientSide) {
            super.tick();
            return;
        }
        if (!profiled) {
            super.tick();
            return;
        }
        // CPU time of this thread, not wall time: other processes and GC pauses don't count against the Bubie
        var threads = java.lang.management.ManagementFactory.getThreadMXBean();
        long start = threads.getCurrentThreadCpuTime();
        super.tick();
        profileNanos += threads.getCurrentThreadCpuTime() - start;
        profileTicks++;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        setAirSupply(getMaxAirSupply()); // no lungs
        if (moodTicks > 0) moodTicks--;
        dj.tick();
        listenForMusic();
        reportToTracker();
        entityData.set(DATA_FACE, (byte) pickFace().ordinal());
        if (getMaxEnergy() != BubieConfig.MAX_ENERGY.get()) entityData.set(DATA_MAX_ENERGY, BubieConfig.MAX_ENERGY.get());
        if (tickCount % 20 == 0) {
            burnFuel();
            if (getHealth() < getMaxHealth() && tickCount % 100 == 0) heal(1.0F);
        }
        if (tickCount % 4 == 0 && getMode() != Mode.STAY) vacuumItems();
        if (getMode() == Mode.STAY) {
            getNavigation().stop();
            setStatus(Status.STAYING);
        } else if (getMode() == Mode.FOLLOW && getStatus() != Status.FLEEING) {
            setStatus(Status.FOLLOWING);
        }
        if (getStatus().problem && tickCount % 40 == 0 && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.05, getZ(), 2, 0.15, 0.05, 0.15, 0.01);
        }
    }

    /** Every two seconds a Bubie with a home tells the world's registry where it is and what it is doing. */
    private void reportToTracker() {
        if (tickCount % 40 != 0 || !(level() instanceof ServerLevel server)) return;
        BubieRegistry registry = BubieRegistry.get(server.getServer());
        BlockPos home = getHome();
        if (home == null) {
            registry.forget(getUUID());
            return;
        }
        registry.sight(getUUID(), new BubieRegistry.Sighting(getName().getString(), level().dimension().location().toString(),
                blockPosition().asLong(), home.asLong(), getMode() == Mode.WORK ? getJob().ordinal() : -1 - getMode().ordinal(),
                getStatus().ordinal(), getFace().ordinal(), Math.round(energyFraction() * 100), getLook().pack()));
    }

    private void vacuumItems() {
        double radius = chipCount(ChipType.MAGNET) > 0 ? 5.0 : 1.0;
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(radius, 0.5, radius))) {
            if (!item.isAlive() || item.hasPickUpDelay()) continue;
            ItemStack stack = item.getItem();
            int before = stack.getCount();
            ItemStack rest = insert(stack);
            int taken = before - rest.getCount();
            if (taken <= 0) continue;
            take(item, taken);
            if (rest.isEmpty()) item.discard();
            else item.setItem(rest);
        }
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level().isClientSide && !canCommand(player)) {
            player.displayClientMessage(Component.translatable("bubie.message.not_yours"), true);
            return InteractionResult.CONSUME;
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;

        if (held.getItem() instanceof ClipboardItem) {
            ClipboardItem.applyTo(held, this, player);
            return InteractionResult.CONSUME;
        }
        if (com.bubie.compat.BlueprintCompat.isBlueprint(held) && com.bubie.compat.BlueprintCompat.loaded()) {
            com.bubie.compat.BlueprintCompat.handOver(this, player, held);
            return InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof ChipItem) {
            for (int i = 1; i <= CHIP_SLOTS; i++) {
                if (extra.getItem(i).isEmpty()) {
                    extra.setItem(i, held.split(1));
                    showMood(Face.HAPPY, 40);
                    playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.6F);
                    return InteractionResult.CONSUME;
                }
            }
            player.displayClientMessage(Component.translatable("bubie.message.chips_full"), true);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && held.isEmpty()) {
            packUp(player);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new BubieMenu(id, inv, this), getDisplayName()), buf -> buf.writeVarInt(getId()));
        }
        return InteractionResult.CONSUME;
    }

    /** Shift + right-click with an empty hand: the Bubie folds into its item, keeping everything. */
    public void packUp(Player player) {
        ItemStack stack = BubieItem.capture(this);
        if (!player.getInventory().add(stack)) spawnAtLocation(stack);
        playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 0.7F);
        discard();
    }

    // ---------------------------------------------------------------- life and death

    /** At 0 health a Bubie shuts down into its item, inventory and settings included. It never dies for good. */
    @Override
    public void die(DamageSource source) {
        if (!isRemoved() && !dead && level() instanceof ServerLevel server) {
            ItemStack stack = BubieItem.capture(this);
            ItemEntity drop = spawnAtLocation(stack, 0.5F);
            if (drop != null) {
                drop.setUnlimitedLifetime();
                drop.setInvulnerable(true);
            }
            server.playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.8F, 1.6F);
            LivingEntity owner = getOwner();
            if (owner instanceof Player player) {
                player.displayClientMessage(Component.translatable("bubie.message.shut_down", getDisplayName(),
                        blockPosition().getX(), blockPosition().getY(), blockPosition().getZ()), false);
            }
        }
        super.die(source);
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        // everything is inside the item made in die()
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && level() instanceof ServerLevel server) {
            BubieRegistry.get(server.getServer()).remove(getOwnerUUID(), getUUID());
            BubieRegistry.get(server.getServer()).forget(getUUID());
            runner.release();
        }
        super.remove(reason);
    }

    /** Machines that attack as fake players (mob crushers, deployers, turrets) leave Bubies alone. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof net.neoforged.neoforge.common.util.FakePlayer) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    /** It takes no fall damage, so it may drop into a pit a few blocks deep to get to work. */
    @Override
    public int getMaxFallDistance() {
        return 4;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ---------------------------------------------------------------- sounds

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return getStatus() == Status.CHARGING || getStatus() == Status.RESTING ? null : SoundEvents.NOTE_BLOCK_BIT.value();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.NOTE_BLOCK_BASS.value();
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public float getVoicePitch() {
        return 1.4F + random.nextFloat() * 0.6F;
    }

    // ---------------------------------------------------------------- save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeBubieData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readBubieData(tag);
    }

    /** Settings and inventory: saved with the entity and carried by the Bubie item. */
    public void writeBubieData(CompoundTag tag) {
        UUID owner = getOwnerUUID();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putByte("Mode", (byte) getMode().ordinal());
        tag.putByte("Job", (byte) getJob().ordinal());
        tag.putByte("MineMode", (byte) getMineMode().ordinal());
        tag.putByte("DigFilter", (byte) getDigFilter());
        tag.putByte("Radius", entityData.get(DATA_RADIUS));
        tag.putInt("Energy", getEnergy());
        tag.put("Look", getSavedLook().save());
        WorkArea area = getMarkedArea();
        if (area != null) tag.put("Area", area.save());
        if (getChest() != null) tag.putLong("Chest", getChest().asLong());
        if (getHome() != null) tag.putLong("Home", getHome().asLong());
        tag.put("Storage", saveContainer(storage));
        tag.put("Extra", saveContainer(extra));
        tag.put("Disc", saveContainer(disc));
        if (!getTool().isEmpty()) tag.put("Tool", getTool().save(registryAccess()));
        dj.save(tag);
        if (!buildPlan.isEmpty() && buildOrigin != null) {
            CompoundTag build = new CompoundTag();
            build.put("Blueprint", buildPlan.save(registryAccess()));
            build.putLong("Origin", buildOrigin.asLong());
            build.putByte("Door", (byte) buildDoor.get3DDataValue());
            build.putInt("Progress", entityData.get(DATA_BUILD));
            tag.put("Build", build);
        }
    }

    public void readBubieData(CompoundTag tag) {
        setOwnerUUID(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        entityData.set(DATA_MODE, tag.getByte("Mode"));
        entityData.set(DATA_JOB, tag.getByte("Job"));
        entityData.set(DATA_MINE_MODE, tag.getByte("MineMode"));
        entityData.set(DATA_FILTER, tag.contains("DigFilter") ? tag.getByte("DigFilter") : (byte) DigFilter.ALL);
        entityData.set(DATA_RADIUS, tag.contains("Radius") ? tag.getByte("Radius")
                : (byte) (BubieConfig.SPEC.isLoaded() ? BubieConfig.HOME_RADIUS.get() : 8));
        entityData.set(DATA_ENERGY, tag.getInt("Energy"));
        setLook(tag.contains("Look") ? Look.load(tag.getCompound("Look")) : Look.DEFAULT);
        entityData.set(DATA_MAX_ENERGY, BubieConfig.SPEC.isLoaded() ? BubieConfig.MAX_ENERGY.get() : 48000);
        WorkArea area = tag.contains("Area") ? WorkArea.load(tag.getCompound("Area")) : null;
        entityData.set(DATA_AREA_MIN, area == null ? Optional.empty() : Optional.of(area.min()));
        entityData.set(DATA_AREA_MAX, area == null ? Optional.empty() : Optional.of(area.max()));
        setChest(tag.contains("Chest") ? BlockPos.of(tag.getLong("Chest")) : null);
        setHome(tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null);
        loading = true;
        try {
            loadContainer(storage, tag.getList("Storage", 10));
            loadContainer(extra, tag.getList("Extra", 10));
            loadContainer(disc, tag.getList("Disc", 10));
            if (!tag.contains("Disc")) {
                // a Bubie from before the disc slot: its first disc in storage moves there
                for (int i = 0; i < storage.getContainerSize() && disc.isEmpty(); i++) {
                    if (isDisc(storage.getItem(i))) disc.setItem(0, storage.removeItem(i, 1));
                }
            }
        } finally {
            loading = false;
        }
        if (tag.contains("Tool")) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.parseOptional(registryAccess(), tag.getCompound("Tool")));
        }
        if (tag.contains("Build")) {
            CompoundTag build = tag.getCompound("Build");
            buildPlan = ItemStack.parseOptional(registryAccess(), build.getCompound("Blueprint"));
            buildOrigin = BlockPos.of(build.getLong("Origin"));
            buildDoor = net.minecraft.core.Direction.from3DDataValue(build.getByte("Door"));
            entityData.set(DATA_BUILD, build.getInt("Progress"));
        } else {
            buildPlan = ItemStack.EMPTY;
            buildOrigin = null;
        }
        syncBuildPlan();
        dj.load(tag);
        setDjTrack(Dj.Track.NONE);
        onChipsChanged();
        runner.reset();
    }

    /** Keeps slot positions (SimpleContainer's own format packs items together, which would mix fuel and chips). */
    private net.minecraft.nbt.ListTag saveContainer(SimpleContainer container) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            list.add(stack.save(registryAccess(), entry));
        }
        return list;
    }

    private void loadContainer(SimpleContainer container, net.minecraft.nbt.ListTag list) {
        container.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < container.getContainerSize()) {
                container.setItem(slot, ItemStack.parseOptional(registryAccess(), entry));
            }
        }
    }

    // ---------------------------------------------------------------- helpers for jobs

    public Vec3 workEyes() {
        return new Vec3(getX(), getY() + 0.6, getZ());
    }

    /** Within arm's reach of a block (robot arms reach a bit further than they look). */
    public boolean canReach(BlockPos pos) {
        return workEyes().distanceToSqr(Vec3.atCenterOf(pos)) <= 4.5 * 4.5;
    }

    public boolean isUnderFeet(BlockPos pos) {
        BlockPos feet = blockPosition();
        return pos.equals(feet.below()) || pos.equals(feet) || pos.equals(feet.above())
                || pos.equals(BlockPos.containing(getX(), getBoundingBox().minY - 0.2, getZ()));
    }

    public void lookAtBlock(BlockPos pos) {
        getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** Moves to a free spot next to a block (used when stuck), with a little puff. */
    public boolean teleportNear(BlockPos target) {
        for (int r = 1; r <= 2; r++) {
            for (int dy = 0; dy >= -1; dy--) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos pos = target.offset(dx, dy, dz);
                        BlockState below = level().getBlockState(pos.below());
                        if (!below.isFaceSturdy(level(), pos.below(), net.minecraft.core.Direction.UP)) continue;
                        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
                        if (!level().noCollision(this, getBoundingBox().move(x - getX(), y - getY(), z - getZ()))) continue;
                        if (level() instanceof ServerLevel server) {
                            server.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 0.5, getZ(), 12, 0.2, 0.3, 0.2, 0.2);
                        }
                        getNavigation().stop();
                        teleportTo(x, y, z);
                        playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.4F, 1.6F);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public BlockState stateAt(BlockPos pos) {
        return level().getBlockState(pos);
    }
}
