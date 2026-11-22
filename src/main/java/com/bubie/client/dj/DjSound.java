package com.bubie.client.dj;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Dj;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * One track of a Bubie DJ, heard in the world like a jukebox: it follows the Bubie as it walks, and the Bubie's volume
 * sets both how loud it is and how far it carries (up to 32 blocks). Discs play their own sound; links stream through
 * {@link UrlAudioStream} in place of the silent placeholder bubie:dj_stream.
 */
public final class DjSound extends AbstractTickableSoundInstance {
    static final ResourceLocation STREAM = Bubie.id("dj_stream");

    private final BubieEntity bubie;
    private final Dj.Track track;
    @Nullable
    private UrlAudioStream stream;
    private boolean failed;

    DjSound(BubieEntity bubie, Dj.Track track) {
        super(SoundEvent.createVariableRangeEvent(track.isLink() ? STREAM : new ResourceLocation(track.ref())),
                SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.bubie = bubie;
        this.track = track;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.looping = false;
        follow();
    }

    Dj.Track track() {
        return track;
    }

    /** A link that ended (or never loaded), so the server may move on. */
    boolean linkEnded() {
        return failed || stream != null && stream.drained();
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

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        if (!track.isLink()) return super.getStream(buffers, sound, looping);
        return UrlAudioStream.open(track.ref()).handle((s, error) -> {
            if (error != null) {
                failed = true;
                throw new java.util.concurrent.CompletionException(error);
            }
            stream = (UrlAudioStream) s;
            return s;
        });
    }
}
