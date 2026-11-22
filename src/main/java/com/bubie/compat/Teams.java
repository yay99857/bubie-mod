package com.bubie.compat;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/** FTB Teams, when present: players in the owner's team may give a Bubie orders. Reflection, so it stays optional. */
public final class Teams {
    private static boolean ready, broken;
    private static Method api, isLoaded, getManager, sameTeam;

    private Teams() {
    }

    public static boolean sameTeam(UUID a, UUID b) {
        if (broken || a == null || b == null || !ModList.get().isLoaded("ftbteams")) return false;
        try {
            if (!ready) {
                Class<?> cls = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                api = cls.getMethod("api");
                Class<?> apiCls = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
                isLoaded = apiCls.getMethod("isManagerLoaded");
                getManager = apiCls.getMethod("getManager");
                sameTeam = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager").getMethod("arePlayersInSameTeam", UUID.class, UUID.class);
                ready = true;
            }
            Object instance = api.invoke(null);
            if (!(Boolean) isLoaded.invoke(instance)) return false;
            return (Boolean) sameTeam.invoke(getManager.invoke(instance), a, b);
        } catch (Throwable t) {
            broken = true;
            return false;
        }
    }
}
