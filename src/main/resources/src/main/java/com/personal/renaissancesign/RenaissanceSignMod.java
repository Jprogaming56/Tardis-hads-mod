package com.personal.renaissancesign;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Client-only add-on: gives AiT's Renaissance police box the POLICE / PUBLIC CALL / BOX sign it is missing.
 * The sign geometry is built in {@link SignGeometry} and swapped into the box's model by {@link SignPatcher};
 * the lit lettering comes from the textures shipped under assets/ait (this mod loads after AiT, so they win).
 */
@Mod(RenaissanceSignMod.MOD_ID)
public class RenaissanceSignMod {
    public static final String MOD_ID = "renaissancesign";

    public RenaissanceSignMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new SignPatcher());
        }
    }
}
