package com.bubie.entity;

import com.bubie.BubieConfig;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Bubie as a DJ: plays the music discs it carries (any mod's discs) and the audio links it was given, one after
 * the other, for everyone nearby. The server keeps the playlist and the clock; clients hear it through
 * {@code client.DjClient}, which reads the synced {@link Track}.
 */
public final class Dj {
    public static final int MAX_LINKS = 16, MAX_LINK_LENGTH = 512;
    /** A link has no known length: a client reports its end; this is the fallback. */
    private static final int LINK_FALLBACK_TICKS = 20 * 60 * 15;

    /** What is playing, as synced to clients: kind "disc" (ref = sound event id) or "link" (ref = URL). */
    public record Track(int serial, String kind, String ref, String title) {
        public static final Track NONE = new Track(0, "", "", "");

        public boolean isNone() {
            return kind.isEmpty();
        }

        public boolean isLink() {
            return kind.equals("link");
        }

        public String encode() {
            return isNone() ? "" : serial + "\n" + kind + "\n" + ref + "\n" + title;
        }

        public static Track decode(String s) {
            if (s == null || s.isEmpty()) return NONE;
            String[] p = s.split("\n", 4);
            if (p.length < 4) return NONE;
            try {
                return new Track(Integer.parseInt(p[0]), p[1], p[2], p[3]);
            } catch (NumberFormatException e) {
                return NONE;
            }
        }
    }

    /** One entry of the playlist: a disc slot of the Bubie's storage or a link. */
    private record Entry(@Nullable Holder<JukeboxSong> song, @Nullable String link) {
    }

    private final BubieEntity bubie;
    private final List<String> links = new ArrayList<>();
    private boolean playing;
    private int index, serial;
    private long endsAt;

    Dj(BubieEntity bubie) {
        this.bubie = bubie;
    }

    public List<String> links() {
        return links;
    }

    public boolean isPlaying() {
        return playing;
    }

    private List<Entry> playlist() {
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) {
            ItemStack stack = bubie.storage().getItem(i);
            if (stack.isEmpty()) continue;
            Optional<Holder<JukeboxSong>> song = JukeboxSong.fromStack(bubie.registryAccess(), stack);
            song.ifPresent(s -> list.add(new Entry(s, null)));
        }
        if (BubieConfig.DJ_LINKS.get()) for (String link : links) list.add(new Entry(null, link));
        return list;
    }

    // ------------------------------------------------------------------ commands

    public void toggle() {
        if (playing) stop();
        else start(index);
    }

    public void stop() {
        playing = false;
        bubie.setDjTrack(Track.NONE);
    }

    public void next() {
        if (playing) start(index + 1);
        else index++;
    }

    public void previous() {
        if (playing) start(index - 1);
        else index--;
    }

    /** A client heard the end of this link (or could not load it). */
    public void trackDone(int doneSerial) {
        if (playing && doneSerial == serial) start(index + 1);
    }

    /** Adds a link; null if it was accepted, else why not. */
    @Nullable
    public Component addLink(String url) {
        url = url.trim();
        if (!BubieConfig.DJ_LINKS.get()) return Component.translatable("bubie.dj.links_off");
        if (links.size() >= MAX_LINKS) return Component.translatable("bubie.dj.too_many", MAX_LINKS);
        if (!isValidLink(url)) return Component.translatable("bubie.dj.bad_link");
        links.add(url);
        bubie.syncDjLinks();
        return null;
    }

    public void removeLink(int i) {
        if (i >= 0 && i < links.size()) {
            links.remove(i);
            bubie.syncDjLinks();
        }
    }

    public static boolean isValidLink(String url) {
        if (url.isEmpty() || url.length() > MAX_LINK_LENGTH || url.indexOf('\n') >= 0) return false;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The file name of a link, without the extension: "Song%20Name.mp3" → "Song Name". */
    public static String linkTitle(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        path = path.substring(path.lastIndexOf('/') + 1);
        int dot = path.lastIndexOf('.');
        if (dot > 0) path = path.substring(0, dot);
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // keep it encoded
        }
        path = path.replace('_', ' ').trim();
        return path.isEmpty() ? URI.create(url).getHost() : path;
    }

    // ------------------------------------------------------------------ playing

    private void start(int at) {
        List<Entry> list = playlist();
        if (list.isEmpty()) {
            stop();
            return;
        }
        index = Math.floorMod(at, list.size());
        Entry entry = list.get(index);
        serial++;
        playing = true;
        long now = bubie.level().getGameTime();
        Track track;
        if (entry.song() != null) {
            JukeboxSong song = entry.song().value();
            endsAt = now + Math.max(20, song.lengthInTicks()) + 20; // a breath between songs
            track = new Track(serial, "disc", song.soundEvent().value().getLocation().toString(),
                    song.description().getString());
        } else {
            endsAt = now + LINK_FALLBACK_TICKS;
            track = new Track(serial, "link", entry.link(), linkTitle(entry.link()));
        }
        bubie.setDjTrack(track);
    }

    void tick() {
        if (!playing) return;
        if (bubie.level().getGameTime() >= endsAt) start(index + 1);
    }

    // ------------------------------------------------------------------ saving

    void save(CompoundTag tag) {
        CompoundTag dj = new CompoundTag();
        ListTag list = new ListTag();
        for (String link : links) list.add(StringTag.valueOf(link));
        dj.put("Links", list);
        dj.putInt("Index", index);
        dj.putByte("Volume", (byte) bubie.getDjVolume());
        tag.put("Dj", dj);
    }

    void load(CompoundTag tag) {
        links.clear();
        playing = false;
        if (!tag.contains("Dj")) return;
        CompoundTag dj = tag.getCompound("Dj");
        ListTag list = dj.getList("Links", 8);
        for (int i = 0; i < list.size() && links.size() < MAX_LINKS; i++) {
            String link = list.getString(i);
            if (isValidLink(link)) links.add(link);
        }
        bubie.syncDjLinks();
        index = dj.getInt("Index");
        if (dj.contains("Volume")) bubie.setDjVolume(dj.getByte("Volume"));
    }
}
