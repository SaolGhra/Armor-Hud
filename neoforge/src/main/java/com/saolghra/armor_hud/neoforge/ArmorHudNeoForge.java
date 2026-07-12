package com.saolghra.armor_hud.neoforge;

import com.saolghra.armor_hud.ArmorHud;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ArmorHud.MOD_ID, dist = Dist.CLIENT)
public class ArmorHudNeoForge {
    public ArmorHudNeoForge() {
        ArmorHud.init(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.addListener(this::onRenderGui);
    }

    private void onRenderGui(RenderGuiEvent.Post event) {
        ArmorHud.render(event.getGuiGraphics());
    }
}
