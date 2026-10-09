package com.personal.hadsswitch.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapmethod.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Fixes: NullPointerException in Tardis.hasGrowthExterior when Immersive Portals
 * triggers a fog pass (MyGameRenderer.resetFogState -> FogRenderer.setupFog ->
 * AiT FoggyUtils.overrideFog -> Tardis.isGrowth) while the client-side Tardis
 * has no exterior yet.
 *
 * Wraps the method and treats a missing exterior as "not a growth exterior".
 *
 * Needs MixinExtras 0.4.0+ (WrapMethod). Your pack already ships 0.4.1.
 * Check hasGrowthExterior's return type in a decompiler; this assumes boolean.
 */
@Mixin(targets = "dev.amble.ait.core.tardis.Tardis", remap = false)
public abstract class TardisMixin {

    @WrapMethod(method = "hasGrowthExterior")
    private boolean hads$nullSafeGrowthExterior(Operation<Boolean> original) {
        try {
            return original.call();
        } catch (NullPointerException e) {
            return false;
        }
    }
}
