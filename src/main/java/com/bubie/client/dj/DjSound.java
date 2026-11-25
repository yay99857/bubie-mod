package com.bubie.client.dj;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Dj;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The disc of a Bubie DJ, heard in the world like a jukebox: it follows the Bubie as it walks, and the Bubie's volume
 * sets both how loud it is and how far it carries (up to 32 blocks).
 */
public final class DjSound extends AbstractTickableSoundInstance {
    private final BubieEntity bubie;
    private final Dj.Track track;

    DjSound(BubieEntity bubie, Dj.Track track) {
        super(SoundEvent.createVariableRangeEvent(Identifier.parse(track.ref())), SoundSource.RECORDS,
                SoundInstance.createUnseededRandom());
        this.bubie = bubie;
        this.track = track;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.looping = false;
        follow();
    }

    Dj.Track track() {
        return track;
    }

    private void follow() {
        x = bubie.getX();
        y = bubie.getY() + 0.8;
        z = bubie.getZ();
        // 0-100 → gain 0-1 and a range of 16-32 blocks (linear attenuation reaches max(volume, 1) x 16)
        volume = Math.max(0.001F, bubie.getDjVolume() / 100F) * 2.0F;
    }

    @Override
    public void tick() {
        if (bubie.isRemoved() || !bubie.getDjTrack().equals(track)) {
            stop();
            return;
        }
        follow();
    }
}
