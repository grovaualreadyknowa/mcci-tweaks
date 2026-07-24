package dev.caden.mccitweaks;

import com.fishingmod.FishingModConfigScreen;
import dev.caden.mccitweaks.materialmarket.MaterialMarketConfigScreen;
import dev.caden.mccnoflash.MccNoFlashConfigScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class McciTweaksConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;

    public McciTweaksConfigScreen(Screen parent) {
        super(Component.literal("MCCI Tweaks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int buttonLeft = centerX - BUTTON_WIDTH / 2;
        int buttonTop = centerY - 40;

        this.addRenderableWidget(Button.builder(
                Component.literal("Fishtance Automation"),
                button -> this.minecraft.setScreen(new FishingModConfigScreen(
                    this,
                    McciTweaksConfigStore.get().automation
                )))
            .bounds(buttonLeft, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build());

        this.addRenderableWidget(Button.builder(
                Component.literal("No Flash"),
                button -> this.minecraft.setScreen(new MccNoFlashConfigScreen(this)))
            .bounds(buttonLeft, buttonTop + 30, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Material Market Inventory"),
                button -> this.minecraft.setScreen(new MaterialMarketConfigScreen(this)))
            .bounds(buttonLeft, buttonTop + 60, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
            .bounds(buttonLeft, buttonTop + 102, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xCC0B1120, 0xCC05070D);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int left = centerX - PANEL_WIDTH / 2;
        int top = centerY - 80;
        int right = left + PANEL_WIDTH;
        int bottom = centerY + 96;

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF1C2235);
        graphics.fill(left, top, right, bottom, 0xFF2B3554);
        graphics.fill(left + 1, top + 1, right - 1, top + 20, 0xFF394874);
        graphics.centeredText(this.font, this.title, centerX, top + 6, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
