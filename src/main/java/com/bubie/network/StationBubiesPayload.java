package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.data.BubieRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: the Bubies that live at a Station, for its tracker. Sent while the Station's menu is open, and
 * as the answer to a Wireless Station ({@link TrackerRequestPayload}).
 */
public record StationBubiesPayload(List<Entry> bubies) {
    private static final int MAX_NAME = 64;

    /**
     * One Bubie: its name, job, status and face (ordinals), energy in percent, packed look, where it was last seen
     * and whether that is in another dimension than the player's.
     */
    public record Entry(String name, int job, int status, int face, int energy, int look, long pos, boolean elsewhere) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name, MAX_NAME);
            buf.writeVarInt(job + 16); // modes are sent as negative "jobs"
            buf.writeVarInt(status);
            buf.writeVarInt(face);
            buf.writeVarInt(energy);
            buf.writeInt(look);
            buf.writeLong(pos);
            buf.writeBoolean(elsewhere);
        }

        static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readUtf(MAX_NAME), buf.readVarInt() - 16, buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readInt(), buf.readLong(), buf.readBoolean());
        }

        public BlockPos blockPos() {
            return BlockPos.of(pos);
        }
    }

    public static final int MAX = 32;

    public void write(FriendlyByteBuf buf) {
        int count = Math.min(MAX, bubies.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) bubies.get(i).write(buf);
    }

    public static StationBubiesPayload read(FriendlyByteBuf buf) {
        int count = Math.min(MAX, buf.readVarInt());
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(Entry.read(buf));
        return new StationBubiesPayload(List.copyOf(list));
    }

    public void sendTo(ServerPlayer player) {
        Bubie.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), this);
    }

    /** What the open tracker shows; replaced by every update. */
    public static volatile List<Entry> latest = List.of();

    /** The Bubies living at a Station as the world's registry last saw them, for a player in the `viewer` dimension. */
    public static StationBubiesPayload of(MinecraftServer server, ResourceKey<Level> dimension, BlockPos station,
                                          ResourceKey<Level> viewer) {
        List<Entry> list = new ArrayList<>();
        String here = viewer.location().toString();
        for (BubieRegistry.Sighting s : BubieRegistry.get(server).livingAt(dimension.location().toString(), station)) {
            if (list.size() >= MAX) break;
            String name = s.name().length() > MAX_NAME ? s.name().substring(0, MAX_NAME) : s.name();
            list.add(new Entry(name, s.doing(), s.status(), s.face(), s.energy(), s.look(), s.pos(), !s.dimension().equals(here)));
        }
        return new StationBubiesPayload(list);
    }

    public static void handle(StationBubiesPayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> latest = payload.bubies());
    }
}
