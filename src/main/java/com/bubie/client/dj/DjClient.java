package com.bubie.client.dj;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Dj;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Plays what every nearby Bubie DJ is playing: starts a {@link DjSound} when a Bubie's track changes and stops it
 * when the Bubie stops or leaves. The server knows how long each disc lasts.
 */
@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class DjClient {
    private static final double HEAR = 48;

    private static final Map<Integer, DjSound> PLAYING = new HashMap<>();

    private DjClient() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYING.values().forEach(sound -> mc.getSoundManager().stop(sound));
            PLAYING.clear();
            return;
        }
        // forget sounds whose Bubie left, stopped or changed track
        Iterator<Map.Entry<Integer, DjSound>> it = PLAYING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, DjSound> e = it.next();
            DjSound sound = e.getValue();
            if (!(mc.level.getEntity(e.getKey()) instanceof BubieEntity bubie) || bubie.isRemoved()
                    || !bubie.getDjTrack().equals(sound.track()) || bubie.distanceToSqr(mc.player) > HEAR * HEAR * 1.5) {
                mc.getSoundManager().stop(sound);
                it.remove();
            }
        }
        // start the ones we don't hear yet
        for (BubieEntity bubie : mc.level.getEntitiesOfClass(BubieEntity.class, mc.player.getBoundingBox().inflate(HEAR))) {
            Dj.Track track = bubie.getDjTrack();
            if (track.isNone() || PLAYING.containsKey(bubie.getId())) continue;
            DjSound sound = new DjSound(bubie, track);
            PLAYING.put(bubie.getId(), sound);
            mc.getSoundManager().play(sound);
        }
    }
}
