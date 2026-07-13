package com.saolghra.armor_hud.forge;

import com.saolghra.armor_hud.ArmorHud;
import com.saolghra.armor_hud.client.config.ArmorHudConfigScreen;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client-only wiring for Forge: loads config, forwards the HUD-render event, and registers the
 * config screen (which enables the "Config" button on this mod's mod-list entry). */
public final class ArmorHudForgeClient {
    private ArmorHudForgeClient() {}

    public static void init() {
        ArmorHud.init(FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.addListener(ArmorHudForgeClient::onRenderGui);

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new ArmorHudConfigScreen(parent)));
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        ArmorHud.render(event.getGuiGraphics());
    }
}
