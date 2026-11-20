package com.bubie.block;

import com.bubie.Bubie;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** Optional chunk loading for stations (config stationLoadsChunks): the 3×3 chunks around each one. */
public final class StationChunks {
    public static final TicketController CONTROLLER = new TicketController(Bubie.id("station"), (level, helper) -> {
        for (BlockPos pos : helper.getBlockTickets().keySet()) {
            if (!level.getBlockState(pos).is(Bubie.STATION.get())) helper.removeAllTickets(pos);
        }
    });

    private StationChunks() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static void set(ServerLevel level, BlockPos station, boolean load) {
        int cx = station.getX() >> 4, cz = station.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                CONTROLLER.forceChunk(level, station, cx + dx, cz + dz, load, true);
            }
        }
    }
}
