package com.saolghra.armor_hud.fabric;

import com.saolghra.armor_hud.ArmorHud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;

public class ArmorHudFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ArmorHud.init(FabricLoader.getInstance().getConfigDir());
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> ArmorHud.render(graphics));
    }
}
