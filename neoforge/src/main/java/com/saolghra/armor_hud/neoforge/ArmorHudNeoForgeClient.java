package com.saolghra.armor_hud.neoforge;

//? if <1.20.5 {
/*import com.saolghra.armor_hud.ArmorHud;
import com.saolghra.armor_hud.client.config.ArmorHudConfigScreen;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

// Client-only wiring for legacy NeoForge (<=20.4), mirroring the Forge 1.20.1 module: load config,
// forward the HUD-render event, and register the config screen via the old ConfigScreenHandler.
// Invoked from ArmorHudNeoForge only under DistExecutor, so it is never classloaded on a server.
final class ArmorHudNeoForgeClient {
    private ArmorHudNeoForgeClient() {}

    static void init() {
        ArmorHud.init(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.addListener(ArmorHudNeoForgeClient::onRenderGui);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new ArmorHudConfigScreen(parent)));
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        ArmorHud.render(event.getGuiGraphics());
    }
}
*///?} else {
// Unused on modern NeoForge (>=1.20.5): ArmorHudNeoForge wires everything directly.
final class ArmorHudNeoForgeClient {
    private ArmorHudNeoForgeClient() {}
}
//?}
