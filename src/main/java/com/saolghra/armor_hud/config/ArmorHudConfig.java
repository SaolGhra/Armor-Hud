package com.saolghra.armor_hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loader-agnostic configuration for Armor HUD, persisted as {@code armor_hud.json}.
 *
 * <p>The config directory is supplied by the active loader via {@link #init(Path)} (Fabric passes
 * {@code FabricLoader.getConfigDir()}, NeoForge passes {@code FMLPaths.CONFIGDIR}), so this class
 * has no dependency on any mod loader and lives in the shared {@code common} source set.
 */
public class ArmorHudConfig {
    private static volatile ArmorHudConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Full path to the config file, set once by {@link #init(Path)} before first use. */
    private static volatile Path configPath;

    // Persisted fields
    private int xOffset;
    private int yOffset;
    private boolean showExclamationMarks;
    private boolean vertical;
    private boolean showDurabilityPoints;
    private float durabilityWarningThreshold;
    private int boxSize;
    private int spacing;
    private boolean visible;

    // Default constants
    private static final int DEFAULT_X_OFFSET = -224;
    private static final int DEFAULT_Y_OFFSET = -22;
    private static final int DEFAULT_BOX_SIZE = 22;
    private static final int DEFAULT_SPACING = 2;
    private static final boolean DEFAULT_VERTICAL = false;
    private static final boolean DEFAULT_SHOW_DURABILITY_POINTS = false;
    private static final boolean DEFAULT_SHOW_EXCLAMATION_MARKS = true;
    private static final float DEFAULT_WARNING_THRESHOLD = 0.20f;
    private static final boolean DEFAULT_VISIBLE = true;

    public ArmorHudConfig() {}

    /**
     * Registers the config directory for this run. Must be called by the loader entrypoint before
     * {@link #getInstance()}.
     */
    public static void init(Path configDir) {
        configPath = configDir.resolve("armor_hud.json");
    }

    public static ArmorHudConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (ArmorHudConfig.class) {
                if (INSTANCE == null) {
                    INSTANCE = loadOrCreate();
                }
            }
        }
        return INSTANCE;
    }

    private static ArmorHudConfig loadOrCreate() {
        Path path = configPath;
        if (path != null && Files.exists(path)) {
            try {
                ArmorHudConfig loaded = mergeWithDefaults(Files.readString(path));
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                System.err.println("[Armor HUD] Failed to load config: " + e.getMessage());
            }
        }
        return createDefaultConfig();
    }

    /**
     * Parses {@code json} onto a freshly defaulted config instead of handing GSON's raw result back
     * directly. {@code Gson.fromJson} only sets the fields actually present in the source text — a
     * field missing from the file (an older schema written before that field existed, a partial or
     * corrupted write, a manually edited file) would otherwise silently end up at Java's primitive
     * zero-value ({@code false}/{@code 0}) instead of this mod's own intended default. Merging the
     * loaded JSON onto a defaults-populated {@link JsonObject} keeps every field the file doesn't
     * mention at its proper default; only keys actually present in {@code json} override it.
     */
    private static ArmorHudConfig mergeWithDefaults(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (parsed == null || !parsed.isJsonObject()) {
            return null;
        }
        ArmorHudConfig defaults = new ArmorHudConfig();
        defaults.applyDefaults();
        JsonObject merged = GSON.toJsonTree(defaults).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        return GSON.fromJson(merged, ArmorHudConfig.class);
    }

    private static ArmorHudConfig createDefaultConfig() {
        ArmorHudConfig config = new ArmorHudConfig();
        config.applyDefaults();
        config.saveConfig();
        return config;
    }

    private void applyDefaults() {
        xOffset = DEFAULT_X_OFFSET;
        yOffset = DEFAULT_Y_OFFSET;
        boxSize = DEFAULT_BOX_SIZE;
        spacing = DEFAULT_SPACING;
        showExclamationMarks = DEFAULT_SHOW_EXCLAMATION_MARKS;
        vertical = DEFAULT_VERTICAL;
        showDurabilityPoints = DEFAULT_SHOW_DURABILITY_POINTS;
        durabilityWarningThreshold = DEFAULT_WARNING_THRESHOLD;
        visible = DEFAULT_VISIBLE;
    }

    public void saveConfig() {
        Path path = configPath;
        if (path == null) {
            return; // init() not called yet; nothing we can persist to
        }
        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[Armor HUD] Failed to save config: " + e.getMessage());
        }
    }

    private void copyFrom(ArmorHudConfig other) {
        if (other != null) {
            this.xOffset = other.xOffset;
            this.yOffset = other.yOffset;
            this.showExclamationMarks = other.showExclamationMarks;
            this.vertical = other.vertical;
            this.showDurabilityPoints = other.showDurabilityPoints;
            this.durabilityWarningThreshold = other.durabilityWarningThreshold;
            this.boxSize = other.boxSize;
            this.spacing = other.spacing;
            this.visible = other.visible;
        }
    }

    public void resetToDefaults() {
        applyDefaults();
        saveConfig();
    }

    // Getters and setters — every setter persists immediately.
    public int getXOffset() { return xOffset; }
    public void setXOffset(int xOffset) { this.xOffset = xOffset; saveConfig(); }

    public int getYOffset() { return yOffset; }
    public void setYOffset(int yOffset) { this.yOffset = yOffset; saveConfig(); }

    public boolean isShowExclamationMarks() { return showExclamationMarks; }
    public void setShowExclamationMarks(boolean show) { this.showExclamationMarks = show; saveConfig(); }

    public boolean isVertical() { return vertical; }
    public void setVertical(boolean vertical) { this.vertical = vertical; saveConfig(); }

    public boolean isShowDurabilityPoints() { return showDurabilityPoints; }
    public void setShowDurabilityPoints(boolean show) { this.showDurabilityPoints = show; saveConfig(); }

    public float getDurabilityWarningThreshold() { return durabilityWarningThreshold; }
    public void setDurabilityWarningThreshold(float threshold) { this.durabilityWarningThreshold = threshold; saveConfig(); }

    public int getBoxSize() { return boxSize; }
    public void setBoxSize(int size) { this.boxSize = size; saveConfig(); }

    public int getSpacing() { return spacing; }
    public void setSpacing(int spacing) { this.spacing = spacing; saveConfig(); }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; saveConfig(); }
}
