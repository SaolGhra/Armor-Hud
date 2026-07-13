package com.saolghra.armor_hud.client;

import com.saolghra.armor_hud.ArmorHudMath;
import com.saolghra.armor_hud.config.ArmorHudConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.5
import net.minecraft.client.renderer.RenderType;

/**
 * Renders the armor HUD (four armor slots with durability bars/points and a low-durability warning)
 * near the hotbar. This class is loader-agnostic: each loader module registers its native HUD hook
 * and calls {@link #render(GuiGraphics)}.
 *
 * <p>Version-sensitive Minecraft API is confined to this file so Stonecutter guards can be added
 * here as the {@code blit}/render APIs change across Minecraft versions.
 */
public class ArmorHudOverlay {
    private final ArmorHudConfig config = ArmorHudConfig.getInstance();

    private static final ResourceLocation HOTBAR_OFFHAND_LEFT =
            id("minecraft:textures/gui/sprites/hud/hotbar_offhand_left.png");
    private static final ResourceLocation EXCLAMATION_MARKS_TEXTURE =
            id("armor_hud:textures/gui/exclamation_marks_flash.png");

    /** {@code ResourceLocation.parse} was added in 1.21; older versions use the constructor. */
    private static ResourceLocation id(String s) {
        //? if >=1.21 {
        return ResourceLocation.parse(s);
        //?} else {
        /*return new ResourceLocation(s);*/
        //?}
    }

    // hotbar_offhand_left.png is 29x24; we trim 6px off the right so the slot reads square-ish.
    private static final int SPRITE_TEX_WIDTH = 29;
    private static final int SPRITE_TEX_HEIGHT = 24;
    private static final int SPRITE_DRAW_WIDTH = 23;
    private static final int SPRITE_DRAW_HEIGHT = 24;

    public void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();

        if (!config.isVisible() || isHudHidden(client) || client.player == null || client.level == null) {
            return;
        }

        ItemStack[] armorItems = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armorItems[i] = client.player.getInventory().getItem(36 + i);
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        boolean vertical = config.isVertical();
        int boxSize = config.getBoxSize();
        int spacing = config.getSpacing();
        int slotStride = boxSize + spacing;
        int xOffset = screenWidth / 2 + config.getXOffset();
        int yOffset = screenHeight + config.getYOffset();

        // Pass 1: slot backgrounds + armor icons (boots -> helmet).
        for (int slot = 0; slot < armorItems.length; slot++) {
            ItemStack armorItem = armorItems[slot];
            if (armorItem.isEmpty()) continue;

            int drawX = slotX(vertical, xOffset, slot, armorItems.length, slotStride);
            int drawY = slotY(vertical, yOffset, boxSize, slot, slotStride);

            drawSlotBackground(graphics, drawX, drawY, boxSize, boxSize);
            graphics.renderItem(armorItem, drawX + (boxSize - 16) / 2, drawY + (boxSize - 16) / 2);
        }

