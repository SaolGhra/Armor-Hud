package com.saolghra.armor_hud.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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

    protected SimpleConfigScreen(Screen parent) {
        super(Text.literal("Armor HUD Configuration"));
        this.parent = parent;
        this.config = ArmorHudConfig.getInstance();
    }

    @Override
    protected void init() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int centerX = this.width / 2 - buttonWidth / 2;
        int textFieldWidth = 60;
        int currentY = 45;  // Start lower to accommodate title

        // Title is rendered separately in render method at Y=15

        // Visibility toggle
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("HUD Visible: " + config.isVisible()),
                        button -> {
                            config.setVisible(!config.isVisible());
                            button.setMessage(Text.literal("HUD Visible: " + config.isVisible()));
                        })
                .dimensions(centerX, currentY, buttonWidth, buttonHeight)
                .build());
        currentY += 35;  // Larger gap after title section

        // X Offset label rendered in render method
        currentY += 15;  // Space for label

        // X Offset text field and buttons
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
        currentY += 35;  // Larger gap between sections

        // Y Offset label rendered in render method
        currentY += 15;  // Space for label

        // Y Offset text field and buttons
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
        currentY += 35;  // Larger gap between sections

        // Toggle exclamation marks
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Show Exclamation Marks: " + config.isShowExclamationMarks()),
                        button -> {
                            config.setShowExclamationMarks(!config.isShowExclamationMarks());
                            button.setMessage(Text.literal("Show Exclamation Marks: " + config.isShowExclamationMarks()));
                        })
                .dimensions(centerX, currentY, buttonWidth, buttonHeight)
                .build());
        currentY += 35;

        // Reset button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Reset to Defaults"),
                        button -> {
                            config.resetToDefaults();
                            xOffsetField.setText(String.valueOf(config.getXOffset()));
                            yOffsetField.setText(String.valueOf(config.getYOffset()));
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // No background rendering for transparency
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("X Offset"), this.width / 2, 80, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Y Offset"), this.width / 2, 130, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}