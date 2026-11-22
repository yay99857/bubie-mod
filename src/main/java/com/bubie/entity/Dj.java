package com.bubie.entity;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

import java.util.Optional;

/**
 * The Bubie as a DJ: plays the music disc in its DJ slot (any mod's disc) for everyone nearby, over and over while
 * it is switched on. The server keeps the clock; clients hear it through {@code client.DjClient}, which reads the
 * synced {@link Track}.
 */
public final class Dj {
    /** What is playing, as synced to clients: the disc's sound event id and its title. */
    public record Track(int serial, String ref, String title) {
        public static final Track NONE = new Track(0, "", "");

        public boolean isNone() {
            return ref.isEmpty();
        }

        public String encode() {
            return isNone() ? "" : serial + "\n" + ref + "\n" + title;
        }

        public static Track decode(String s) {
            if (s == null || s.isEmpty()) return NONE;
            String[] p = s.split("\n", 3);
            if (p.length < 3) return NONE;
            try {
                return new Track(Integer.parseInt(p[0]), p[1], p[2]);
            } catch (NumberFormatException e) {
                return NONE;
            }
        }
    }

    private final BubieEntity bubie;
    private boolean playing;
    private int serial;
    private long endsAt;

    Dj(BubieEntity bubie) {
        this.bubie = bubie;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void toggle() {
        if (playing) stop();
        else start();
    }

    public void stop() {
        playing = false;
        bubie.setDjTrack(Track.NONE);
    }

    /** Plays the disc in the slot from its beginning; with no disc there, stops. */
    private void start() {
        Optional<Holder<JukeboxSong>> disc = discSong();
        if (disc.isEmpty()) {
            stop();
            return;
        }
        JukeboxSong song = disc.get().value();
        serial++;
        playing = true;
        endsAt = bubie.level().getGameTime() + Math.max(20, song.lengthInTicks()) + 20; // a breath before it starts over
        bubie.setDjTrack(new Track(serial, song.soundEvent().value().getLocation().toString(), song.description().getString()));
    }

    void tick() {
        if (!playing) return;
        // a note now and then, in a colour that walks the scale
        if (bubie.tickCount % 12 == 0 && bubie.level() instanceof net.minecraft.server.level.ServerLevel level) {
            double colour = (bubie.tickCount / 12 % 24) / 24.0;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, bubie.getX(), bubie.getY() + 1.3, bubie.getZ(),
                    0, colour, 0, 0, 1);
        }
        // the disc it is playing was taken out (or swapped): play what is there now, or stop
        Track track = bubie.getDjTrack();
        if (bubie.tickCount % 5 == 0 && !track.isNone()
                && !discSong().map(s -> s.value().soundEvent().value().getLocation().toString().equals(track.ref())).orElse(false)) {
            start();
            return;
        }
        if (bubie.level().getGameTime() >= endsAt) start();
    }

    private Optional<Holder<JukeboxSong>> discSong() {
        ItemStack stack = bubie.disc().getItem(0);
        return stack.isEmpty() ? Optional.empty() : JukeboxSong.fromStack(bubie.registryAccess(), stack);
    }

    // ------------------------------------------------------------------ saving

    void save(CompoundTag tag) {
        CompoundTag dj = new CompoundTag();
        dj.putByte("Volume", (byte) bubie.getDjVolume());
        tag.put("Dj", dj);
    }

    void load(CompoundTag tag) {
        playing = false;
        if (!tag.contains("Dj")) return;
        CompoundTag dj = tag.getCompound("Dj");
        if (dj.contains("Volume")) bubie.setDjVolume(dj.getByte("Volume"));
    }
}
