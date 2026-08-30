package com.saolghra.armor_hud.fabric;

import com.saolghra.armor_hud.ArmorHud;

import net.fabricmc.api.ClientModInitializer;
// 26.1 replaced the HudRenderCallback event entirely with the HudElementRegistry/HudElement layer
// system (fabric-api restructured its whole HUD-rendering API around the same "extract render
// state, then submit" split as vanilla's GuiGraphics -> GuiGraphicsExtractor rename — there is no
// HudRenderCallback class left to fall back on at 26.1+, confirmed against the real resolved
// fabric-rendering-v1 jar). HudElement's render method is itself named extractRenderState, taking a
// GuiGraphicsExtractor, matching ArmorHud.render's own >=26.1 signature exactly.
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//?}
import net.fabricmc.loader.api.FabricLoader;

public class ArmorHudFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ArmorHud.init(FabricLoader.getInstance().getConfigDir());
        //? if >=26.1 {
        /*HudElementRegistry.addLast(net.minecraft.resources.Identifier.parse("armor_hud:overlay"),
                (graphics, tickCounter) -> ArmorHud.render(graphics));
        *///?} else {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> ArmorHud.render(graphics));
        //?}
    }
}
