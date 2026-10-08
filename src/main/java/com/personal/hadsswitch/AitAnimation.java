package com.personal.hadsswitch;

import java.lang.reflect.InvocationTargetException;
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

    private static boolean accepted(Object result) {
        // AiT returns Optional<ActionQueue>: present = it took the order, empty = it refused
        return !(result instanceof java.util.Optional<?> o) || o.isPresent();
    }

    /** Asks AiT to land. Returns false if AiT refused (cooldown, vetoed by an event, not in flight...). */
    public static boolean rematerialize(Object travel) throws Exception {
        return accepted(invoke(travel, "rematerialize"));
    }

    /**
     * AiT's own "dematerialise with a trip-specific arrival animation": AiT remembers the normal arrival animation,
     * switches to ours when the TARDIS starts to land, and puts the normal one back once it has landed.
     * Falls back to a plain dematerialise (NoSuchMethodException) if this AiT doesn't have that call.
     * Returns false if AiT refused to dematerialise.
     */
    public static boolean dematerialize(Object travel, Object matId) throws Exception {
        Object matAnim = null;
        if (matId != null && isRegistered(matId)) {
            Class<?> reg = Class.forName("dev.amble.ait.core.tardis.animation.v2.datapack.TardisAnimationRegistry");
            Object instance = reg.getMethod("getInstance").invoke(null);
            matAnim = invoke(instance, "instantiate", matId);
        }
        return accepted(invoke(travel, "dematerialize", null, matAnim));
    }

    /** The id of the animation that is really playing right now, null if none, "?" if we can't tell. */
    public static String runningAnimationId(Object travel) {
        try {
            Object holder = invoke(travel, "getAnimations");
            Method m = holder.getClass().getDeclaredMethod("getCurrent"); // protected in AiT
            m.setAccessible(true);
            Object cur = m.invoke(holder);
            return cur == null ? null : String.valueOf(invoke(cur, "id"));
        } catch (Exception e) {
            return "?";
        }
    }

    /** Replaces the animation that is playing right now with this one (AiT's own override; syncs to players). */
    public static boolean forceAnimation(Object travel, Object resourceLocation) throws Exception {
        return Boolean.TRUE.equals(invoke(travel, "setTemporaryAnimation", resourceLocation));
    }

    /**
     * True if AiT has loaded this animation: both its type file (data/.../fx/animation/type) AND the keyframes it
     * points at (an animation of the same name inside a data/.../fx/animation/keyframes/*.animation.json file).
     * AiT registers a type even when its keyframes are missing and then quietly plays a random other animation,
     * so the second check is the one that matters. Returns true if we simply can't tell.
     */
    public static boolean isRegistered(Object resourceLocation) {
        try {
            Class<?> reg = Class.forName("dev.amble.ait.core.tardis.animation.v2.datapack.TardisAnimationRegistry");
            Object instance = reg.getMethod("getInstance").invoke(null);
            Object result = invoke(instance, "getOptional", resourceLocation);
            if (!(result instanceof java.util.Optional<?> o && o.isPresent())) return false;
        } catch (Exception e) {
            // can't tell from the registry, fall through to the keyframes check
        }
        try {
            Class<?> parser = Class.forName("dev.amble.ait.core.tardis.animation.v2.blockbench.BlockbenchParser");
            for (Method m : parser.getMethods()) {
                if (m.getName().equals("getOrThrow") && m.getParameterCount() == 1
                        && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    m.invoke(null, resourceLocation); // throws if AiT has no keyframes under this name
                    return true;
                }
            }
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException) return false; // "No blockbench animation found"
        } catch (Exception ignored) {
            // can't tell
        }
        return true;
    }
}
