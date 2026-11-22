package com.bubie.client.dj;

import com.bubie.Bubie;
import com.bubie.BubieClientConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Dj;
import com.bubie.network.BubieCommandPayload;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Plays what every nearby Bubie DJ is playing: starts a {@link DjSound} when a Bubie's track changes, stops it when
 * the Bubie stops or leaves, and tells the server when a link ended (the server knows how long discs last).
 */
@Mod.EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class DjClient {
    private static final double HEAR = 48;
    /** Links shorter than this are reported as broken, not as finished, a moment later (no double skips). */
    private static final int MIN_LINK_TICKS = 40;

    private static final class Playing {
        final DjSound sound;
        int ticks;
        boolean reported;

        Playing(DjSound sound) {
            this.sound = sound;
        }
    }

    private static final Map<Integer, Playing> PLAYING = new HashMap<>();

    private DjClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYING.values().forEach(p -> mc.getSoundManager().stop(p.sound));
            PLAYING.clear();
            return;
        }
        // forget sounds whose Bubie left, stopped or changed track
        Iterator<Map.Entry<Integer, Playing>> it = PLAYING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Playing> e = it.next();
            Playing p = e.getValue();
            if (!(mc.level.getEntity(e.getKey()) instanceof BubieEntity bubie) || bubie.isRemoved()
                    || !bubie.getDjTrack().equals(p.sound.track()) || bubie.distanceToSqr(mc.player) > HEAR * HEAR * 1.5) {
                mc.getSoundManager().stop(p.sound);
                it.remove();
                continue;
            }
            p.ticks++;
            if (!p.reported && p.sound.track().isLink() && p.ticks > 20
                    && (p.sound.linkEnded() || !mc.getSoundManager().isActive(p.sound))) {
                p.reported = true;
                if (p.ticks >= MIN_LINK_TICKS || p.sound.linkEnded()) {
                    new BubieCommandPayload(bubie.getId(), BubieCommandPayload.DJ_TRACK_DONE,
                            p.sound.track().serial()).send();
                }
            }
        }
        // start the ones we don't hear yet
        for (BubieEntity bubie : mc.level.getEntitiesOfClass(BubieEntity.class, mc.player.getBoundingBox().inflate(HEAR))) {
            Dj.Track track = bubie.getDjTrack();
            if (track.isNone() || PLAYING.containsKey(bubie.getId())) continue;
            if (track.isLink() && !BubieClientConfig.DJ_PLAY_LINKS.get()) continue;
            DjSound sound = new DjSound(bubie, track);
            PLAYING.put(bubie.getId(), new Playing(sound));
            mc.getSoundManager().play(sound);
        }
    }
}
