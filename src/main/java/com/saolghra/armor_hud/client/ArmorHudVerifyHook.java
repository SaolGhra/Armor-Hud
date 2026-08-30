package com.saolghra.armor_hud.client;

// Verification seam for the release harness, compiled ONLY into verification builds
// (-Parmorhud.verify=true). Released jars contain neither this class nor the call to it.
//
// Two jobs, both driven by environment variables the harness sets before launching:
//
//  * ARMOR_HUD_VERIFY_OPEN_CONFIG opens the mod's own config screen a few frames into the world,
//    so the config UI can be screenshot-tested without input automation. (This constructs the
//    screen directly, so it does NOT exercise ModMenu or the loaders' config registration — those
//    need the real mod list driven by input.)
//
//  * ARMOR_HUD_VERIFY_SHOT makes the mod take a screenshot of its OWN GL framebuffer once the world
//    has settled, via Minecraft's Screenshot.grab. This exists because a headless CI agent never
//    maps a visible X window — Minecraft renders correctly but there is no on-screen window to
//    photograph, so every X-based capture returns black. Reading the framebuffer directly is
//    independent of whether any window was mapped.
//
// Deliberately uses line comments only — Stonecutter has to comment this whole file out for
// release builds, and nested block comments make that encoding fragile. That same fragility is
// why the two 26.x API renames below (Minecraft#setScreen -> #setScreenAndShow at 26.1,
// Minecraft#getMainRenderTarget() removed at 26.2) are resolved with reflection instead of a
// nested Stonecutter guard — this is a one-off verification-only call, not shipped code, so the
// small reflection cost is a fair trade for not fighting comment-nesting here.
//? if verify {
/*import com.saolghra.armor_hud.client.config.ArmorHudConfigScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;

public final class ArmorHudVerifyHook {
    // Gradle's runClient forks a JVM that inherits the environment but not ad-hoc -D flags,
    // so the harness sets these as environment variables (with matching -D fallbacks).
    private static final String OPEN_PROPERTY = "armor_hud.verify.openConfig";
    private static final String OPEN_ENV = "ARMOR_HUD_VERIFY_OPEN_CONFIG";
    private static final String SHOT_PROPERTY = "armor_hud.verify.shot";
    private static final String SHOT_ENV = "ARMOR_HUD_VERIFY_SHOT";

    // Frames to let the world settle before acting, so a capture is of the world, not a loading GUI.
    private static final int DELAY_FRAMES = 120;

    // Resolved once — these are read on the render path, which runs every frame.
    private static final String MODE = resolve(OPEN_PROPERTY, OPEN_ENV);
    private static final boolean SHOT = resolve(SHOT_PROPERTY, SHOT_ENV) != null;

    private static int framesSeen;
    private static boolean opened;
    private static boolean shotTaken;

    private ArmorHudVerifyHook() {}

    // A property/env value, or null when unset. Empty counts as unset: the harness exports the
    // variable with no value to request a plain in-world capture, and System.getenv returns "".
    private static String resolve(String property, String env) {
        String v = System.getProperty(property);
        if (v == null || v.isEmpty()) {
            v = System.getenv(env);
        }
        return (v == null || v.isEmpty()) ? null : v;
    }

    // Called once per in-world HUD frame — so it runs only while the HUD is on screen, which is
    // exactly the frame a HUD screenshot should capture.
    static void onFrame() {
        if (++framesSeen < DELAY_FRAMES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        if (MODE != null && !opened) {
            // This seam never exercises onClose() through a real user interaction, only the
            // harness's own screenshot/teardown, so a null parent (return to gameplay on close)
            // is fine on every version — sidesteps Minecraft#screen, which 26.2 removed entirely
            // with no direct replacement.
            ArmorHudConfigScreen screen = new ArmorHudConfigScreen(null);
            screen.setInteractiveMode("interactive".equals(MODE));
            setScreen(client, screen);
            opened = true;
        }
    }

    // 26.1 added Minecraft#setScreenAndShow alongside the older #setScreen; 26.2 removed #setScreen
    // entirely, leaving only #setScreenAndShow. Tries the newer name first (present on every 26.x
    // node) and falls back to the older one (present on every pre-26 node).
    private static void setScreen(Minecraft client, Screen screen) {
        for (String name : new String[] {"setScreenAndShow", "setScreen"}) {
            try {
                Method m = Minecraft.class.getMethod(name, Screen.class);
                m.invoke(client, screen);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        System.out.println("[armor_hud verify] no setScreen/setScreenAndShow method found");
    }

    // Called by ArmorHudVerifyGameRendererMixin at the TAIL of GameRenderer.render — the point where
    // the whole frame, world AND the fully composited HUD, is in the main render target. Grabbing
    // from the HUD render hook instead gets world-only, because the GUI is still batched there and is
    // flushed to the target only after this. Screenshot.grab auto-names a PNG into
    // <gameDir>/screenshots/.
    public static void captureFrame() {
        if (!SHOT || shotTaken || framesSeen < DELAY_FRAMES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        shotTaken = true;
        System.out.println("[armor_hud verify] framebuffer screenshot into "
                + client.gameDirectory + "/screenshots");
        try {
            grab(client);
            System.out.println("[armor_hud verify] Screenshot.grab returned");
        } catch (Throwable t) {
            System.out.println("[armor_hud verify] Screenshot.grab threw: " + t);
        }
    }

    // 26.2 removed Minecraft#getMainRenderTarget() with no direct replacement, but the same release
    // added a simpler Screenshot.grab(Minecraft, boolean) overload that needs no render target at
    // all. Prefers the older (File, RenderTarget, Consumer) overload — present on every pre-26.2
    // node — and falls back to the newer one.
    private static void grab(Minecraft client) throws ReflectiveOperationException {
        try {
            // target.getClass() would return the concrete RenderTarget subclass Minecraft actually
            // hands back, which does not exact-match the (File, RenderTarget, Consumer) overload's
            // declared parameter type -- getMethod requires an exact type match, not a subtype.
            // getReturnType() gives the declared type instead.
            Method getTarget = Minecraft.class.getMethod("getMainRenderTarget");
            Object target = getTarget.invoke(client);
            Method grab = Screenshot.class.getMethod(
                    "grab", java.io.File.class, getTarget.getReturnType(), java.util.function.Consumer.class);
            grab.invoke(null, client.gameDirectory, target, (java.util.function.Consumer<net.minecraft.network.chat.Component>) message -> {});
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        Method grab = Screenshot.class.getMethod("grab", Minecraft.class, boolean.class);
        grab.invoke(null, client, false);
    }
}
*///?}
