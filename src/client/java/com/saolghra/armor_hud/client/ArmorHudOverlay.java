package com.saolghra.armor_hud.client;

import com.saolghra.armor_hud.client.config.ArmorHudConfig;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class ArmorHudOverlay {
    private final ArmorHudConfig config = ArmorHudConfig.getInstance();
    private static final Identifier HOTBAR_TEXTURE = Identifier.of("armor_hud", "textures/gui/hotbar_texture.png");
    private static final Identifier EXCLAMATION_MARKS_TEXTURE = Identifier.of("armor_hud", "textures/gui/exclamation_marks_flash.png");

    public void renderArmorUI(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!config.isVisible() || client.options.hudHidden || client.player == null || client.world == null) {
            return;
        }

        // Get armor items
        ItemStack[] armorItems = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armorItems[i] = client.player.getInventory().getStack(36 + i);
        }

        // Get screen width and height
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Use config values
        int boxSize = config.getBoxSize();
        int spacing = config.getSpacing();
        int xOffset = screenWidth / 2 + config.getXOffset();
        int yOffset = screenHeight + config.getYOffset();

        // Draw armor boxes and icons
        for (int i = armorItems.length - 1; i >= 0; i--) {
            ItemStack armorItem = armorItems[i];

            if (!armorItem.isEmpty()) {
                int armorSpacing = (armorItems.length - 1 - i) * (boxSize + spacing);

                // Draw box background
                drawTexture(context, xOffset + armorSpacing, yOffset, boxSize, boxSize);

                // Draw armor icon
                context.drawItem(armorItem, xOffset + armorSpacing + (boxSize - 16) / 2, yOffset + (boxSize - 16) / 2);
            }
        }

        // Draw durability bar and exclamation mark
        for (int i = armorItems.length - 1; i >= 0; i--) {
            ItemStack armorItem = armorItems[i];

            if (!armorItem.isEmpty()) {
                int armorSpacing = (armorItems.length - 1 - i) * (boxSize + spacing);

                // Draw the durability
                drawDurabilityBar(context, xOffset + armorSpacing, yOffset + boxSize - 6, boxSize, armorItem);

                // Draw exclamation mark if needed
                if (isDurabilityLow(armorItem) && config.isShowExclamationMarks()) {
                    // Exclamation mark appears at the top right of the box
                    drawExclamationMark(context, xOffset + armorSpacing, yOffset, boxSize);
                }
            }
        }
    }

    private boolean isDurabilityLow(ItemStack item) {
        int maxDamage = item.getMaxDamage();
        int damage = item.getDamage();
        return damage > 0 && (maxDamage - damage) / (float) maxDamage < config.getDurabilityWarningThreshold();
    }

    private void drawExclamationMark(DrawContext context, int boxX, int boxY, int boxSize) {
        long currentTime = System.currentTimeMillis();
        float bobbingOffset = (float) Math.sin(currentTime / 200.0) * 2;

        int iconSize = 11;
        int offsetX = 0; // No offset from left edge (or try 1-2 for a tiny gap)
        int offsetY = -2; // Slightly above the box

        int drawX = boxX + offsetX; // Now positioned from the LEFT edge
        int drawY = boxY + offsetY + (int) bobbingOffset;

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                EXCLAMATION_MARKS_TEXTURE,
                drawX, drawY,
                0, 0,
                iconSize, iconSize,
                iconSize, iconSize
        );
    }

    private void drawTexture(DrawContext context, int x, int y, int width, int height) {
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                HOTBAR_TEXTURE,
                x, y,
                0, 0,
                width, height,
                width, height
        );
    }

    private void drawDurabilityBar(DrawContext context, int x, int y, int width, ItemStack item) {
        int maxDamage = item.getMaxDamage();
        int damage = item.getDamage();

        if (damage == 0) {
            return;
        }

        // Total width of the durability bar
        int barWidth = 13;
        int barX = x + (width - barWidth) / 2 + 1;
        int barHeight = 2;

        float durabilityRatio = ((maxDamage - damage) / (float) maxDamage);

        // Get remaining width using increments of barWidth / 13
        int remainingWidth = (int) Math.round(durabilityRatio * 13);

        // Get durability bar color from HSV
        int barColor = convertHSVtoARGB((durabilityRatio / 3f) * 360, 1, 1);

        // Draw whole black background
        fill(context, barX, y, barX + barWidth, y + barHeight, 0xFF000000);

        // Draw the remaining durability over the background
        fill(context, barX, y, barX + remainingWidth, y + barHeight / 2, barColor);
    }

    private void fill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    private int convertHSVtoARGB(float h, float s, float v) {
        h = (h % 360 + 360) % 360;

        float hh = h / 60.0f;
        int i = (int) hh % 6;

        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        int r = 0, g = 0, b = 0;

        switch (i) {
            case 0: r = Math.round(v * 255); g = Math.round(t * 255); b = Math.round(p * 255); break;
            case 1: r = Math.round(q * 255); g = Math.round(v * 255); b = Math.round(p * 255); break;
            case 2: r = Math.round(p * 255); g = Math.round(v * 255); b = Math.round(t * 255); break;
            case 3: r = Math.round(p * 255); g = Math.round(q * 255); b = Math.round(v * 255); break;
            case 4: r = Math.round(t * 255); g = Math.round(p * 255); b = Math.round(v * 255); break;
            case 5: r = Math.round(v * 255); g = Math.round(p * 255); b = Math.round(q * 255); break;
        }

        // Return standard RGB hex value
        return (255 << 24) | (r << 16) | (g << 8) | b;
    }
}