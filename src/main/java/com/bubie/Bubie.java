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
import com.bubie.item.EmptyDriveIngredient;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Mod(Bubie.MOD_ID)
public class Bubie {
    public static final String MOD_ID = "bubie";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<EntityType<BubieEntity>> BUBIE_ENTITY = ENTITIES.register("bubie",
            () -> EntityType.Builder.of(BubieEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.98F)
                    .clientTrackingRange(10)
                    .build(MOD_ID + ":bubie"));

    public static final RegistryObject<StationBlock> STATION = BLOCKS.register("bubie_station",
            () -> new StationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(2.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 4)));

    public static final RegistryObject<BubieItem> BUBIE_ITEM = ITEMS.register("bubie",
            () -> new BubieItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<ClipboardItem> CLIPBOARD = ITEMS.register("bubie_clipboard",
            () -> new ClipboardItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockItem> STATION_ITEM = ITEMS.register("bubie_station",
            () -> new BlockItem(STATION.get(), new Item.Properties()));
    /** A Bubie's storage: what it carries lives on the drive (every Bubie comes with a Hard Drive). */
    public static final RegistryObject<DriveItem> HARD_DRIVE = ITEMS.register("hard_drive",
            () -> new DriveItem(18, new Item.Properties()));
    public static final RegistryObject<DriveItem> SOLID_STATE_DRIVE = ITEMS.register("solid_state_drive",
            () -> new DriveItem(36, new Item.Properties()));
    public static final RegistryObject<WirelessStationItem> WIRELESS_STATION = ITEMS.register("wireless_station",
            () -> new WirelessStationItem(new Item.Properties().stacksTo(1)));
    public static final Map<ChipType, RegistryObject<ChipItem>> CHIPS = new EnumMap<>(ChipType.class);

    static {
        for (ChipType type : ChipType.values()) {
            CHIPS.put(type, ITEMS.register(type.id(), () -> new ChipItem(type, new Item.Properties().stacksTo(16))));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<StationBlockEntity>> STATION_BE = BLOCK_ENTITIES.register("bubie_station",
            () -> BlockEntityType.Builder.of(StationBlockEntity::new, STATION.get()).build(null));

    public static final RegistryObject<MenuType<BubieMenu>> BUBIE_MENU = MENUS.register("bubie",
            () -> IForgeMenuType.create(BubieMenu::fromNetwork));
    public static final RegistryObject<MenuType<StationMenu>> STATION_MENU = MENUS.register("bubie_station",
            () -> IForgeMenuType.create(StationMenu::fromNetwork));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("bubie", () -> CreativeModeTab.builder()
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

    private static final String PROTOCOL = "1";
    /** Menu buttons to the server; the Bubies of an open Station to the client. */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(id("main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    public Bubie() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BubieConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BubieClientConfig.SPEC);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);

        modBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
                CraftingHelper.register(id("empty_drive"), EmptyDriveIngredient.SERIALIZER);
            }
        });
        modBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(BUBIE_ENTITY.get(), BubieEntity.createAttributes().build()));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(StationChunks::register));
        CHANNEL.registerMessage(0, BubieCommandPayload.class, BubieCommandPayload::write, BubieCommandPayload::read,
                BubieCommandPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, StationBubiesPayload.class, StationBubiesPayload::write, StationBubiesPayload::read,
                StationBubiesPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, RenamePayload.class, RenamePayload::write, RenamePayload::read,
                RenamePayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, TrackerRequestPayload.class, TrackerRequestPayload::write, TrackerRequestPayload::read,
                TrackerRequestPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
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
        return new ResourceLocation(MOD_ID, path);
    }
}
