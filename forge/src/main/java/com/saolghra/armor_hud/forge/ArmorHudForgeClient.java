package com.saolghra.armor_hud.forge;

import com.saolghra.armor_hud.ArmorHud;

import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client-only wiring for Forge: loads config and forwards the HUD-render event to common. */
public final class ArmorHudForgeClient {
    private ArmorHudForgeClient() {}

    public static void init() {
        ArmorHud.init(FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.addListener(ArmorHudForgeClient::onRenderGui);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        ArmorHud.render(event.getGuiGraphics());
    }
}
