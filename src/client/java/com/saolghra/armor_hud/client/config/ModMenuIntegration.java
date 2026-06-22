package com.saolghra.armor_hud.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ArmorHudConfigScreen::new;
    }

    private static final class ArmorHudConfigScreen extends Screen {
        private static final int BUTTON_WIDTH = 220;
        private static final int BUTTON_HEIGHT = 20;
        private static final int ROW_SPACING = 24;

        private final Screen parent;
        private final ArmorHudConfig config;
        private Button interactiveModeButton;

        private boolean isDragging = false;
        private boolean isInteractiveMode = false;
        private int dragStartX = 0;
        private int dragStartY = 0;
        private int initialConfigX = 0;
        private int initialConfigY = 0;

        private final ItemStack[] previewArmor = new ItemStack[] {
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY
        };

        private ArmorHudConfigScreen(Screen parent) {
            super(Component.literal("Armor HUD Configuration"));
            this.parent = parent;
            this.config = ArmorHudConfig.getInstance();

        }

        @Override
        protected void init() {
            initializePreviewArmor();

            int left = (this.width - BUTTON_WIDTH) / 2;
            int y = 42;

            this.addRenderableWidget(Button.builder(
                    Component.literal("HUD Visible: " + config.isVisible()),
                    button -> {
                        config.setVisible(!config.isVisible());
                        button.setMessage(Component.literal("HUD Visible: " + config.isVisible()));
                    })
                .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
            y += ROW_SPACING;

            this.interactiveModeButton = this.addRenderableWidget(Button.builder(
                    Component.literal(isInteractiveMode ? "Exit Interactive Mode" : "Interactive Positioning"),
                    button -> {
                        isInteractiveMode = !isInteractiveMode;
                        isDragging = false;
                        button.setMessage(Component.literal(isInteractiveMode ? "Exit Interactive Mode" : "Interactive Positioning"));
                    })
                .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
            y += ROW_SPACING + 4;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Show Exclamation Marks: " + config.isShowExclamationMarks()),
                    button -> {
                        config.setShowExclamationMarks(!config.isShowExclamationMarks());
                        button.setMessage(Component.literal("Show Exclamation Marks: " + config.isShowExclamationMarks()));
                    })
                .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
            y += ROW_SPACING;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Vertical Layout: " + config.isVertical()),
                    button -> {
                        config.setVertical(!config.isVertical());
                        button.setMessage(Component.literal("Vertical Layout: " + config.isVertical()));
                    })
                .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
            y += ROW_SPACING;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Show Durability Points: " + config.isShowDurabilityPoints()),
                    button -> {
                        config.setShowDurabilityPoints(!config.isShowDurabilityPoints());
                        button.setMessage(Component.literal("Show Durability Points: " + config.isShowDurabilityPoints()));
                    })
                .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
            y += ROW_SPACING + 8;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Reset to Defaults"),
                    button -> config.resetToDefaults())
                .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("Done"),
                    button -> closeToParent())
                .bounds(left, this.height - 34, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        }

        @Override
        public void onClose() {
            closeToParent();
        }

        private void closeToParent() {
            if (this.minecraft == null) {
                return;
            }
            this.minecraft.setScreen(this.parent);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (isInteractiveMode && event.button() == 0) {
                double mouseX = event.x();
                double mouseY = event.y();
                int hudX = getHudX();
                int hudY = getHudY();

                if (isMouseWithinHudBounds(mouseX, mouseY, hudX, hudY)) {
                    isDragging = true;
                    dragStartX = (int) mouseX;
                    dragStartY = (int) mouseY;
                    initialConfigX = config.getXOffset();
                    initialConfigY = config.getYOffset();
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (isDragging && event.button() == 0) {
                isDragging = false;
                return true;
            }
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (isDragging && isInteractiveMode) {
                int deltaXInt = (int) event.x() - dragStartX;
                int deltaYInt = (int) event.y() - dragStartY;

                config.setXOffset(initialConfigX + deltaXInt);
                config.setYOffset(initialConfigY + deltaYInt);
                return true;
            }
            return super.mouseDragged(event, dragX, dragY);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int blurAlpha = isInteractiveMode ? 0x20000000 : 0x40000000;
            graphics.fill(0, 0, this.width, this.height, blurAlpha);
            graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
            graphics.centeredText(this.font,
                Component.literal("X: " + config.getXOffset() + "  Y: " + config.getYOffset()),
                this.width / 2, 28, 0xB0B0B0);

            super.extractRenderState(graphics, mouseX, mouseY, delta);

            renderPreviewHud(graphics);
        }

        private boolean isMouseWithinHudBounds(double mouseX, double mouseY, int hudX, int hudY) {
            if (config.isVertical()) {
                int totalHeight = getTotalHudHeight();
                return mouseX >= hudX && mouseX <= hudX + config.getBoxSize()
                    && mouseY >= hudY - totalHeight && mouseY <= hudY;
            }

            int totalWidth = getTotalHudWidth();
            int hudHeight = config.getBoxSize();
            return mouseX >= hudX && mouseX <= hudX + totalWidth
                && mouseY >= hudY && mouseY <= hudY + hudHeight;
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

        private void renderPreviewHud(GuiGraphicsExtractor graphics) {
            if (!isInteractiveMode) {
                return;
            }

            initializePreviewArmor();

            int boxSize = config.getBoxSize();
            int spacing = config.getSpacing();
            int slotStride = boxSize + spacing;
            int xOffset = getHudX();
            int yOffset = getHudY();

            for (int slot = 0; slot < previewArmor.length; slot++) {
                ItemStack armorItem = previewArmor[previewArmor.length - 1 - slot];
                int armorSpacing = slot * slotStride;

                int drawX = config.isVertical() ? xOffset : xOffset + ((previewArmor.length - 1 - slot) * slotStride);
                int drawY = config.isVertical() ? (yOffset - boxSize - armorSpacing) : yOffset;

                graphics.fill(drawX, drawY, drawX + boxSize, drawY + boxSize, 0x80000000);
                graphics.item(armorItem, drawX + (boxSize - 16) / 2, drawY + (boxSize - 16) / 2);
            }

            for (int slot = 0; slot < previewArmor.length; slot++) {
                ItemStack armorItem = previewArmor[previewArmor.length - 1 - slot];
                int armorSpacing = slot * slotStride;

                int drawX = config.isVertical() ? xOffset : xOffset + ((previewArmor.length - 1 - slot) * slotStride);
                int drawY = config.isVertical() ? (yOffset - boxSize - armorSpacing) : yOffset;

                if (armorItem.getMaxDamage() > 0) {
                    int damage = armorItem.getDamageValue();
                    int maxDamage = armorItem.getMaxDamage();
                    float durabilityRatio = (maxDamage - damage) / (float) maxDamage;

                    if (config.isShowDurabilityPoints()) {
                        String display = String.valueOf(maxDamage - damage);
                        int finalWidth = this.font.width(display);
                        int finalX = drawX + boxSize - 2 - finalWidth;
                        int drawTextY = drawY + 2;
                        graphics.text(this.font, Component.literal(display), finalX, drawTextY, 0x88FFFFFF, true);
                    } else {
                        int barWidth = 13;
                        int barX = drawX + (boxSize / 2) - (barWidth / 2);
                        int barY = drawY + boxSize - 6;
                        durabilityRatio = Math.max(0.0f, Math.min(1.0f, durabilityRatio));
                        int remainingWidth = Math.max(0, Math.min(barWidth, Math.round(durabilityRatio * barWidth)));
                        int argb = hsvToArgb(durabilityRatio * 120.0f, 1.0f, 1.0f);

                        graphics.fill(barX, barY, barX + barWidth, barY + 2, 0xFF000000);
                        if (remainingWidth > 0) {
                            graphics.fill(barX, barY, barX + remainingWidth, barY + 1, 0xFF000000 | (argb & 0x00FFFFFF));
                        }
                    }

                    if (config.isShowExclamationMarks() && durabilityRatio < config.getDurabilityWarningThreshold()) {
                        graphics.text(this.font, Component.literal("!"), drawX - 1, drawY - 2, 0xFFFFFF00, true);
                    }
                }
            }

            Component hint = isDragging
                ? Component.literal("Dragging...")
                : Component.literal("Click and drag the armor HUD to position it");
            graphics.centeredText(this.font, hint, this.width / 2, 20, 0xFFFFFF);
        }

        private static int hsvToArgb(float h, float s, float v) {
            h = (h % 360 + 360) % 360;

            float hh = h / 60.0f;
            int i = (int) hh % 6;

            float f = hh - i;
            float p = v * (1 - s);
            float q = v * (1 - f * s);
            float t = v * (1 - (1 - f) * s);

            int r = 0;
            int g = 0;
            int b = 0;

            switch (i) {
                case 0: r = Math.round(v * 255); g = Math.round(t * 255); b = Math.round(p * 255); break;
                case 1: r = Math.round(q * 255); g = Math.round(v * 255); b = Math.round(p * 255); break;
                case 2: r = Math.round(p * 255); g = Math.round(v * 255); b = Math.round(t * 255); break;
                case 3: r = Math.round(p * 255); g = Math.round(q * 255); b = Math.round(v * 255); break;
                case 4: r = Math.round(t * 255); g = Math.round(p * 255); b = Math.round(v * 255); break;
                case 5: r = Math.round(v * 255); g = Math.round(p * 255); b = Math.round(q * 255); break;
            }

            return (255 << 24) | (r << 16) | (g << 8) | b;
        }

        private void initializePreviewArmor() {
            for (int i = 0; i < previewArmor.length; i++) {
                if (!previewArmor[i].isEmpty()) {
                    continue;
                }

                ItemStack stack = createPreviewStackBySlot(i);
                if (!stack.isEmpty() && stack.getMaxDamage() > 0) {
                    stack.setDamageValue((int) (stack.getMaxDamage() * 0.7f));
                }
                previewArmor[i] = stack;
            }
        }

        private ItemStack createPreviewStackBySlot(int slot) {
            try {
                return switch (slot) {
                    case 0 -> new ItemStack(Items.DIAMOND_HELMET);
                    case 1 -> new ItemStack(Items.DIAMOND_CHESTPLATE);
                    case 2 -> new ItemStack(Items.DIAMOND_LEGGINGS);
                    case 3 -> new ItemStack(Items.DIAMOND_BOOTS);
                    default -> ItemStack.EMPTY;
                };
            } catch (RuntimeException ignored) {
                return ItemStack.EMPTY;
            }
        }
    }
}
