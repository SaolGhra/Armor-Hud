package com.saolghra.armor_hud.fabric.config;

import com.saolghra.armor_hud.client.config.ArmorHudConfigScreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Exposes the shared {@link ArmorHudConfigScreen} through ModMenu's "Config" button on Fabric.
 * ModMenu is an optional dependency: this entrypoint only runs when ModMenu is installed, so the mod
 * has no hard dependency on it.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ArmorHudConfigScreen::new;
    }
}
