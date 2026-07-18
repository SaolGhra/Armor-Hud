package com.saolghra.armor_hud.neoforge;

import com.saolghra.armor_hud.ArmorHud;

//? if >=1.20.5 {
/*import com.saolghra.armor_hud.client.config.ArmorHudConfigScreen;

//? if >=1.20.6 {
/^import net.neoforged.api.distmarker.Dist;
^///?}
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/^*
 * NeoForge entrypoint. From 1.20.5 (NeoForge 20.5+) the modern config API applies: the screen is an
 * {@link IConfigScreenFactory} extension point registered on the injected {@link ModContainer}.
 * {@code @Mod(dist=…)} only arrived in 20.6, so 1.20.5 uses a plain {@code @Mod} (harmless — this mod
 * is client-only regardless). The legacy Forge-style form (NeoForge &lt;=20.4) is the else-branch.
 ^/
//? if >=1.20.6 {
/^@Mod(value = ArmorHud.MOD_ID, dist = Dist.CLIENT)
^///?} else {
@Mod(ArmorHud.MOD_ID)
//?}
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
*///?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.common.Mod;

// Legacy NeoForge (<=20.4) has no @Mod(dist=...), so the class loads on both sides. Client wiring is
// deferred to ArmorHudNeoForgeClient through DistExecutor so client types never load on a server.
@Mod(ArmorHud.MOD_ID)
public class ArmorHudNeoForge {
    public ArmorHudNeoForge() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ArmorHudNeoForgeClient::init);
    }
}
//?}
