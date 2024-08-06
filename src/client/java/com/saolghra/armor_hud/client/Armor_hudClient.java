package com.saolghra.armor_hud.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class Armor_hudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register the HUD renderer Callback
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            new ArmorHudOverlay().renderArmorUI(context);
        });
    }
}
