package com.saolghra.armor_hud.client.config;

import com.saolghra.armor_hud.ArmorHudMath;
import com.saolghra.armor_hud.config.ArmorHudConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9
/*import net.minecraft.client.input.MouseButtonEvent;*/
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Loader-agnostic configuration screen for Armor HUD. Fabric reaches it through ModMenu
 * ({@code ModMenuIntegration}); NeoForge registers it as its {@code IConfigScreenFactory}. Because
 * both loaders share this one class, the config UI stays identical across them.
 *
 * <p>Rendering uses the classic {@code GuiGraphics}/{@code render} API, which is stable across the
 * whole 1.20&ndash;1.21.11 matrix. The only version-sensitive part is mouse input: 1.21.9 replaced the
 * {@code (double,double,int)} overloads with {@code MouseButtonEvent}, so the drag logic lives in
 * version-agnostic helpers and only the thin overrides are guarded. Guard the render path here too
 * when the 26.x {@code extractRenderState} split lands.
 */
public class ArmorHudConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    private static final int SLOT_COUNT = 4;
    private static final int ICON_SIZE = 16;

    private final Screen parent;
    private final ArmorHudConfig config;

    private boolean interactiveMode = false;
    private boolean dragging = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private int initialConfigX = 0;
    private int initialConfigY = 0;

    private final ItemStack[] previewArmor = new ItemStack[SLOT_COUNT];

    public ArmorHudConfigScreen(Screen parent) {
        super(Component.translatable("armor_hud.config.title"));
        this.parent = parent;
        this.config = ArmorHudConfig.getInstance();
    }

    @Override
    protected void init() {
        initializePreviewArmor();

        int left = (this.width - BUTTON_WIDTH) / 2;
        int y = 44;

        addToggle(left, y, "armor_hud.config.visible", config::isVisible, config::setVisible);
        y += ROW_SPACING;

        addRenderableWidget(Button.builder(interactiveModeLabel(), button -> {
            interactiveMode = !interactiveMode;
            dragging = false;
            button.setMessage(interactiveModeLabel());
        }).bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        y += ROW_SPACING + 4;

        addToggle(left, y, "armor_hud.config.exclamation_marks",
                config::isShowExclamationMarks, config::setShowExclamationMarks);
        y += ROW_SPACING;

        addToggle(left, y, "armor_hud.config.vertical", config::isVertical, config::setVertical);
        y += ROW_SPACING;

        addToggle(left, y, "armor_hud.config.durability_points",
                config::isShowDurabilityPoints, config::setShowDurabilityPoints);
        y += ROW_SPACING + 8;

        addRenderableWidget(Button.builder(
                Component.translatable("armor_hud.config.reset"),
                button -> config.resetToDefaults())
            .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> onClose())
            .bounds(left, this.height - 34, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /** Adds a labelled boolean toggle whose label reflects the current value. */
    private void addToggle(int x, int y, String key, java.util.function.BooleanSupplier getter,
                           java.util.function.Consumer<Boolean> setter) {
        Button button = Button.builder(toggleLabel(key, getter.getAsBoolean()), b -> {
            boolean next = !getter.getAsBoolean();
            setter.accept(next);
            b.setMessage(toggleLabel(key, next));
        }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(button);
    }

    private static Component toggleLabel(String key, boolean value) {
        Component state = Component.translatable(value ? "gui.yes" : "gui.no");
        return Component.translatable(key).append(": ").append(state);
    }

    private Component interactiveModeLabel() {
        return Component.translatable(interactiveMode
                ? "armor_hud.config.interactive.exit"
                : "armor_hud.config.interactive.enter");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Dim the background ourselves (renderBackground's signature moved across versions).
        graphics.fill(0, 0, this.width, this.height, interactiveMode ? 0x20000000 : 0xC0101010);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("armor_hud.config.position", config.getXOffset(), config.getYOffset()),
                this.width / 2, 20, 0xFFB0B0B0);
        if (interactiveMode) {
            Component hint = Component.translatable(dragging
                    ? "armor_hud.config.interactive.dragging"
                    : "armor_hud.config.interactive.hint");
            graphics.drawCenteredString(this.font, hint, this.width / 2, 30, 0xFFFFFF00);
        }

        super.render(graphics, mouseX, mouseY, delta);

        renderPreviewHud(graphics);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    // --- Drag handling -------------------------------------------------------------------------
    // The Screen mouse API changed in 1.21.9: the (double,double,int) overloads became
    // MouseButtonEvent-based. The actual drag logic lives in these three version-agnostic helpers,
    // so only the thin overrides below need a Stonecutter guard. Each returns true if it consumed
    // the event.

    private boolean beginDrag(double mouseX, double mouseY, int button) {
        if (interactiveMode && button == 0 && isWithinHud(mouseX, mouseY)) {
            dragging = true;
            dragStartX = (int) mouseX;
            dragStartY = (int) mouseY;
            initialConfigX = config.getXOffset();
            initialConfigY = config.getYOffset();
            return true;
        }
        return false;
    }

    private boolean endDrag(int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return false;
    }

    private boolean moveDrag(double mouseX, double mouseY) {
        if (dragging && interactiveMode) {
            config.setXOffset(initialConfigX + (int) mouseX - dragStartX);
            config.setYOffset(initialConfigY + (int) mouseY - dragStartY);
            return true;
        }
        return false;
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return beginDrag(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return endDrag(event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return moveDrag(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return beginDrag(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return endDrag(button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return moveDrag(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    //?}

    private int hudOriginX() {
        return this.width / 2 + config.getXOffset();
    }

    private int hudOriginY() {
        return this.height + config.getYOffset();
    }

    private boolean isWithinHud(double mouseX, double mouseY) {
        int box = config.getBoxSize();
        int span = SLOT_COUNT * box + (SLOT_COUNT - 1) * config.getSpacing();
        int x = hudOriginX();
        int y = hudOriginY();
        if (config.isVertical()) {
            return mouseX >= x && mouseX <= x + box && mouseY >= y - span && mouseY <= y;
        }
        return mouseX >= x && mouseX <= x + span && mouseY >= y && mouseY <= y + box;
    }

    /** Draws a live preview of the HUD using the same layout maths as the real overlay. */
    private void renderPreviewHud(GuiGraphics graphics) {
        if (!interactiveMode) {
            return;
        }

        boolean vertical = config.isVertical();
        int box = config.getBoxSize();
        int stride = box + config.getSpacing();
        int originX = hudOriginX();
        int originY = hudOriginY();

        for (int slot = 0; slot < previewArmor.length; slot++) {
            ItemStack item = previewArmor[slot];
            int drawX = ArmorHudMath.slotX(vertical, originX, slot, previewArmor.length, stride);
            int drawY = ArmorHudMath.slotY(vertical, originY, box, slot, stride);

            graphics.fill(drawX, drawY, drawX + box, drawY + box, 0x80000000);
            graphics.renderItem(item, drawX + (box - ICON_SIZE) / 2, drawY + (box - ICON_SIZE) / 2);

            int maxDamage = item.getMaxDamage();
            if (maxDamage <= 0) {
                continue;
            }
            int damage = item.getDamageValue();
            float ratio = ArmorHudMath.durabilityRatio(maxDamage, damage);

            if (config.isShowDurabilityPoints()) {
                String display = String.valueOf(maxDamage - damage);
                int textX = drawX + box - 2 - this.font.width(display);
                graphics.drawString(this.font, Component.literal(display), textX, drawY + 2, 0x88FFFFFF, true);
            } else {
                int barWidth = 13;
                int barX = drawX + (box / 2) - (barWidth / 2);
                int barY = drawY + box - 6;
                int remaining = Math.max(0, Math.min(barWidth, Math.round(ratio * barWidth)));
                int color = ArmorHudMath.durabilityColor(ratio);
                graphics.fill(barX, barY, barX + barWidth, barY + 2, 0xFF000000);
                if (remaining > 0) {
                    graphics.fill(barX, barY, barX + remaining, barY + 1, 0xFF000000 | (color & 0x00FFFFFF));
                }
            }

            if (config.isShowExclamationMarks()
                    && ArmorHudMath.isLowDurability(maxDamage, damage, config.getDurabilityWarningThreshold())) {
                graphics.drawString(this.font, Component.literal("!"), drawX - 1, drawY - 2, 0xFFFFFF00, true);
            }
        }
    }

    private void initializePreviewArmor() {
        for (int i = 0; i < previewArmor.length; i++) {
            ItemStack stack = switch (i) {
                case 0 -> new ItemStack(Items.DIAMOND_BOOTS);
                case 1 -> new ItemStack(Items.DIAMOND_LEGGINGS);
                case 2 -> new ItemStack(Items.DIAMOND_CHESTPLATE);
                default -> new ItemStack(Items.DIAMOND_HELMET);
            };
            if (stack.getMaxDamage() > 0) {
                stack.setDamageValue(Math.round(stack.getMaxDamage() * 0.7f));
            }
            previewArmor[i] = stack;
        }
    }
}
