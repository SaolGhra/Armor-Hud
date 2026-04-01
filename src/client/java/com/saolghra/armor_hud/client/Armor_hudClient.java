package com.saolghra.armor_hud.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class Armor_hudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(Identifier.parse("armor_hud:armor_hud"), (graphics, deltaTracker) -> new ArmorHudOverlay().renderArmorUI(graphics));
    }
}
