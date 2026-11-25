package com.bubie;

import com.bubie.block.StationBlock;
import com.bubie.block.StationBlockEntity;
import com.bubie.block.StationChunks;
import com.bubie.compat.BlueprintCompat;
import com.bubie.compat.BlueprintHelper;
import com.bubie.entity.BubieEntity;
import com.bubie.item.BubieItem;
import com.bubie.item.ChipItem;
import com.bubie.item.ChipType;
import com.bubie.item.ClipboardItem;
import com.bubie.item.DriveItem;
import com.bubie.item.WirelessStationItem;
import com.bubie.menu.BubieMenu;
import com.bubie.menu.StationMenu;
import com.bubie.network.BubieCommandPayload;
import com.bubie.network.RenamePayload;
import com.bubie.network.StationBubiesPayload;
import com.bubie.network.TrackerRequestPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Mod(Bubie.MOD_ID)
public class Bubie {
    public static final String MOD_ID = "bubie";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BubieEntity>> BUBIE_ENTITY = ENTITIES.register("bubie",
            () -> EntityType.Builder.of(BubieEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.98F)
                    .eyeHeight(0.72F)
                    .clientTrackingRange(10)
                    .build(MOD_ID + ":bubie"));

    public static final DeferredBlock<StationBlock> STATION = BLOCKS.register("bubie_station",
            () -> new StationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(2.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 4)));

    public static final DeferredItem<BubieItem> BUBIE_ITEM = ITEMS.register("bubie",
            () -> new BubieItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ClipboardItem> CLIPBOARD = ITEMS.register("bubie_clipboard",
            () -> new ClipboardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> STATION_ITEM = ITEMS.registerSimpleBlockItem(STATION);
    /** A Bubie's storage: what it carries lives on the drive (every Bubie comes with a Hard Drive). */
    public static final DeferredItem<DriveItem> HARD_DRIVE = ITEMS.register("hard_drive",
            () -> new DriveItem(18, new Item.Properties()));
    public static final DeferredItem<DriveItem> SOLID_STATE_DRIVE = ITEMS.register("solid_state_drive",
            () -> new DriveItem(36, new Item.Properties()));
    public static final DeferredItem<WirelessStationItem> WIRELESS_STATION = ITEMS.register("wireless_station",
            () -> new WirelessStationItem(new Item.Properties().stacksTo(1)));
    public static final Map<ChipType, DeferredItem<ChipItem>> CHIPS = new EnumMap<>(ChipType.class);

    static {
        for (ChipType type : ChipType.values()) {
            CHIPS.put(type, ITEMS.register(type.id(), () -> new ChipItem(type, new Item.Properties().stacksTo(16))));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<StationBlockEntity>> STATION_BE = BLOCK_ENTITIES.register("bubie_station",
            () -> BlockEntityType.Builder.of(StationBlockEntity::new, STATION.get()).build(null));

    public static final Supplier<MenuType<BubieMenu>> BUBIE_MENU = MENUS.register("bubie",
            () -> IMenuTypeExtension.create(BubieMenu::fromNetwork));
    public static final Supplier<MenuType<StationMenu>> STATION_MENU = MENUS.register("bubie_station",
            () -> IMenuTypeExtension.create(StationMenu::fromNetwork));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("bubie", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bubie"))
            .icon(() -> new ItemStack(BUBIE_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(BUBIE_ITEM.get());
                out.accept(CLIPBOARD.get());
                out.accept(STATION_ITEM.get());
                out.accept(WIRELESS_STATION.get());
                out.accept(HARD_DRIVE.get());
                out.accept(SOLID_STATE_DRIVE.get());
                // the Storage Chip is only an ingredient now (of the SSD): not offered on its own
                for (ChipType type : ChipType.values()) if (type != ChipType.STORAGE) out.accept(CHIPS.get(type).get());
            })
            .build());

    public Bubie(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, BubieConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, BubieClientConfig.SPEC);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);

        modBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(BUBIE_ENTITY.get(), BubieEntity.createAttributes().build()));
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STATION_BE.get(), (be, side) -> be.storageHandler());
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STATION_BE.get(), (be, side) -> be.energy());
        });
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToServer(BubieCommandPayload.TYPE, BubieCommandPayload.STREAM_CODEC, BubieCommandPayload::handle)
                .playToServer(RenamePayload.TYPE, RenamePayload.STREAM_CODEC, RenamePayload::handle)
                .playToServer(TrackerRequestPayload.TYPE, TrackerRequestPayload.STREAM_CODEC, TrackerRequestPayload::handle)
                .playToClient(StationBubiesPayload.TYPE, StationBubiesPayload.STREAM_CODEC, StationBubiesPayload::handle));
        modBus.addListener(StationChunks::register);
        // Base Blueprint 1.3.0+: a blueprint asks for a helper ("Built by: Bubie" in its menu)
        if (BlueprintCompat.loaded() && classExists("com.baseblueprint.api.HelperBuildEvent")) {
            BlueprintHelper.register();
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Bubie.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
