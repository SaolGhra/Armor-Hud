package com.saolghra.armor_hud.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class ArmorHudOverlay {
    private static final Identifier HOTBAR_TEXTURE = Identifier.of("minecraft", "textures/gui/widgets.png");

    public void renderArmorUI(DrawContext context, RenderTickCounter tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) return;

        // Get armor items
        ItemStack[] armorItems = client.player.getInventory().armor.toArray(new ItemStack[0]);

        // Define position for armor boxes
        int xOffset = 10;
        int yOffset = 10;
        int boxSize = 20;
        int spacing = 4;

        // Bind the hotbar texture
        context.getMatrices().push();
        context.getMatrices().scale(1.0f, 1.0f, 1.0f);
        context.getMatrices().pop();

        for (int i = 0; i < armorItems.length; i++) {
            ItemStack armorItem = armorItems[i];

            if (!armorItem.isEmpty()) {
                // Draw box background
                drawTexture(context, xOffset, yOffset + (i * (boxSize + spacing)), 0, 0, boxSize, boxSize, 256, 256);

                // Draw armor icon
                context.drawItem(armorItem, xOffset + 2, yOffset + (i * (boxSize + spacing)) + 2);

                // Draw the durability bar
                int durability = armorItem.getMaxDamage() - armorItem.getDamage();
                int durabilityWidth = (int) ((durability / (float) armorItem.getMaxDamage()) * boxSize);
                fill(context, xOffset, yOffset + (i * (boxSize + spacing)) + boxSize - 4, xOffset + durabilityWidth, yOffset + (i * (boxSize + spacing)) + boxSize, 0xFF00FF00); // Green for durability
            }
        }
    }

    private void drawTexture(DrawContext context, int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight) {
        context.drawTexture(HOTBAR_TEXTURE, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    private void fill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }
}