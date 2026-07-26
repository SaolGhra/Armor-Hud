package com.saolghra.armor_hud.mixin;

// Verification-only mixin. Like ArmorHudVerifyHook, the whole class is compiled out of release builds
// by the `verify` Stonecutter const, and the mixin config that registers it is added to the manifest
// only in verify builds — so shipped jars contain no mixin at all, keeping the mod's mixin-free promise.
//
// It lives in its OWN package (com.saolghra.armor_hud.mixin), NOT alongside the mod's real classes.
// A mixin config's declared `package` is claimed by Mixin as mixins-only for the whole subtree, and it
// forbids ordinary code from referencing anything under it (IllegalClassLoadError). Pointing the config
// at com.saolghra.armor_hud.client — which is full of real runtime classes (ArmorHudConfigScreen,
// ArmorHudOverlay, …) — crashes the game during mod construction the instant one is loaded.
//
// Why a mixin is needed: the mod's own per-frame hook (HudRenderCallback / RenderGuiEvent.Post) runs
// while the GUI is still batched, before it is flushed into the main render target. A framebuffer
// screenshot taken there — the only capture that works on a headless agent with no visible window —
// comes out world-only, with no HUD. GameRenderer.render's TAIL is after the GUI flush, so the main
// render target holds the fully composited frame there. That is the point F2 screenshots effectively
// use, and it is the earliest place a screenshot includes the HUD.
//
// Line comments only, matching ArmorHudVerifyHook, so Stonecutter can comment the file out cleanly.
//? if verify {
/*import com.saolghra.armor_hud.client.ArmorHudVerifyHook;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class ArmorHudVerifyGameRendererMixin {
    // TAIL of render: world and the flushed, composited HUD are both in the main render target now.
    @Inject(method = "render", at = @At("TAIL"))
    private void armorHudVerifyCapture(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ArmorHudVerifyHook.captureFrame();
    }
}
*///?}
