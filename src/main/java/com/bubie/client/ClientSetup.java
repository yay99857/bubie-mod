package com.bubie.client;

import com.bubie.Bubie;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    /**
     * Hold to see your Bubies through walls (an amber outline), up to 128 blocks away. Alt+B: B alone is often a
     * minimap's key, and Ctrl+B is the game's narrator.
     */
    public static final KeyMapping HIGHLIGHT = new KeyMapping("key.bubie.highlight",
            KeyConflictContext.IN_GAME, KeyModifier.ALT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.bubie");

    private ClientSetup() {
    }

    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent event) {
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
    static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> LookTextures.clear());
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(Bubie.BUBIE_MENU.get(), BubieScreen::new);
        event.register(Bubie.STATION_MENU.get(), StationScreen::new);
    }
}
