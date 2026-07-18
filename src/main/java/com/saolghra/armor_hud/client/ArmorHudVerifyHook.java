package com.saolghra.armor_hud.client;

// Verification seam for scripts/verify, compiled ONLY into verification builds
// (-Parmorhud.verify=true). Released jars contain neither this class nor the call to it.
//
// Why it exists: the harness runs on a Wayland session, and driving Minecraft's menus needs
// input automation. Setting ARMOR_HUD_VERIFY_OPEN_CONFIG (or -Darmor_hud.verify.openConfig)
// makes the mod open its own config screen a few frames into the world, so the config UI can be
// screenshot-tested on every version instead of spot-checked by hand.
//
// Note what this does NOT cover: it constructs the screen directly, so it never exercises
// ModMenu's entry point or NeoForge/Forge's config-screen registration. Those paths need the real
// mod list driven by input automation.
//
// Deliberately uses line comments only — Stonecutter has to comment this whole file out for
// release builds, and nested block comments make that encoding fragile.
//? if verify {
/*import com.saolghra.armor_hud.client.config.ArmorHudConfigScreen;

import net.minecraft.client.Minecraft;

final class ArmorHudVerifyHook {
    private static final String PROPERTY = "armor_hud.verify.openConfig";
    // Gradle's runClient forks a JVM that inherits the environment but not ad-hoc -D flags,
    // so the harness sets this instead.
    private static final String ENV_VAR = "ARMOR_HUD_VERIFY_OPEN_CONFIG";

    // Frames to let the world settle before swapping screens, so the capture isn't of a loading GUI.
    private static final int DELAY_FRAMES = 120;

    // Resolved once — this is read on the render path, which runs every frame.
    private static final String MODE = resolveMode();

    private static int framesSeen;
    private static boolean opened;

    private ArmorHudVerifyHook() {}

    // Requested mode, or null when unset. Empty counts as unset: the harness exports the variable
    // with no value to request a plain in-world capture, and System.getenv returns "" for that.
    private static String resolveMode() {
        String mode = System.getProperty(PROPERTY);
        if (mode == null || mode.isEmpty()) {
            mode = System.getenv(ENV_VAR);
        }
        return (mode == null || mode.isEmpty()) ? null : mode;
    }

    // Called once per HUD frame; opens the config screen when the harness asked for it.
    static void onFrame() {
        if (MODE == null || opened || ++framesSeen < DELAY_FRAMES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        ArmorHudConfigScreen screen = new ArmorHudConfigScreen(client.screen);
        screen.setInteractiveMode("interactive".equals(MODE));
        client.setScreen(screen);
        opened = true;
    }
}
*///?}
