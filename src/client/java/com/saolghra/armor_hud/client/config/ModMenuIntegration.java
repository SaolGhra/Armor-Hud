package com.saolghra.armor_hud.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SimpleConfigScreen::new;
    }
}

class SimpleConfigScreen extends Screen {
    private final Screen parent;
    private final ArmorHudConfig config;

    // Interactive positioning variables
    private boolean isDragging = false;
    private boolean isInteractiveMode = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private int initialConfigX = 0;
    private int initialConfigY = 0;

    // Preview armor items for the interactive mode
    private final ItemStack[] previewArmor = {
            new ItemStack(Items.DIAMOND_HELMET),
            new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS),
            new ItemStack(Items.DIAMOND_BOOTS)
    };

    protected SimpleConfigScreen(Screen parent) {
        super(Text.literal("Armor HUD Configuration"));
        this.parent = parent;
        this.config = ArmorHudConfig.getInstance();

        // Make preview armor damaged for better visualization
        for (ItemStack armor : previewArmor) {
            if (armor.getMaxDamage() > 0) {
                armor.setDamage((int)(armor.getMaxDamage() * 0.7)); // 30% durability left
            }
        }
    }

    @Override
    protected void init() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int centerX = this.width / 2 - buttonWidth / 2;
        int currentY = 45;

        // Visibility toggle
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("HUD Visible: " + config.isVisible()),
                        button -> {
                            config.setVisible(!config.isVisible());
                            button.setMessage(Text.literal("HUD Visible: " + config.isVisible()));
                        })
                .dimensions(centerX, currentY, buttonWidth, buttonHeight)
                .build());
        currentY += 30;

        // Interactive positioning mode toggle
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal(isInteractiveMode ? "Exit Interactive Mode" : "Interactive Positioning"),
                        button -> {
                            isInteractiveMode = !isInteractiveMode;
                            isDragging = false;
                            button.setMessage(Text.literal(isInteractiveMode ? "Exit Interactive Mode" : "Interactive Positioning"));
                        })
                .dimensions(centerX, currentY, buttonWidth, buttonHeight)
                .build());
        currentY += 35;

        // Toggle exclamation marks
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Show Exclamation Marks: " + config.isShowExclamationMarks()),
                        button -> {
                            config.setShowExclamationMarks(!config.isShowExclamationMarks());
                            button.setMessage(Text.literal("Show Exclamation Marks: " + config.isShowExclamationMarks()));
                        })
                .dimensions(centerX, currentY, buttonWidth, buttonHeight)
                .build());
        currentY += 30;

    // Toggle vertical layout
    this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Vertical Layout: " + config.isVertical()),
            button -> {
                config.setVertical(!config.isVertical());
                button.setMessage(Text.literal("Vertical Layout: " + config.isVertical()));
            })
        .dimensions(centerX, currentY, buttonWidth, buttonHeight)
        .build());
    currentY += 30;

    // Toggle durability points
    this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Show Durability Points: " + config.isShowDurabilityPoints()),
            button -> {
                config.setShowDurabilityPoints(!config.isShowDurabilityPoints());
                button.setMessage(Text.literal("Show Durability Points: " + config.isShowDurabilityPoints()));
            })
        .dimensions(centerX, currentY, buttonWidth, buttonHeight)
        .build());
    currentY += 30;

        // Reset button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Reset to Defaults"),
                        button -> config.resetToDefaults())
                .dimensions(centerX, currentY, buttonWidth, buttonHeight)
                .build());

        // Done button at the bottom
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Done"),
                        button -> {
                            assert this.client != null;
                            this.client.setScreen(this.parent);
                        })
                .dimensions(centerX, this.height - 40, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (isInteractiveMode && click.button() == 0) { // Left click
            // Check if click is within the armor HUD area
            int hudX = getHudX();
            int hudY = getHudY();
            if (config.isVertical()) {
                int totalHeight = getTotalHudHeight();
                // Vertical HUD occupies boxSize width and totalHeight tall, ending at hudY
                if (mouseX >= hudX && mouseX <= hudX + config.getBoxSize() &&
                        mouseY >= hudY - totalHeight && mouseY <= hudY) {
                    isDragging = true;
                    dragStartX = (int) mouseX;
                    dragStartY = (int) mouseY;
                    initialConfigX = config.getXOffset();
                    initialConfigY = config.getYOffset();
                    return true;
                }
            } else {
                int totalWidth = getTotalHudWidth();
                int hudHeight = config.getBoxSize();

                if (mouseX >= hudX && mouseX <= hudX + totalWidth &&
                        mouseY >= hudY && mouseY <= hudY + hudHeight) {
                isDragging = true;
                dragStartX = (int) mouseX;
                dragStartY = (int) mouseY;
                initialConfigX = config.getXOffset();
                initialConfigY = config.getYOffset();
                return true;
            }
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (isDragging && click.button() == 0) {
            isDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (isDragging && isInteractiveMode) {
            int deltaXInt = (int) click.x() - dragStartX;
            int deltaYInt = (int) click.y() - dragStartY;

            config.setXOffset(initialConfigX + deltaXInt);
            config.setYOffset(initialConfigY + deltaYInt);

            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    private int getHudX() {
        return this.width / 2 + config.getXOffset();
    }

    private int getHudY() {
        return this.height + config.getYOffset();
    }

    private int getTotalHudWidth() {
        return 4 * config.getBoxSize() + 3 * config.getSpacing();
    }

    private int getTotalHudHeight() {
        return 4 * config.getBoxSize() + 3 * config.getSpacing();
    }

    private void renderPreviewHud(DrawContext context) {
        if (!isInteractiveMode) return;

        // Push matrices and translate to a higher z-level to render above blur
        // context.getMatrices().push();
        // context.getMatrices().translate(0, 0, 1000); // High z-value to render on top

        int boxSize = config.getBoxSize();
        int spacing = config.getSpacing();
        int xOffset = getHudX();
        int yOffset = getHudY();

    // Draw armor boxes and icons first (iterate slots 0..3 where slot 0=boots, 3=helmet)
    for (int slot = 0; slot < previewArmor.length; slot++) {
        ItemStack armorItem = previewArmor[previewArmor.length - 1 - slot]; // map slot->preview index
        int armorSpacing = slot * (boxSize + spacing);

        int drawX = config.isVertical() ? xOffset : xOffset + ((previewArmor.length - 1 - slot) * (boxSize + spacing));
        int drawY = config.isVertical() ? (yOffset - boxSize - armorSpacing) : yOffset;

        // Draw box background - semi-transparent black background
        context.fill(drawX, drawY,
            drawX + boxSize, drawY + boxSize,
            0x80000000);

        // Draw armor icon
        context.drawItem(armorItem,
            drawX + (boxSize - 16) / 2,
            drawY + (boxSize - 16) / 2);
    }

        // Translate to even higher z-level for durability bars and exclamation marks
        // context.getMatrices().translate(0, 0, 200);

        // Draw durability bars and exclamation marks on top
        for (int slot = 0; slot < previewArmor.length; slot++) {
            ItemStack armorItem = previewArmor[previewArmor.length - 1 - slot];
            int armorSpacing = slot * (boxSize + spacing);

            int drawX = config.isVertical() ? xOffset : xOffset + ((previewArmor.length - 1 - slot) * (boxSize + spacing));
            int drawY = config.isVertical() ? (yOffset - boxSize - armorSpacing) : yOffset;

            // Draw durability bar or points
            if (armorItem.getMaxDamage() > 0) {
                int damage = armorItem.getDamage();
                int maxDamage = armorItem.getMaxDamage();
                float durabilityRatio = ((maxDamage - damage) / (float) maxDamage);

                if (config.isShowDurabilityPoints()) {
                    String numText = String.valueOf(maxDamage - damage);
                    int drawTextY = drawY + 2;
                    String compact = numText;
                    // Always show the full remaining number (no '+'). Use slightly lower opacity.
                    String display = compact;
                    int finalWidth = this.textRenderer.getWidth(display);
                    int finalX = drawX + boxSize - 2 - finalWidth;
                    context.drawTextWithShadow(this.textRenderer, Text.literal(display), finalX, drawTextY, 0x88FFFFFF);
                } else {
                    int barWidth = 13;
                    int barX = drawX + (boxSize - barWidth) / 2;
                    int barY = drawY + boxSize - 6;
                    durabilityRatio = Math.max(0.0f, Math.min(1.0f, durabilityRatio));
                    int remainingWidth = Math.max(0, Math.min(barWidth, Math.round(durabilityRatio * barWidth)));

                    // Match vanilla-style durability bar visuals.
                    // Compute hue-based color (green->red) without adding extra helpers.
                    float hueDegrees = durabilityRatio * 120.0f;
                    int argb = hsvToArgb(hueDegrees, 1.0f, 1.0f);

                    context.fill(barX, barY, barX + barWidth, barY + 2, 0xFF000000);
                    if (remainingWidth > 0) {
                        context.fill(barX, barY, barX + remainingWidth, barY + 1, 0xFF000000 | (argb & 0x00FFFFFF));
                    }
                }
            }

            // Draw exclamation mark if enabled and durability is low
            if (config.isShowExclamationMarks() && armorItem.getMaxDamage() > 0) {
                int damage = armorItem.getDamage();
                int maxDamage = armorItem.getMaxDamage();
                float durabilityRatio = ((maxDamage - damage) / (float) maxDamage);

                if (durabilityRatio < config.getDurabilityWarningThreshold()) {
                    // Simple exclamation mark using text
                    context.drawTextWithShadow(this.textRenderer, "!",
                            drawX - 1,
                            drawY - 2,
                            0xFFFFFF00);
                }
            }
        }

        // context.getMatrices().pop(); // Restore matrix state

        // // Draw drag hint (also at higher z-level)
        // context.getMatrices().push();
        // context.getMatrices().translate(0, 0, 1000);

        if (isDragging) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Dragging..."),
                    this.width / 2, 20, 0xFFFFFF);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Click and drag the armor HUD to position it"),
                    this.width / 2, 20, 0xFFFFFF);
        }

        // context.getMatrices().pop();
    }

    private static int hsvToArgb(float h, float s, float v) {
        h = (h % 360 + 360) % 360;

        float hh = h / 60.0f;
        int i = (int) hh % 6;

        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        int r = 0, g = 0, b = 0;
        switch (i) {
            case 0 -> { r = Math.round(v * 255); g = Math.round(t * 255); b = Math.round(p * 255); }
            case 1 -> { r = Math.round(q * 255); g = Math.round(v * 255); b = Math.round(p * 255); }
            case 2 -> { r = Math.round(p * 255); g = Math.round(v * 255); b = Math.round(t * 255); }
            case 3 -> { r = Math.round(p * 255); g = Math.round(q * 255); b = Math.round(v * 255); }
            case 4 -> { r = Math.round(t * 255); g = Math.round(p * 255); b = Math.round(v * 255); }
            case 5 -> { r = Math.round(v * 255); g = Math.round(p * 255); b = Math.round(q * 255); }
        }

        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Use lighter blur in interactive mode, normal blur otherwise
        int blurAlpha = isInteractiveMode ? 0x20000000 : 0x40000000;
        context.fill(0, 0, this.width, this.height, blurAlpha);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);

        // Render the preview HUD LAST to ensure it's on top
        renderPreviewHud(context);
    }
}