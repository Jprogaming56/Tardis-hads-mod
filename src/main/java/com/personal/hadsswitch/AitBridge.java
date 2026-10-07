package com.personal.hadsswitch;

import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Talks to Adventures in Time through reflection, so this mod compiles without AiT on the classpath.
 * Only AiT's own class and method names are used (taken from AiT 1.2.12).
 */
public final class AitBridge {
    private AitBridge() {}

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        return c;
    }

    private static Method find(Class<?> cls, String name, Object[] args) throws NoSuchMethodException {
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] pt = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < pt.length; i++) {
                if (args[i] != null && !wrap(pt[i]).isInstance(args[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok) return m;
        }
        throw new NoSuchMethodException(cls.getName() + "." + name);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Method m = find(target.getClass(), name, args);
        try {
            m.setAccessible(true);
        } catch (Exception ignored) {
            // fine, public anyway
        }
        return m.invoke(target, args);
    }

    private static Object callStatic(Class<?> cls, String name, Object... args) throws Exception {
        Method m = find(cls, name, args);
        try {
            m.setAccessible(true);
        } catch (Exception ignored) {
            // fine, public anyway
        }
        return m.invoke(null, args);
    }

    /** The id of the TARDIS whose interior is this level, or null if it isn't a TARDIS interior. */
    public static UUID tardisIdOf(ServerLevel level) throws Exception {
        Class<?> worldCls = Class.forName("dev.amble.ait.core.world.TardisServerWorld");
        return (UUID) callStatic(worldCls, "getTardisId", level);
    }

    public static Object tardis(MinecraftServer server, UUID id) throws Exception {
        Class<?> mgrCls = Class.forName("dev.amble.ait.core.tardis.manager.ServerTardisManager");
        Object mgr = mgrCls.getMethod("getInstance").invoke(null);
        return call(mgr, "demandTardis", server, id);
    }

    /** Sets AiT's own built-in HADS flag (we keep it off and run our own logic instead). */
    public static void setBuiltInHads(Object tardis, boolean want) throws Exception {
        Class<?> idCls = Class.forName("dev.amble.ait.api.tardis.TardisComponent$Id");
        Object hadsId = idCls.getField("HADS").get(null);
        Object hads = call(tardis, "handler", hadsId);
        Object value = call(hads, "enabled");
        Object current = call(value, "get");
        if (Boolean.TRUE.equals(current) != want) {
            call(value, "set", Boolean.valueOf(want));
        }
    }

    public static Object travel(Object tardis) throws Exception {
        return call(tardis, "travel");
    }

    public static String stateName(Object travel) throws Exception {
        return ((Enum<?>) call(travel, "getState")).name();
    }

    public static Object position(Object travel) throws Exception {
        return call(travel, "position");
    }

    public static ServerLevel worldOf(Object pos) throws Exception {
        return (ServerLevel) call(pos, "getWorld");
    }

    public static BlockPos blockOf(Object pos) throws Exception {
        return (BlockPos) call(pos, "getPos");
    }

    public static Object destination(Object travel) throws Exception {
        return call(travel, "destination");
    }

    public static void setDestination(Object travel, Object pos) throws Exception {
        call(travel, "destination", pos);
    }

    public static void dematerialize(Object travel) throws Exception {
        call(travel, "dematerialize");
    }

    public static void rematerialize(Object travel) throws Exception {
        call(travel, "rematerialize");
    }

    public static Object door(Object tardis) throws Exception {
        return call(tardis, "door");
    }

    /** True if the TARDIS doors are open (AiT refuses to dematerialise while they are). */
    public static boolean isDoorOpen(Object door) throws Exception {
        return Boolean.TRUE.equals(call(door, "isOpen"));
    }

    /** True once the doors are fully shut (the closing animation has finished). */
    public static boolean isDoorClosed(Object door) throws Exception {
        try {
            return Boolean.TRUE.equals(call(door, "isClosed"));
        } catch (NoSuchMethodException e) {
            return !isDoorOpen(door);
        }
    }

    public static void closeDoors(Object door) throws Exception {
        call(door, "closeDoors");
    }

    public static void alarm(Object tardis, boolean on) throws Exception {
        Object alarm = call(tardis, "alarm");
        call(alarm, on ? "enable" : "disable");
    }

    /** True if this level is the inside of some TARDIS. */
    public static boolean isInterior(ServerLevel level) {
        try {
            return tardisIdOf(level) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Builds an AiT position (world + block + rotation). */
    public static Object makePos(ServerLevel level, BlockPos pos, byte rotation) throws Exception {
        Class<?> cls = Class.forName("dev.amble.ait.lib.data.CachedDirectedGlobalPos");
        return callStatic(cls, "create", level, pos, Byte.valueOf(rotation));
    }

    /** The facing of an existing AiT position, or 0 if it can't be read. */
    public static byte rotationOf(Object pos) {
        for (String name : new String[] {"getRotation", "rotation"}) {
            try {
                Object r = call(pos, name);
                if (r instanceof Number n) return n.byteValue();
            } catch (Exception ignored) {
                // try next name
            }
        }
        return 0;
    }
}
