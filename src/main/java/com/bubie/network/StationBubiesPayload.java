package com.bubie.network;

import com.bubie.Bubie;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to client, while a Station's menu is open: the Bubies that live there, for the list beside the slots. */
public record StationBubiesPayload(List<Entry> bubies) {
    /** One Bubie: its name, job, status and face (ordinals), energy in percent and packed look. */
    public record Entry(String name, int job, int status, int face, int energy, int look) {
        private static final int MAX_NAME = 64;

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name, MAX_NAME);
            buf.writeVarInt(job);
            buf.writeVarInt(status);
            buf.writeVarInt(face);
            buf.writeVarInt(energy);
            buf.writeInt(look);
        }

        static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readUtf(MAX_NAME), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readInt());
        }
    }

    public static final int MAX = 8;

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

    /** What the open Station screen shows; replaced by every update. */
    public static volatile List<Entry> latest = List.of();

    public static void handle(StationBubiesPayload payload, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().setPacketHandled(true);
        latest = payload.bubies();
    }
}
