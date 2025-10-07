package com.saolghra.armor_hud.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.gui.Click;

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
    private int initialConfigX = 0;
    private int initialConfigY = 0;
    private double dragDeltaX = 0;
    private double dragDeltaY = 0;

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
    public boolean mouseClicked(Click click, boolean isDoubleClick) {
        if (isInteractiveMode && click.button() == 0) {
            // Check if click is within the armor HUD area
            int hudX = getHudX();
            int hudY = getHudY();
            int totalWidth = getTotalHudWidth();
            int hudHeight = config.getBoxSize();

            if (click.x() >= hudX && click.x() <= hudX + totalWidth &&
                    click.y() >= hudY && click.y() <= hudY + hudHeight) {
                isDragging = true;
                initialConfigX = config.getXOffset();
                initialConfigY = config.getYOffset();
                dragDeltaX = 0;
                dragDeltaY = 0;
                return true;
            }
        }
        return super.mouseClicked(click, isDoubleClick);
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
            dragDeltaX += deltaX;
            dragDeltaY += deltaY;

            config.setXOffset(initialConfigX + (int) dragDeltaX);
            config.setYOffset(initialConfigY + (int) dragDeltaY);

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

    private void renderPreviewHud(DrawContext context) {
        if (!isInteractiveMode) return;

        // Push matrices and translate to a higher z-level to render above blur
        // context.getMatrices().push();
        // context.getMatrices().translate(0, 0, 1000); // High z-value to render on top

        int boxSize = config.getBoxSize();
        int spacing = config.getSpacing();
        int xOffset = getHudX();
        int yOffset = getHudY();

        // Draw armor boxes and icons first
        for (int i = previewArmor.length - 1; i >= 0; i--) {
            ItemStack armorItem = previewArmor[i];
            int armorSpacing = (previewArmor.length - 1 - i) * (boxSize + spacing);

            // Draw box background - semi-transparent black background
            context.fill(xOffset + armorSpacing, yOffset,
                    xOffset + armorSpacing + boxSize, yOffset + boxSize,
                    0x80000000);

            // Draw armor icon
            context.drawItem(armorItem,
                    xOffset + armorSpacing + (boxSize - 16) / 2,
                    yOffset + (boxSize - 16) / 2);
        }

        // Translate to even higher z-level for durability bars and exclamation marks
        // context.getMatrices().translate(0, 0, 200);

        // Draw durability bars and exclamation marks on top
        for (int i = previewArmor.length - 1; i >= 0; i--) {
            ItemStack armorItem = previewArmor[i];
            int armorSpacing = (previewArmor.length - 1 - i) * (boxSize + spacing);

            // Draw durability bar (simplified version)
            if (armorItem.getMaxDamage() > 0) {
                int damage = armorItem.getDamage();
                int maxDamage = armorItem.getMaxDamage();
                float durabilityRatio = ((maxDamage - damage) / (float) maxDamage);

                int barWidth = 13;
                int barX = xOffset + armorSpacing + (boxSize - barWidth) / 2 + 1;
                int barY = yOffset + boxSize - 6;
                int barHeight = 2;
                int remainingWidth = (int) Math.round(durabilityRatio * 13);

                // Simple red to green color based on durability
                int barColor = durabilityRatio > 0.5f ? 0xFF00FF00 : 0xFFFF0000;

                context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
                context.fill(barX, barY, barX + remainingWidth, barY + barHeight, barColor);
            }

            // Draw exclamation mark if enabled and durability is low
            if (config.isShowExclamationMarks() && armorItem.getMaxDamage() > 0) {
                int damage = armorItem.getDamage();
                int maxDamage = armorItem.getMaxDamage();
                float durabilityRatio = ((maxDamage - damage) / (float) maxDamage);

                if (durabilityRatio < config.getDurabilityWarningThreshold()) {
                    // Simple exclamation mark using text
                    context.drawTextWithShadow(this.textRenderer, "!",
                            xOffset + armorSpacing - 1,
                            yOffset - 2,
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