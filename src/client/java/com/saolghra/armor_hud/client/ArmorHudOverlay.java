package com.saolghra.armor_hud.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
                drawTexture(context, xOffset + armorSpacing, yOffset, 0, 0, boxSize, boxSize, 22, 22);

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
        float bobbingOffset = (float) Math.sin(currentTime / 200.0) * 2; // Adjust the divisor to control speed and amplitude

        // Draw the exclamation marks
        context.getMatrices().push();
        context.getMatrices().translate(x - 5, y + 16 + bobbingOffset, 500);
        context.getMatrices().scale(0.5f, 0.5f, 500f);
        context.drawTexture(EXCLAMATION_MARKS_TEXTURE, 0, 0, 0, 0, 22, 22, 22, 22);
        context.getMatrices().pop();
    }

    private void drawTexture(DrawContext context, int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight) {
        context.drawTexture(HOTBAR_TEXTURE, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    private void drawDurabilityBar(DrawContext context, int x, int y, int width, ItemStack item) {
        int maxDamage = item.getMaxDamage();
        int damage = item.getDamage();

        if (damage == 0) {
            return;
        }

        int durability = maxDamage - damage;

        // Total width of the durability bar
        int barWidth = width - 8;
        int barX = x + 4;
        int barHeight = 2;

        // Calculate the width of the remaining and the lost durability
        int remainingWidth = (int) ((durability / (float) maxDamage) * barWidth);
//        int lostWidth = barWidth - remainingWidth;

        // Variable to have colours
        int barColor;
        float durabilityRatio = (durability / (float) maxDamage);

        if (durabilityRatio > 0.65) {
            barColor = 0xFF00FF00; // Green
        } else if (durabilityRatio > 0.20) {
            barColor = 0xFFFFFF00; // Yellow
        } else {
            barColor = 0xFFFF0000; // Red
        }

        // Draw the remaining durability bar
        fill(context, barX, y, barX + remainingWidth, y + barHeight, barColor);

        // Draw the lost durability bar (black)
        fill(context, barX + remainingWidth, y, barX + barWidth, y + barHeight, 0xFF000000);
    }

    private void fill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }
}