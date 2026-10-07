package com.personal.hadsswitch;

import java.lang.reflect.Method;

/**
 * Reads and sets which arrival/departure animation a TARDIS uses, through reflection (same approach as
 * AitBridge, kept separate so AitBridge stays exactly as you have it).
 */
public final class AitAnimation {
    private AitAnimation() {}

    private static Object stateValue(String name) throws Exception {
        Class<?> cls = Class.forName("dev.amble.ait.core.tardis.handler.travel.TravelHandlerBase$State");
        for (Object c : cls.getEnumConstants()) {
            if (((Enum<?>) c).name().equals(name)) return c;
        }
        throw new IllegalArgumentException("No TARDIS state " + name);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] pt = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < pt.length; i++) {
                if (args[i] != null && !pt[i].isInstance(args[i])) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            try {
                m.setAccessible(true);
            } catch (Exception ignored) {
                // public anyway
            }
            return m.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    /** The id (a ResourceLocation) of the animation this TARDIS uses for the given state, e.g. "MAT". */
    public static Object animationIdFor(Object travel, String stateName) throws Exception {
        return invoke(travel, "getAnimationIdFor", stateValue(stateName));
    }

    public static void setAnimationFor(Object travel, String stateName, Object resourceLocation) throws Exception {
        invoke(travel, "setAnimationFor", stateValue(stateName), resourceLocation);
    }

    /** True if AiT has loaded an animation with this id (or if we simply can't tell). */
    public static boolean isRegistered(Object resourceLocation) {
        try {
            Class<?> reg = Class.forName("dev.amble.ait.core.tardis.animation.v2.datapack.TardisAnimationRegistry");
            Object instance = reg.getMethod("getInstance").invoke(null);
            Object result = invoke(instance, "getOptional", resourceLocation);
            return result instanceof java.util.Optional<?> o && o.isPresent();
        } catch (Exception e) {
            return true;
        }
    }
}
