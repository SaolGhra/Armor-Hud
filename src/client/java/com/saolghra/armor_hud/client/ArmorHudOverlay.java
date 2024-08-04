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

        // Position for the armor boxes
        int boxSize = 20;
        int spacing = 4;

        // Get screen width and height
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Calculate the position for the offhand slot
        int offhandSlotX = screenWidth / 2 + 10;
        int offhandSlotY = screenHeight - 60;

        // Offset based on offhand slot position
        int xOffset = offhandSlotX - (armorItems.length * (boxSize + spacing) + spacing);
        int yOffset = offhandSlotY;

        // Bind the hotbar texture
        context.getMatrices().push();
        context.getMatrices().scale(1.0f, 1.0f, 1.0f);
        context.getMatrices().pop();

        // int i = 0; i < armorItems.length; i++
        for (int i = armorItems.length - 1; i >= 0; i--) {
            ItemStack armorItem = armorItems[i];

            if (!armorItem.isEmpty()) {
                // Draw box background
                int armorSpacing = (armorItems.length - 1 - i) * (boxSize + spacing);

                drawTexture(context, xOffset + armorSpacing, yOffset, 0, 0, boxSize, boxSize, 256, 256);

                // Draw armor icon
                context.drawItem(armorItem, xOffset + armorSpacing + 2, yOffset + 2);

                // Draw the durability bar
                int durability = armorItem.getMaxDamage() - armorItem.getDamage();
                int durabilityWidth = (int) ((durability / (float) armorItem.getMaxDamage()) * boxSize);
                fill(context, xOffset + armorSpacing, yOffset + boxSize - 4, xOffset + armorSpacing + durabilityWidth, yOffset + boxSize, 0xFF00FF00); // Green for durability
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