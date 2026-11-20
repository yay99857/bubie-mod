package com.bubie.job;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Collector: picks up every item lying in the area (next to mob or crop farms) and stores it. */
public class CollectJob extends Job {
    public CollectJob(BubieEntity bubie, WorkArea area) {
        super(bubie, area);
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public BlockPos next(JobRunner runner) {
        return JobRunner.NOTHING; // the runner walks to loose items before asking for blocks
    }
}
