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
// The handler captures NONE of render's arguments — just CallbackInfo. Mixin's checkDescriptor
// accepts a handler whose descriptor equals the "simple" callback descriptor (CallbackInfo only) and
// runs it with captureArgs=false, so this matches render(...) regardless of its parameters. That is
// deliberate: render's signature varies across the matrix (DeltaTracker vs. float partialTick, etc.),
// and an arg-less handler needs no per-version Stonecutter guard and no version-specific imports —
// it compiles and applies on every node. `method = "render"` resolves by name (unique in GameRenderer).
//
// Line comments only, matching ArmorHudVerifyHook, so Stonecutter can comment the file out cleanly.
//? if verify {
/*import com.saolghra.armor_hud.client.ArmorHudVerifyHook;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class ArmorHudVerifyGameRendererMixin {
    // TAIL of render: world and the flushed, composited HUD are both in the main render target now.
    @Inject(method = "render", at = @At("TAIL"))
    private void armorHudVerifyCapture(CallbackInfo ci) {
        ArmorHudVerifyHook.captureFrame();
    }
}
*///?}
