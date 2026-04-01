package com.saolghra.armor_hud.client;

import com.saolghra.armor_hud.client.config.ArmorHudConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ArmorHudOverlay {
    private final ArmorHudConfig config = ArmorHudConfig.getInstance();
    private static final Identifier HOTBAR_OFFHAND_LEFT = Identifier.parse("minecraft:textures/gui/sprites/hud/hotbar_offhand_left.png");
    private static final Identifier EXCLAMATION_MARKS_TEXTURE = Identifier.parse("armor_hud:textures/gui/exclamation_marks_flash.png");

    public void renderArmorUI(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();

        if (!config.isVisible() || client.options.hideGui || client.player == null || client.level == null) {
            return;
        }

        // Get armor items
        ItemStack[] armorItems = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armorItems[i] = client.player.getInventory().getItem(36 + i);
        }

        // Get screen width and height
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        // Use config values
        boolean vertical = config.isVertical();
        int boxSize = config.getBoxSize();
        int spacing = config.getSpacing();
        int slotStride = boxSize + spacing;
    int xOffset = screenWidth / 2 + config.getXOffset();
    int yOffset = screenHeight + config.getYOffset();

        // Draw armor boxes and icons (iterate by slot: 0 boots -> 3 helmet)
        for (int slot = 0; slot < armorItems.length; slot++) {
            ItemStack armorItem = armorItems[slot];

            if (!armorItem.isEmpty()) {
                int armorSpacing = slot * slotStride;

                int drawX = vertical ? xOffset : xOffset + ((armorItems.length - 1 - slot) * slotStride);
                int drawY = vertical ? (yOffset - boxSize - armorSpacing) : yOffset;

                // Draw slot background
                drawTexture(graphics, drawX, drawY, boxSize, boxSize);

                // Draw armor icon
                graphics.item(armorItem, drawX + (boxSize - 16) / 2, drawY + (boxSize - 16) / 2);
            }
        }

        // Draw durability bar and exclamation mark
        for (int slot = 0; slot < armorItems.length; slot++) {
            ItemStack armorItem = armorItems[slot];

            if (!armorItem.isEmpty()) {
                int armorSpacing = slot * slotStride;

                int drawX = vertical ? xOffset : xOffset + ((armorItems.length - 1 - slot) * slotStride);
                int drawY = vertical ? (yOffset - boxSize - armorSpacing) : yOffset;

                // Draw the durability (either bar or numeric points)
                if (config.isShowDurabilityPoints()) {
                    drawDurabilityPoints(graphics, drawX, drawY, boxSize, armorItem);
                } else {
                    drawDurabilityBar(graphics, drawX, drawY + boxSize - 6, boxSize, armorItem);
                }

                // Draw exclamation mark if needed
                if (isDurabilityLow(armorItem) && config.isShowExclamationMarks()) {
                    // Exclamation mark appears at the top right of the box
                    drawExclamationMark(graphics, drawX, drawY, boxSize);
                }
            }
        }
    }

    private void drawDurabilityPoints(GuiGraphicsExtractor graphics, int boxX, int boxY, int boxSize, ItemStack item) {
        int maxDamage = item.getMaxDamage();
        int damage = item.getDamageValue();

        if (maxDamage <= 0) return;

        int remaining = maxDamage - damage;
        String text = String.valueOf(remaining);

        // Draw a small badge at the top-right inside the box so it doesn't cover the icon
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        // Draw small numeric durability as compact top-right label (no badge) so it doesn't cover the icon.
    String numText = text;
    // compact will be the working string; we won't do last-two-digit truncation here
    String compact = numText;
            // Always show the full remaining number (no '+'). If it overflows the box it will still be drawn.
            String display = compact;
            int finalWidth = client.font.width(display);
            int finalX = boxX + boxSize - 2 - finalWidth;
            int drawTextY = boxY + 2; // small inset from top
            // slightly more transparent white to feel smaller
            graphics.text(client.font, Component.literal(display), finalX, drawTextY, 0x88FFFFFF, true);
    }

    private boolean isDurabilityLow(ItemStack item) {
        int maxDamage = item.getMaxDamage();
        int damage = item.getDamageValue();
        return damage > 0 && (maxDamage - damage) / (float) maxDamage < config.getDurabilityWarningThreshold();
    }

    private void drawExclamationMark(GuiGraphicsExtractor graphics, int boxX, int boxY, int boxSize) {
        long currentTime = System.currentTimeMillis();
        float bobbingOffset = (float) Math.sin(currentTime / 200.0) * 2;

        int iconSize = 11;
        int offsetX = -1;
        int offsetY = -2;

        int drawX = boxX + offsetX;
        int drawY = boxY + offsetY + (int) bobbingOffset;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                EXCLAMATION_MARKS_TEXTURE,
                drawX, drawY,
                0, 0,
                iconSize, iconSize,
                iconSize, iconSize
        );
    }

    private void drawTexture(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        // hotbar_offhand_left.png is 29x24 pixels
        // Remove 6 pixels from right side: use first 23 pixels width, full 24 height
        int spriteWidth = 23;
        int spriteHeight = 24;
        
        // Center the sprite within the configured box
        int spriteX = x + (width - spriteWidth) / 2;
        int spriteY = y + (height - spriteHeight) / 2;
        
        // Blit with correct texture dimensions: 29x24 (the actual file size)
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                HOTBAR_OFFHAND_LEFT,
                spriteX, spriteY,
                0, 0,           // texture U, V: top-left corner
                spriteWidth, spriteHeight,  // read 23x24 from texture (removes right 6px)
                29, 24          // hotbar_offhand_left.png is 29x24
        );
    }

    private void drawDurabilityBar(GuiGraphicsExtractor graphics, int x, int y, int width, ItemStack item) {
        int maxDamage = item.getMaxDamage();
        int damage = item.getDamageValue();

        if (maxDamage <= 0 || damage <= 0) {
            return;
        }

        // Total width of the durability bar
        int barWidth = 13;
        int barX = x + (width / 2) - (barWidth / 2);

        float durabilityRatio = (maxDamage - damage) / (float) maxDamage;
        durabilityRatio = Math.max(0.0f, Math.min(1.0f, durabilityRatio));

        int remainingWidth = Math.max(0, Math.min(barWidth, Math.round(durabilityRatio * barWidth)));

        // Vanilla-style durability bar: 2px black background with 1px colored foreground.
        int barColor = convertHSVtoARGB(durabilityRatio * 120f, 1f, 1f);
        int barBackgroundHeight = 2;
        int barForegroundHeight = 1;

        fill(graphics, barX, y, barX + barWidth, y + barBackgroundHeight, 0xFF000000);
        if (remainingWidth > 0) {
            fill(graphics, barX, y, barX + remainingWidth, y + barForegroundHeight, 0xFF000000 | (barColor & 0x00FFFFFF));
        }
    }

    private void fill(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
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