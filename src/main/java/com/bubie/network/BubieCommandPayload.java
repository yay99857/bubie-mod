package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.DigFilter;
import com.bubie.entity.JobType;
import com.bubie.entity.MineMode;
import com.bubie.entity.Mode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A button in the Bubie's menu, sent to the server. */
public record BubieCommandPayload(int entityId, int command, int value) {
    public static final int SET_MODE = 0, SET_JOB = 1, TOGGLE_FILTER = 2, SET_MINE_MODE = 3,
            CLEAR_AREA = 4, CLEAR_CHEST = 5, CLEAR_HOME = 6, SET_LOOK = 7,
            DJ_TOGGLE = 8, DJ_VOLUME = 11, SET_RADIUS = 14;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(command);
        buf.writeVarInt(value);
    }

    public static BubieCommandPayload read(FriendlyByteBuf buf) {
        return new BubieCommandPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    /** From the menu to the server. */
    public void send() {
        Bubie.CHANNEL.sendToServer(this);
    }

    public static void handle(BubieCommandPayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null || !(player.level().getEntity(payload.entityId()) instanceof BubieEntity bubie)) return;
            int v = payload.value();
            if (!bubie.canCommand(player) || bubie.distanceToSqr(player) > 12 * 12) return;
            switch (payload.command()) {
                case SET_MODE -> bubie.setMode(Mode.byId(v));
                case SET_JOB -> {
                    // without Base Blueprint the Builder is skipped, in whichever direction the player was going
                    JobType job = JobType.byId(v);
                    if (job == JobType.BUILDER && !com.bubie.compat.BlueprintCompat.loaded()) {
                        job = JobType.byId(v + (v > bubie.getJob().ordinal() ? 1 : -1));
                    }
                    bubie.setJob(job);
                }
                case TOGGLE_FILTER -> {
                    if (v >= 0 && v < DigFilter.values().length) bubie.setDigFilter(bubie.getDigFilter() ^ DigFilter.values()[v].bit());
                }
                case SET_MINE_MODE -> bubie.setMineMode(MineMode.byId(v));
                case CLEAR_AREA -> bubie.setArea(null);
                case CLEAR_CHEST -> bubie.setChest(null);
                case CLEAR_HOME -> bubie.setHome(null);
                case SET_LOOK -> {
                    bubie.setLook(com.bubie.entity.Look.unpack(v));
                    bubie.showMood(com.bubie.entity.Face.HAPPY, 60);
                    bubie.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.6F);
                }
                case SET_RADIUS -> bubie.setWorkRadius(v);
                case DJ_TOGGLE -> bubie.dj().toggle();
                case DJ_VOLUME -> bubie.setDjVolume(v);
                default -> {
                }
            }
        });
    }
}
