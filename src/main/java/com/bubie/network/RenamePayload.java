package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The name typed over the title of a Bubie's menu (empty: back to plain "Bubie"). */
public record RenamePayload(int entityId, String name) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(name, 256);
    }

    public static RenamePayload read(FriendlyByteBuf buf) {
        return new RenamePayload(buf.readVarInt(), buf.readUtf(256));
    }

    public void send() {
        Bubie.CHANNEL.sendToServer(this);
    }

    public static void handle(RenamePayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null || !(player.level().getEntity(payload.entityId()) instanceof BubieEntity bubie)) return;
            if (!bubie.canCommand(player) || bubie.distanceToSqr(player) > 12 * 12) return;
            bubie.rename(payload.name());
        });
    }
}
