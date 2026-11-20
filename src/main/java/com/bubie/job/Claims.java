package com.bubie.job;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Targets the Bubies have picked, so two of them don't walk to the same block (or the same tree, for lumberjacks).
 * A claim lasts a few seconds and is renewed while its Bubie works on it, so a Bubie that dies or unloads frees its
 * target on its own.
 */
public final class Claims {
    private static final int TTL = 200;
    /** How far up and down a lumberjack's claim reaches: the whole trunk. */
    private static final int TREE_HEIGHT = 24;

    private record Claim(int owner, long until) {
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Claim>> BY_LEVEL = new HashMap<>();

    private Claims() {
    }

    private static Map<BlockPos, Claim> of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level.dimension(), k -> new HashMap<>());
    }

    public static void claim(ServerLevel level, BlockPos pos, int owner) {
        of(level).put(pos.immutable(), new Claim(owner, level.getGameTime() + TTL));
    }

    public static void release(ServerLevel level, int owner) {
        of(level).values().removeIf(c -> c.owner == owner);
    }

    /**
     * Whether another Bubie claimed this spot. With a radius, a claim anywhere within it (sideways) counts, over the
     * height of a tree.
     */
    public static boolean takenByOther(ServerLevel level, BlockPos pos, int owner, int radius) {
        long now = level.getGameTime();
        Map<BlockPos, Claim> claims = of(level);
        if (radius <= 0) {
            Claim c = claims.get(pos);
            if (c == null) return false;
            if (c.until < now) {
                claims.remove(pos);
                return false;
            }
            return c.owner != owner;
        }
        Iterator<Map.Entry<BlockPos, Claim>> it = claims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Claim> e = it.next();
            Claim c = e.getValue();
            if (c.until < now) {
                it.remove();
                continue;
            }
            if (c.owner == owner) continue;
            BlockPos p = e.getKey();
            if (Math.abs(p.getX() - pos.getX()) <= radius && Math.abs(p.getZ() - pos.getZ()) <= radius
                    && Math.abs(p.getY() - pos.getY()) <= TREE_HEIGHT) return true;
        }
        return false;
    }

    /** For tests: forget everything. */
    public static void clear() {
        BY_LEVEL.clear();
    }
}
