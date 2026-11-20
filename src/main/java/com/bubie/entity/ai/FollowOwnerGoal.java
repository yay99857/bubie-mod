package com.bubie.entity.ai;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** FOLLOW mode: keeps close to the owner and teleports to them when left far behind, like a pet. */
public class FollowOwnerGoal extends Goal {
    private static final double START = 3.5, STOP = 2.0, TELEPORT = 20.0;
    private final BubieEntity bubie;
    private LivingEntity owner;
    private int recalc;

    public FollowOwnerGoal(BubieEntity bubie) {
        this.bubie = bubie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (bubie.getMode() != Mode.FOLLOW) return false;
        LivingEntity o = bubie.getOwner();
        if (o == null || !o.isAlive() || o.isSpectator()) return false;
        if (bubie.distanceToSqr(o) < START * START) return false;
        owner = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return bubie.getMode() == Mode.FOLLOW && owner != null && owner.isAlive() && owner.level() == bubie.level()
                && bubie.distanceToSqr(owner) > STOP * STOP;
    }

    @Override
    public void start() {
        recalc = 0;
        bubie.setStatus(Status.FOLLOWING);
    }

    @Override
    public void stop() {
        owner = null;
        bubie.getNavigation().stop();
    }

    @Override
    public void tick() {
        bubie.getLookControl().setLookAt(owner, 10.0F, bubie.getMaxHeadXRot());
        if (--recalc > 0) return;
        recalc = 10;
        if (bubie.distanceToSqr(owner) > TELEPORT * TELEPORT) {
            if (owner.onGround()) bubie.teleportNear(owner.blockPosition());
        } else {
            bubie.getNavigation().moveTo(owner, 1.25);
        }
    }
}
