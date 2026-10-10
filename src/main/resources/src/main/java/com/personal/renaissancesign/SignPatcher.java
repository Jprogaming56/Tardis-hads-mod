package com.personal.renaissancesign;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Swaps the sign geometry on AiT's Renaissance police box models.
 *
 * AiT builds a fresh PoliceBoxModel every time an exterior takes on a variant, and the Renaissance variant shares
 * the stock police box model, so there is nothing to subclass. Instead this looks, a few times a second, at the
 * exterior renderers AiT has registered; whenever one holds a Renaissance variant together with a model that has
 * not been patched yet, the sign part inside that model is replaced with {@link SignGeometry}'s. AiT is only
 * touched through reflection, so this compiles without AiT on the classpath.
 */
public final class SignPatcher {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String RENAISSANCE_VARIANT =
            "dev.amble.ait.data.schema.exterior.variant.box.client.ClientPoliceBoxRenaissanceVariant";
    private static final String POLICE_BOX_MODEL = "dev.amble.ait.client.models.exteriors.PoliceBoxModel";
    private static final String AIT_PACKAGE = "dev.amble.ait.";

    private final Set<Object> done = Collections.newSetFromMap(new WeakHashMap<>());
    private int cooldown;
    private boolean warned;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++cooldown < 5) return;
        cooldown = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        try {
            scan(mc.getBlockEntityRenderDispatcher(), BlockEntityRenderer.class);
            scan(mc.getEntityRenderDispatcher(), EntityRenderer.class);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Renaissance Sign could not scan AiT's renderers; the sign will not appear", t);
            }
        }
    }

    /** Looks through every Map field of the dispatcher for renderers belonging to AiT. */
    private void scan(Object dispatcher, Class<?> rendererType) throws Exception {
        for (Class<?> c = dispatcher.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object value = f.get(dispatcher);
                if (!(value instanceof Map<?, ?> map)) continue;
                for (Object renderer : map.values()) {
                    if (rendererType.isInstance(renderer) && renderer.getClass().getName().startsWith(AIT_PACKAGE)) {
                        patchHolder(renderer);
                    }
                }
            }
        }
    }

    /** A renderer is a "holder" when it has a Renaissance variant and a police box model in its fields. */
    private void patchHolder(Object renderer) throws Exception {
        Object variant = null;
        Object model = null;
        for (Class<?> c = renderer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                Object v = f.get(renderer);
                if (v == null) continue;
                String name = v.getClass().getName();
                if (name.equals(RENAISSANCE_VARIANT)) variant = v;
                else if (name.equals(POLICE_BOX_MODEL)) model = v;
            }
        }
        if (variant != null && model != null) patchModel(model);
    }

    private void patchModel(Object model) {
        try {
            ModelPart tardis = null;
            for (Field f : model.getClass().getDeclaredFields()) {
                if (f.getType() == ModelPart.class && f.getName().equals("TARDIS")) {
                    f.setAccessible(true);
                    tardis = (ModelPart) f.get(model);
                }
            }
            if (tardis == null || done.contains(tardis)) return;
            done.add(tardis);

            ModelPart target = tardis.getChild("TARDIS_t").getChild("PCB_t");
            ModelPart sign = SignGeometry.bake();
            transplant(target, sign);
            LOGGER.info("Renaissance Sign: new sign installed on a Renaissance police box model");
        } catch (Throwable t) {
            LOGGER.warn("Renaissance Sign could not patch a police box model", t);
        }
    }

    /** Copies the cube list and child parts of {@code from} into {@code to} (both are baked, immutable parts). */
    private static void transplant(ModelPart to, ModelPart from) throws Exception {
        boolean copiedCubes = false;
        boolean copiedChildren = false;
        for (Field f : ModelPart.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == List.class) {
                f.setAccessible(true);
                f.set(to, f.get(from));
                copiedCubes = true;
            } else if (f.getType() == Map.class) {
                f.setAccessible(true);
                f.set(to, f.get(from));
                copiedChildren = true;
            }
        }
        if (!copiedCubes || !copiedChildren) {
            throw new IllegalStateException("ModelPart layout not recognised");
        }
    }
}
