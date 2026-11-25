package com.bubie;

import com.bubie.block.StationBlock;
import com.bubie.block.StationBlockEntity;
import com.bubie.block.StationChunks;
import com.bubie.entity.BubieEntity;
import com.bubie.item.BubieItem;
import com.bubie.item.ChipItem;
import com.bubie.item.ChipType;
import com.bubie.item.ClipboardItem;
import com.bubie.menu.BubieMenu;
import com.bubie.menu.StationMenu;
import com.bubie.network.BubieCommandPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BubieEntity>> BUBIE_ENTITY = ENTITIES.registerEntityType("bubie",
            BubieEntity::new, MobCategory.MISC, b -> b
                    .sized(0.6F, 0.98F)
                    .eyeHeight(0.72F)
                    .clientTrackingRange(10));

    public static final DeferredBlock<StationBlock> STATION = BLOCKS.registerBlock("bubie_station",
            StationBlock::new, p -> p
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(2.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 4));

    public static final DeferredItem<BubieItem> BUBIE_ITEM = ITEMS.registerItem("bubie", BubieItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ClipboardItem> CLIPBOARD = ITEMS.registerItem("bubie_clipboard", ClipboardItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<BlockItem> STATION_ITEM = ITEMS.registerSimpleBlockItem(STATION);
    public static final DeferredItem<com.bubie.item.WirelessStationItem> WIRELESS_STATION = ITEMS.registerItem("wireless_station", com.bubie.item.WirelessStationItem::new, p -> p.stacksTo(1));
    public static final Map<ChipType, DeferredItem<ChipItem>> CHIPS = new EnumMap<>(ChipType.class);

    static {
        for (ChipType type : ChipType.values()) {
            CHIPS.put(type, ITEMS.registerItem(type.id(), p -> new ChipItem(type, p), p -> p.stacksTo(16)));
        }
    }

    public static final Supplier<BlockEntityType<StationBlockEntity>> STATION_BE = BLOCK_ENTITIES.register("bubie_station",
            () -> new BlockEntityType<>(StationBlockEntity::new, STATION.get()));

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
                for (ChipType type : ChipType.values()) out.accept(CHIPS.get(type).get());
            })
            .build());

    public Bubie(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SYNCED, BubieConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, BubieClientConfig.SPEC);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        com.bubie.gametest.BubieTests.register(modBus);

        modBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(BUBIE_ENTITY.get(), BubieEntity.createAttributes().build()));
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(Capabilities.Item.BLOCK, STATION_BE.get(), (be, side) -> be.storageHandler());
            event.registerBlockEntity(Capabilities.Energy.BLOCK, STATION_BE.get(), (be, side) -> be.energy());
        });
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToServer(BubieCommandPayload.TYPE, BubieCommandPayload.STREAM_CODEC, BubieCommandPayload::handle)
                .playToServer(com.bubie.network.RenamePayload.TYPE, com.bubie.network.RenamePayload.STREAM_CODEC,
                        com.bubie.network.RenamePayload::handle)
                .playToServer(com.bubie.network.TrackerRequestPayload.TYPE, com.bubie.network.TrackerRequestPayload.STREAM_CODEC,
                        com.bubie.network.TrackerRequestPayload::handle)
                .playToClient(com.bubie.network.StationBubiesPayload.TYPE, com.bubie.network.StationBubiesPayload.STREAM_CODEC,
                        com.bubie.network.StationBubiesPayload::handle));
        modBus.addListener(StationChunks::register);
        // Base Blueprint 1.3.0+: a blueprint asks for a helper ("Built by: Bubie" in its menu)
        if (com.bubie.compat.BlueprintCompat.loaded() && classExists("com.baseblueprint.api.HelperBuildEvent")) {
            com.bubie.compat.BlueprintHelper.register();
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

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
