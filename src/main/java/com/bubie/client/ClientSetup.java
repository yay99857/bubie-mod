package com.bubie.client;

import com.bubie.Bubie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
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
    static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> LookTextures.clear());
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(Bubie.BUBIE_MENU.get(), BubieScreen::new);
        event.register(Bubie.STATION_MENU.get(), StationScreen::new);
    }
}
