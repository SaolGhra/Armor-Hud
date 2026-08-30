package com.saolghra.armor_hud;

import com.saolghra.armor_hud.client.ArmorHudOverlay;
import com.saolghra.armor_hud.config.ArmorHudConfig;

// 26.1 renamed GuiGraphics -> GuiGraphicsExtractor (same package) as part of the HUD/Screen render
// split into an "extract render state, then submit" pipeline. Only the name changed for our
// purposes; the method surface we use (fill/blit/pose/item/text) carries over unchanged.
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

import java.nio.file.Path;

/**
 * Loader-agnostic entry point. Each loader module calls {@link #init(Path)} with its config
 * directory, then forwards its native HUD-render hook to {@link #render}.
 */
public final class ArmorHud {
    public static final String MOD_ID = "armor_hud";

    private static ArmorHudOverlay overlay;

    private ArmorHud() {}

    /** Initializes config from the loader-provided config directory and builds the overlay once. */
    public static void init(Path configDir) {
        ArmorHudConfig.init(configDir);
        overlay = new ArmorHudOverlay();
    }

    /** Renders the armor HUD. Safe to call before {@link #init(Path)} (no-op until initialized). */
    //? if >=26.1 {
    /*public static void render(GuiGraphicsExtractor graphics) {
    *///?} else {
    public static void render(GuiGraphics graphics) {
    //?}
        if (overlay != null) {
            overlay.render(graphics);
        }
    }
}
