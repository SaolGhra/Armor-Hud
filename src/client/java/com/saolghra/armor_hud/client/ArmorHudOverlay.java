package com.saolghra.armor_hud.client;

import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class ArmorHudOverlay {
    private static final Identifier HOTBAR_TEXTURE = Identifier.of("armor_hud", "textures/gui/hotbar_texture.png");
    private static final Identifier EXCLAMATION_MARKS_TEXTURE = Identifier.of("armor_hud", "textures/gui/exclamation_marks_flash.png");

    public void renderArmorUI(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.options.hudHidden || client.player == null || client.world == null) {
            return;
        }

        // Get armor items
        ItemStack[] armorItems = client.player.getInventory().armor.toArray(new ItemStack[0]);

        // Position for the armor boxes
        int boxSize = 22;
        int spacing = 2; // Was 4

        // Get screen width and height
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Calculate the position for the offhand slot
        int offhandSlotX = screenWidth / 2 - 124; // Was 2 - 120

        // Offset based on offhand slot position
        int xOffset = offhandSlotX - (armorItems.length * (boxSize + spacing) + spacing);
        int yOffset = screenHeight - 22;

        // Bind the hotbar texture
        context.getMatrices().push();
        context.getMatrices().scale(1.0f, 1.0f, 1.0f);
        context.getMatrices().pop();

        // int i = 0; i < armorItems.length; i++
        for (int i = armorItems.length - 1; i >= 0; i--) {
            ItemStack armorItem = armorItems[i];

            if (!armorItem.isEmpty()) {
                // Reused variable redeclaration.
                int armorSpacing = (armorItems.length - 1 - i) * (boxSize + spacing);

                // Draw box background
                drawTexture(context, xOffset + armorSpacing, yOffset, boxSize, boxSize);

                // Draw armor icon
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 0);
                context.drawItem(armorItem, xOffset + armorSpacing + (boxSize - 16) / 2, yOffset + (boxSize - 16) / 2);
                context.getMatrices().pop();
            }
        }

        // Draw durability bar separately so it is on top
        for (int i = armorItems.length - 1; i >= 0; i--) {
            ItemStack armorItem = armorItems[i];

            if (!armorItem.isEmpty()) {
                int armorSpacing = (armorItems.length - 1 - i) * (boxSize + spacing);

                // Draw the durability
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200);
                drawDurabilityBar(context, xOffset + armorSpacing, yOffset + boxSize - 6, boxSize, armorItem);
                context.getMatrices().pop();

                if(isDurabilityLow(armorItem)) {
                    drawExclamationMark(context, xOffset + armorSpacing + (boxSize - 16) / 2, yOffset - 20);
                }
            }
        }
    }

    // Check if the durability is low
    private boolean isDurabilityLow(ItemStack item) {
        int maxDamage = item.getMaxDamage();
        int damage = item.getDamage();
        return damage > 0 && (maxDamage - damage) / (float) maxDamage < 0.20;
    }

    private void drawExclamationMark(DrawContext context, int x, int y) {
        long currentTime = System.currentTimeMillis();

        // Calculate the bobbing offset using a sine wave function
        float bobbingOffset = (float) Math.sin(currentTime / 200.0) * 2;

        // Draw the exclamation marks
        context.getMatrices().push();
        context.getMatrices().translate(x - 5, y + 16 + bobbingOffset, 500);
        context.getMatrices().scale(0.5f, 0.5f, 500f);

        context.drawTexture(
                RenderLayer::getGuiTexturedOverlay,
                EXCLAMATION_MARKS_TEXTURE,
                0, 0,
                0f, 0f,
                22, 22,
                22, 22
        );

        context.getMatrices().pop();
    }

    private void drawTexture(DrawContext context, int x, int y, int width, int height) {
        context.drawTexture(
                RenderLayer::getGuiTexturedOverlay,
                HOTBAR_TEXTURE,
                x, y,
                0f, 0f,
                width, height,
                22, 22
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