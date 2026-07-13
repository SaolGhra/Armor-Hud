package com.saolghra.armor_hud.neoforge;

import com.saolghra.armor_hud.ArmorHud;
import com.saolghra.armor_hud.client.config.ArmorHudConfigScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ArmorHud.MOD_ID, dist = Dist.CLIENT)
public class ArmorHudNeoForge {
    public ArmorHudNeoForge(ModContainer container) {
        ArmorHud.init(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.addListener(this::onRenderGui);

        // Enables the "Config" button on this mod's entry in the NeoForge mod list.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new ArmorHudConfigScreen(parent));
    }

    private void onRenderGui(RenderGuiEvent.Post event) {
        ArmorHud.render(event.getGuiGraphics());
    }
}
