package com.bubie.block;

import com.bubie.Bubie;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.world.ForgeChunkManager;

/** Optional chunk loading for stations (config stationLoadsChunks): the 3×3 chunks around each one. */
public final class StationChunks {
    private StationChunks() {
    }

    /** When a world loads: tickets whose station is gone are dropped. */
    public static void register() {
        ForgeChunkManager.setForcedChunkLoadingCallback(Bubie.MOD_ID, (level, helper) -> {
            for (BlockPos pos : helper.getBlockTickets().keySet()) {
                if (!level.getBlockState(pos).is(Bubie.STATION.get())) helper.removeAllTickets(pos);
            }
        });
    }

    public static void set(ServerLevel level, BlockPos station, boolean load) {
        int cx = station.getX() >> 4, cz = station.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ForgeChunkManager.forceChunk(level, Bubie.MOD_ID, station, cx + dx, cz + dz, load, true);
            }
        }
    }
}