        // Pass 2: durability (bar or numeric) + low-durability warning.
        for (int slot = 0; slot < armorItems.length; slot++) {
            ItemStack armorItem = armorItems[slot];
            if (armorItem.isEmpty()) continue;

            int drawX = slotX(vertical, xOffset, slot, armorItems.length, slotStride);
            int drawY = slotY(vertical, yOffset, boxSize, slot, slotStride);

            if (config.isShowDurabilityPoints()) {
                drawDurabilityPoints(graphics, drawX, drawY, boxSize, armorItem);
            } else {
                drawDurabilityBar(graphics, drawX, drawY + boxSize - 6, boxSize, armorItem);
            }

            if (config.isShowExclamationMarks() && isDurabilityLow(armorItem)) {
                drawExclamationMark(graphics, drawX, drawY);
            }
        }
    }

    private static int slotX(boolean vertical, int xOffset, int slot, int count, int slotStride) {
        return vertical ? xOffset : xOffset + ((count - 1 - slot) * slotStride);
    }

    private static int slotY(boolean vertical, int yOffset, int boxSize, int slot, int slotStride) {
        return vertical ? (yOffset - boxSize - (slot * slotStride)) : yOffset;
    }

    private void drawDurabilityPoints(GuiGraphics graphics, int boxX, int boxY, int boxSize, ItemStack item) {
        int maxDamage = item.getMaxDamage();
        if (maxDamage <= 0) return;

        Minecraft client = Minecraft.getInstance();
        String display = String.valueOf(maxDamage - item.getDamageValue());
        int textWidth = client.font.width(display);
        int textX = boxX + boxSize - 2 - textWidth;
        int textY = boxY + 2;
        graphics.drawString(client.font, Component.literal(display), textX, textY, 0x88FFFFFF, true);
    }

    private boolean isDurabilityLow(ItemStack item) {
        return ArmorHudMath.isLowDurability(item.getMaxDamage(), item.getDamageValue(),
                config.getDurabilityWarningThreshold());
    }

    private void drawExclamationMark(GuiGraphics graphics, int boxX, int boxY) {
        float bobbingOffset = (float) Math.sin(System.currentTimeMillis() / 200.0) * 2;
        int iconSize = 11;
        int drawX = boxX - 1;
        int drawY = boxY - 2 + (int) bobbingOffset;
        blitTexture(graphics, EXCLAMATION_MARKS_TEXTURE, drawX, drawY, iconSize, iconSize, iconSize, iconSize);
    }

    private void drawSlotBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        int spriteX = x + (width - SPRITE_DRAW_WIDTH) / 2;
        int spriteY = y + (height - SPRITE_DRAW_HEIGHT) / 2;
        blitTexture(graphics, HOTBAR_OFFHAND_LEFT, spriteX, spriteY,
                SPRITE_DRAW_WIDTH, SPRITE_DRAW_HEIGHT, SPRITE_TEX_WIDTH, SPRITE_TEX_HEIGHT);
    }

    /**
     * Version-guarded texture blit. Minecraft 1.21.1 uses the {@code (ResourceLocation, x, y, u, v,
     * w, h, texW, texH)} overload; 1.21.5+ requires a {@code RenderPipeline} first argument. The
     * Stonecutter guard for that is introduced when 1.21.5+ is added to the matrix (Phase 3).
     */
    private void blitTexture(GuiGraphics graphics, ResourceLocation texture,
                             int x, int y, int width, int height, int texWidth, int texHeight) {
        //? if >=1.21.5 {
        graphics.blit(RenderType::guiTextured, texture, x, y, 0.0F, 0.0F, width, height, texWidth, texHeight);
        //?} else {
        /*graphics.blit(texture, x, y, 0.0F, 0.0F, width, height, texWidth, texHeight);*/
        //?}
    }

    private void drawDurabilityBar(GuiGraphics graphics, int x, int y, int width, ItemStack item) {
        int maxDamage = item.getMaxDamage();
        int damage = item.getDamageValue();
        if (maxDamage <= 0 || damage <= 0) return;

        int barWidth = 13;
        int barX = x + (width / 2) - (barWidth / 2);

        float durabilityRatio = ArmorHudMath.durabilityRatio(maxDamage, damage);
        int remainingWidth = Math.max(0, Math.min(barWidth, Math.round(durabilityRatio * barWidth)));
        int barColor = ArmorHudMath.durabilityColor(durabilityRatio);

        // Vanilla-style bar: 2px black background, 1px colored foreground.
        graphics.fill(barX, y, barX + barWidth, y + 2, 0xFF000000);
        if (remainingWidth > 0) {
            graphics.fill(barX, y, barX + remainingWidth, y + 1, 0xFF000000 | (barColor & 0x00FFFFFF));
        }
    }

    private boolean isHudHidden(Minecraft client) {
        return client == null || client.options == null || client.options.hideGui;
    }
}
