package com.saolghra.armor_hud.client.config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ArmorHudConfig {
    private static final ArmorHudConfig INSTANCE = new ArmorHudConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;

    private int xOffset = -224;
    private int yOffset = -22;
    private boolean showExclamationMarks = true;
    private float durabilityWarningThreshold = 0.20f;
    private int boxSize = 22;
    private int spacing = 2;
    private boolean visible = true;

    private static final int DEFAULT_X_OFFSET = -224;
    private static final int DEFAULT_Y_OFFSET = -22;
    private static final int DEFAULT_BOX_SIZE = 22;
    private static final int DEFAULT_SPACING = 2;

    private ArmorHudConfig() {
        try {
            configPath = FabricLoader.getInstance().getConfigDir().resolve("armor_hud.json");
            loadConfig();
        } catch (Exception e) {
            System.err.println("Failed to initialize config path: " + e.getMessage());
        }
    }

    public static ArmorHudConfig getInstance() {
        return INSTANCE;
    }

    public void saveConfig() {
        try {
            if (configPath != null) {
                String json = GSON.toJson(this);
                Files.writeString(configPath, json);
            }
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    private void loadConfig() {
        try {
            if (configPath != null && Files.exists(configPath)) {
                String json = Files.readString(configPath);
                ArmorHudConfig loaded = GSON.fromJson(json, ArmorHudConfig.class);
                if (loaded != null) {
                    copyFrom(loaded);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
            resetToDefaults();
        }
    }

    private void copyFrom(ArmorHudConfig other) {
        this.xOffset = other.xOffset;
        this.yOffset = other.yOffset;
        this.showExclamationMarks = other.showExclamationMarks;
        this.durabilityWarningThreshold = other.durabilityWarningThreshold;
        this.boxSize = other.boxSize;
        this.spacing = other.spacing;
        this.visible = other.visible;
    }

    public void resetToDefaults() {
        xOffset = DEFAULT_X_OFFSET;
        yOffset = DEFAULT_Y_OFFSET;
        boxSize = DEFAULT_BOX_SIZE;
        spacing = DEFAULT_SPACING;
        showExclamationMarks = true;
        durabilityWarningThreshold = 0.20f;
        visible = true;
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