package com.saolghra.armor_hud.client;

import com.saolghra.armor_hud.ArmorHudMath;
import com.saolghra.armor_hud.config.ArmorHudConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
// 1.21.11 renamed ResourceLocation -> Identifier (same package) as part of Mojang's 26.x
// unobfuscation prep. Only the name changed; `parse` still exists.
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import net.minecraft.world.item.ItemStack;
//? if >=1.21.6 {
/*import net.minecraft.client.renderer.RenderPipelines;
*///?} elif >=1.21.2 {
import net.minecraft.client.renderer.RenderType;
//?}

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

    /**
     * Slot background. The vanilla GUI sprite {@code hud/hotbar_offhand_left} only exists from
     * **1.20.5** (the GUI sprite-atlas rework); older versions still ship the single
     * {@code gui/widgets.png} atlas, so blitting the sprite path there renders a missing texture.
     * {@code <1.20.5} therefore falls back to our own bundled 24x24 slot texture.
     */
    //? if >=1.21.11 {
    /*private static final Identifier SLOT_BACKGROUND =
            id("minecraft:textures/gui/sprites/hud/hotbar_offhand_left.png");
    private static final Identifier EXCLAMATION_MARKS_TEXTURE =
            id("armor_hud:textures/gui/exclamation_marks_flash.png");
    *///?} elif >=1.20.5 {
    private static final ResourceLocation SLOT_BACKGROUND =
            id("minecraft:textures/gui/sprites/hud/hotbar_offhand_left.png");
    private static final ResourceLocation EXCLAMATION_MARKS_TEXTURE =
            id("armor_hud:textures/gui/exclamation_marks_flash.png");
    //?} else {
    /*private static final ResourceLocation SLOT_BACKGROUND =
            id("armor_hud:textures/gui/hotbar_texture.png");
    private static final ResourceLocation EXCLAMATION_MARKS_TEXTURE =
            id("armor_hud:textures/gui/exclamation_marks_flash.png");
    *///?}

    /**
     * Builds a texture id. {@code parse} was added in 1.21 (older versions use the constructor), and
     * the type itself was renamed {@code ResourceLocation} -> {@code Identifier} in 1.21.11.
     */
    //? if >=1.21.11 {
    /*private static Identifier id(String s) {
        return Identifier.parse(s);
    }
    *///?} else {
    private static ResourceLocation id(String s) {
        //? if >=1.21 {
        return ResourceLocation.parse(s);
        //?} else {
        /*return new ResourceLocation(s);*/
        //?}
    }
    //?}

    //? if >=1.20.5 {
    // hotbar_offhand_left.png is 29x24; we trim 6px off the right so the slot reads square-ish.
    private static final int SPRITE_TEX_WIDTH = 29;
    private static final int SPRITE_TEX_HEIGHT = 24;
    private static final int SPRITE_DRAW_WIDTH = 23;
    private static final int SPRITE_DRAW_HEIGHT = 24;
    //?} else {
    /*// The bundled fallback is already a square 24x24 slot; draw it whole.
    private static final int SPRITE_TEX_WIDTH = 24;
    private static final int SPRITE_TEX_HEIGHT = 24;
    private static final int SPRITE_DRAW_WIDTH = 24;
    private static final int SPRITE_DRAW_HEIGHT = 24;
    *///?}

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

            int drawX = ArmorHudMath.slotX(vertical, xOffset, slot, armorItems.length, slotStride);
            int drawY = ArmorHudMath.slotY(vertical, yOffset, boxSize, slot, slotStride);

            drawSlotBackground(graphics, drawX, drawY, boxSize, boxSize);
            graphics.renderItem(armorItem, drawX + (boxSize - 16) / 2, drawY + (boxSize - 16) / 2);
        }

        // Pass 2: durability (bar or numeric) + low-durability warning.
        for (int slot = 0; slot < armorItems.length; slot++) {
            ItemStack armorItem = armorItems[slot];
            if (armorItem.isEmpty()) continue;

            int drawX = ArmorHudMath.slotX(vertical, xOffset, slot, armorItems.length, slotStride);
            int drawY = ArmorHudMath.slotY(vertical, yOffset, boxSize, slot, slotStride);

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
        blitTexture(graphics, SLOT_BACKGROUND, spriteX, spriteY,
                SPRITE_DRAW_WIDTH, SPRITE_DRAW_HEIGHT, SPRITE_TEX_WIDTH, SPRITE_TEX_HEIGHT);
    }

    /**
     * Version-guarded texture blit. The first argument of {@code GuiGraphics.blit} changed twice:
     * <ul>
     *   <li>{@code <=1.21.1} — no extra argument, just the {@link ResourceLocation}.</li>
     *   <li>{@code 1.21.2–1.21.5} — a {@code Function<ResourceLocation, RenderType>}.</li>
     *   <li>{@code >=1.21.6} — a {@code RenderPipeline} ({@code RenderPipelines.GUI_TEXTURED}).</li>
     * </ul>
     */
    //? if >=1.21.11 {
    /*private void blitTexture(GuiGraphics graphics, Identifier texture,
                             int x, int y, int width, int height, int texWidth, int texHeight) {
    *///?} else {
    private void blitTexture(GuiGraphics graphics, ResourceLocation texture,
                             int x, int y, int width, int height, int texWidth, int texHeight) {
    //?}
        //? if >=1.21.6 {
        /*graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height, texWidth, texHeight);
        *///?} elif >=1.21.2 {
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
