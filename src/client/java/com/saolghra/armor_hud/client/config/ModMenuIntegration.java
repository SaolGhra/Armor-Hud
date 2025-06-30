package com.saolghra.armor_hud.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
    private TextFieldWidget xOffsetField;
    private TextFieldWidget yOffsetField;

    // Interactive positioning variables
    private boolean isDragging = false;
    private boolean isInteractiveMode = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private int initialConfigX = 0;
    private int initialConfigY = 0;

    // Label positions
    private int xOffsetLabelY = 0;
    private int yOffsetLabelY = 0;

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
        int textFieldWidth = 60;
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

        // Store Y positions for labels
        int xOffsetLabelY = 0;
        int yOffsetLabelY = 0;

        // Only show text fields and buttons when not in interactive mode
        if (!isInteractiveMode) {
            // X Offset section
            xOffsetLabelY = currentY; // Store the Y position for the label
            currentY += 15; // Space for the label

            xOffsetField = new TextFieldWidget(this.textRenderer, centerX, currentY, textFieldWidth, buttonHeight, Text.literal(""));
            xOffsetField.setText(String.valueOf(config.getXOffset()));
            xOffsetField.setChangedListener(text -> {
                try {
                    int value = Integer.parseInt(text);
                    config.setXOffset(value);
                } catch (NumberFormatException ignored) {}
            });
            this.addDrawableChild(xOffsetField);

            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal("-10"),
                            button -> {
                                config.setXOffset(config.getXOffset() - 10);
                                xOffsetField.setText(String.valueOf(config.getXOffset()));
                            })
                    .dimensions(centerX - 70, currentY, 50, buttonHeight)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal("+10"),
                            button -> {
                                config.setXOffset(config.getXOffset() + 10);
                                xOffsetField.setText(String.valueOf(config.getXOffset()));
                            })
                    .dimensions(centerX + textFieldWidth + 20, currentY, 50, buttonHeight)
                    .build());
            currentY += 35;

            // Y Offset section
            yOffsetLabelY = currentY; // Store the Y position for the label
            currentY += 15; // Space for the label

            yOffsetField = new TextFieldWidget(this.textRenderer, centerX, currentY, textFieldWidth, buttonHeight, Text.literal(""));
            yOffsetField.setText(String.valueOf(config.getYOffset()));
            yOffsetField.setChangedListener(text -> {
                try {
                    int value = Integer.parseInt(text);
                    config.setYOffset(value);
                } catch (NumberFormatException ignored) {}
            });
            this.addDrawableChild(yOffsetField);

            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal("-10"),
                            button -> {
                                config.setYOffset(config.getYOffset() - 10);
                                yOffsetField.setText(String.valueOf(config.getYOffset()));
                            })
                    .dimensions(centerX - 70, currentY, 50, buttonHeight)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal("+10"),
                            button -> {
                                config.setYOffset(config.getYOffset() + 10);
                                yOffsetField.setText(String.valueOf(config.getYOffset()));
                            })
                    .dimensions(centerX + textFieldWidth + 20, currentY, 50, buttonHeight)
                    .build());
            currentY += 35;
        }

        // Store the label positions as instance variables so render() can use them
        this.xOffsetLabelY = xOffsetLabelY;
        this.yOffsetLabelY = yOffsetLabelY;

        // Rest of the buttons...
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
                        button -> {
                            config.resetToDefaults();
                            if (xOffsetField != null) xOffsetField.setText(String.valueOf(config.getXOffset()));
                            if (yOffsetField != null) yOffsetField.setText(String.valueOf(config.getYOffset()));
                        })
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInteractiveMode && button == 0) { // Left click
            // Check if click is within the armor HUD area
            int hudX = getHudX();
            int hudY = getHudY();
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging && button == 0) {
            isDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && isInteractiveMode) {
            int deltaXInt = (int) mouseX - dragStartX;
            int deltaYInt = (int) mouseY - dragStartY;

            config.setXOffset(initialConfigX + deltaXInt);
            config.setYOffset(initialConfigY + deltaYInt);

            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1000); // High z-value to render on top

        int boxSize = config.getBoxSize();
        int spacing = config.getSpacing();
        int xOffset = getHudX();
        int yOffset = getHudY();

        // Draw armor boxes and icons
        for (int i = previewArmor.length - 1; i >= 0; i--) {
            ItemStack armorItem = previewArmor[i];
            int armorSpacing = (previewArmor.length - 1 - i) * (boxSize + spacing);

            // Draw box background - you'll need to implement this or use a simple fill
            context.fill(xOffset + armorSpacing, yOffset,
                    xOffset + armorSpacing + boxSize, yOffset + boxSize,
                    0x80000000); // Semi-transparent black background

            // Draw armor icon
            context.drawItem(armorItem,
                    xOffset + armorSpacing + (boxSize - 16) / 2,
                    yOffset + (boxSize - 16) / 2);

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

        context.getMatrices().pop(); // Restore matrix state

        // Draw drag hint (also at higher z-level)
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1000);

        if (isDragging) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Dragging..."),
                    this.width / 2, 20, 0xFFFFFF);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Click and drag the armor HUD to position it"),
                    this.width / 2, 20, 0xFFFFFF);
        }

        context.getMatrices().pop();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Use lighter blur in interactive mode, normal blur otherwise
        int blurAlpha = isInteractiveMode ? 0x20000000 : 0x40000000;
        context.fill(0, 0, this.width, this.height, blurAlpha);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        // Only show X/Y offset labels when NOT in interactive mode and the fields exist
        if (!isInteractiveMode && xOffsetField != null && yOffsetField != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("X Offset"), this.width / 2, xOffsetLabelY, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Y Offset"), this.width / 2, yOffsetLabelY, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);

        // Render the preview HUD LAST to ensure it's on top
        renderPreviewHud(context);
    }
}