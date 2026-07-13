package com.saolghra.armor_hud.forge;

import com.saolghra.armor_hud.ArmorHud;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge (1.20.1) entrypoint. All client-only code lives in {@link ArmorHudForgeClient}, invoked via
 * {@link DistExecutor} so its client class references are never loaded on a dedicated server.
 */
@Mod(ArmorHud.MOD_ID)
public class ArmorHudForge {
    public ArmorHudForge() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ArmorHudForgeClient::init);
    }
}
