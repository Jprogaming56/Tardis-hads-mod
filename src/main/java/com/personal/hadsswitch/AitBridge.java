package com.personal.hadsswitch;

import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

/**
 * Talks to Adventures in Time through reflection, so this mod compiles without AiT on the classpath.
 * Only AiT's own class and method names are used (taken from AiT 1.2.12).
 */
public final class AitBridge {
    private AitBridge() {}

    /** Flips HADS for the TARDIS whose interior is this level. Returns the new state, or null if not a TARDIS interior. */
    public static Boolean toggleHads(ServerLevel level) throws Exception {
        Class<?> worldCls = Class.forName("dev.amble.ait.core.world.TardisServerWorld");
        UUID id = null;
        boolean found = false;
        for (Method m : worldCls.getMethods()) {
            if (m.getName().equals("getTardisId") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(level.getClass())) {
                id = (UUID) m.invoke(null, level);
                found = true;
                break;
            }
        }
        if (!found) throw new NoSuchMethodException("TardisServerWorld.getTardisId(Level)");
        if (id == null) return null;

        Class<?> mgrCls = Class.forName("dev.amble.ait.core.tardis.manager.ServerTardisManager");
        Object mgr = mgrCls.getMethod("getInstance").invoke(null);
        Object tardis = null;
        for (Method m : mgrCls.getMethods()) {
            if (m.getName().equals("demandTardis") && m.getParameterCount() == 2
                    && m.getParameterTypes()[1] == UUID.class
                    && m.getParameterTypes()[0].isAssignableFrom(level.getServer().getClass())) {
                tardis = m.invoke(mgr, level.getServer(), id);
                break;
            }
        }
        if (tardis == null) return null;

        Class<?> idCls = Class.forName("dev.amble.ait.api.tardis.TardisComponent$Id");
        Object hadsId = idCls.getField("HADS").get(null);
        Object hads = null;
        for (Method m : tardis.getClass().getMethods()) {
            if (m.getName().equals("handler") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(idCls)) {
                hads = m.invoke(tardis, hadsId);
                break;
            }
        }
        if (hads == null) throw new IllegalStateException("TARDIS has no HADS handler");

        Object value = hads.getClass().getMethod("enabled").invoke(hads);
        Object current = value.getClass().getMethod("get").invoke(value);
        boolean next = !Boolean.TRUE.equals(current);
        boolean set = false;
        for (Method m : value.getClass().getMethods()) {
            if (m.getName().equals("set") && m.getParameterCount() == 1) {
                try {
                    m.invoke(value, Boolean.valueOf(next));
                    set = true;
                    break;
                } catch (IllegalArgumentException ignored) {
                    // wrong overload, try the next one
                }
            }
        }
        if (!set) throw new NoSuchMethodException("BoolValue.set");
        return next;
    }
}
