package com.saolghra.armor_hud.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ArmorHudConfig {
    private static volatile ArmorHudConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("armor_hud.json");

    // Fields without default values
    private int xOffset;
    private int yOffset;
    private boolean showExclamationMarks;
    private float durabilityWarningThreshold;
    private int boxSize;
    private int spacing;
    private boolean visible;

    // Default constants
    private static final int DEFAULT_X_OFFSET = -224;
    private static final int DEFAULT_Y_OFFSET = -22;
    private static final int DEFAULT_BOX_SIZE = 22;
    private static final int DEFAULT_SPACING = 2;


    public ArmorHudConfig() {}

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
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                return GSON.fromJson(json, ArmorHudConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to load config: " + e.getMessage());
            }
        }
        return createDefaultConfig();
    }
    private static ArmorHudConfig createDefaultConfig() {
        ArmorHudConfig config = new ArmorHudConfig();
        config.xOffset = DEFAULT_X_OFFSET;
        config.yOffset = DEFAULT_Y_OFFSET;
        config.boxSize = DEFAULT_BOX_SIZE;
        config.spacing = DEFAULT_SPACING;
        config.showExclamationMarks = true;
        config.durabilityWarningThreshold = 0.20f;
        config.visible = true;
        config.saveConfig();
        return config;
    }

    private void initDefaults() {
        xOffset = DEFAULT_X_OFFSET;
        yOffset = DEFAULT_Y_OFFSET;
        boxSize = DEFAULT_BOX_SIZE;
        spacing = DEFAULT_SPACING;
        showExclamationMarks = true;
        durabilityWarningThreshold = 0.20f;
        visible = true;
    }

    private void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                ArmorHudConfig loaded = GSON.fromJson(json, ArmorHudConfig.class);
                if (loaded != null) {
                    copyFrom(loaded);
                }
            } else {
                saveConfig();
            }
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }

    public void saveConfig() {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    private void copyFrom(ArmorHudConfig other) {
        if (other != null) {
            this.xOffset = other.xOffset;
            this.yOffset = other.yOffset;
            this.showExclamationMarks = other.showExclamationMarks;
            this.durabilityWarningThreshold = other.durabilityWarningThreshold;
            this.boxSize = other.boxSize;
            this.spacing = other.spacing;
            this.visible = other.visible;
        }
    }

    public void resetToDefaults() {
        ArmorHudConfig defaults = createDefaultConfig();
        copyFrom(defaults);
        saveConfig();
    }

    // Getters and setters with save calls
    public int getXOffset() { return xOffset; }
    public void setXOffset(int xOffset) {
        this.xOffset = xOffset;
        saveConfig();
    }

    public int getYOffset() { return yOffset; }
    public void setYOffset(int yOffset) {
        this.yOffset = yOffset;
        saveConfig();
    }

    public boolean isShowExclamationMarks() { return showExclamationMarks; }
    public void setShowExclamationMarks(boolean show) {
        this.showExclamationMarks = show;
        saveConfig();
    }

    public float getDurabilityWarningThreshold() { return durabilityWarningThreshold; }
    public void setDurabilityWarningThreshold(float threshold) {
        this.durabilityWarningThreshold = threshold;
        saveConfig();
    }

    public int getBoxSize() { return boxSize; }
    public void setBoxSize(int size) {
        this.boxSize = size;
        saveConfig();
    }

    public int getSpacing() { return spacing; }
    public void setSpacing(int spacing) {
        this.spacing = spacing;
        saveConfig();
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) {
        this.visible = visible;
        saveConfig();
    }
}