package com.bubie.client;

import com.bubie.Bubie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    /** Bubie's own group in Controls. */
    public static final net.minecraft.client.KeyMapping.Category KEYS = new net.minecraft.client.KeyMapping.Category(Bubie.id("bubie"));

    /**
     * Hold to see your Bubies through walls (an amber outline), up to 128 blocks away. Alt+B: B alone is often a
     * minimap's key, and Ctrl+B is the game's narrator.
     */
    public static final net.minecraft.client.KeyMapping HIGHLIGHT = new net.minecraft.client.KeyMapping("key.bubie.highlight",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME, net.neoforged.neoforge.client.settings.KeyModifier.ALT,
            com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, com.mojang.blaze3d.platform.InputConstants.KEY_B, ClientSetup.KEYS);

    private ClientSetup() {
    }

    @SubscribeEvent
    static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.registerCategory(KEYS);
        event.register(HIGHLIGHT);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BubieModel.LAYER, BubieModel::createLayer);
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Bubie.BUBIE_ENTITY.get(), BubieRenderer::new);
    }

    @SubscribeEvent
    static void reload(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) {
        event.addListener(com.bubie.Bubie.id("look_textures"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> LookTextures.clear());
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(Bubie.BUBIE_MENU.get(), BubieScreen::new);
        event.register(Bubie.STATION_MENU.get(), StationScreen::new);
    }
}
