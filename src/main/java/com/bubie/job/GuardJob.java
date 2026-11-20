package com.bubie.job;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Guard: watches its area (or the ground around its station) and fights the monsters that walk in, with the weapon in
 * its tool slot. It picks up what they drop and keeps watch at night instead of resting. Creepers are left alone:
 * hitting one in melee only makes it blow up next to the base.
 */
public class GuardJob extends Job {
    /** How far past the area's edge a monster still counts (they come from outside). */
    private static final double MARGIN = 4.0;

    public GuardJob(BubieEntity bubie, WorkArea area) {
        super(bubie, area);
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public BlockPos next(JobRunner runner) {
        return JobRunner.NOTHING; // monsters are found by findEnemy, before any block work
    }

    @Override
    public boolean plugsFluids() {
        return false;
    }

    boolean isEnemy(LivingEntity e) {
        return e.isAlive() && e instanceof Enemy && !(e instanceof Creeper) && !e.isInvisible()
                && area.box().inflate(MARGIN).contains(e.position());
    }

    /** The nearest monster in (or near) the area that the Bubie can see. */
    @Nullable
    LivingEntity findEnemy() {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : bubie.level().getEntitiesOfClass(LivingEntity.class, area.box().inflate(MARGIN), this::isEnemy)) {
            if (!bubie.getSensing().hasLineOfSight(e) && bubie.distanceToSqr(e) > 9) continue;
            double d = bubie.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
