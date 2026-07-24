package com.fishingmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FishingModConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 156;
    private static final int BUTTON_WIDTH = 240;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final FishingModConfig config;
    private Button debugToggleButton;
    private Button teleportToggleButton;
    private Button targetInstanceButton;

    public FishingModConfigScreen(Screen parent, FishingModConfig config) {
        super(Component.literal("MCCI Tweaks - Automation"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int buttonLeft = centerX - BUTTON_WIDTH / 2;
        int buttonTop = centerY - 35;

        this.debugToggleButton = Button.builder(debugToggleText(), button -> {
            config.debugLogging = !config.debugLogging;
            updateButtonState();
        }).bounds(buttonLeft, buttonTop, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.debugToggleButton);

        this.teleportToggleButton = Button.builder(teleportToggleText(), button -> {
            config.teleportEnabled = !Boolean.TRUE.equals(config.teleportEnabled);
            updateButtonState();
        }).bounds(buttonLeft, buttonTop + BUTTON_HEIGHT + 10, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.teleportToggleButton);

        this.targetInstanceButton = Button.builder(targetInstanceText(), button -> {
            config.cycleTargetInstance();
            updateButtonState();
        }).bounds(buttonLeft, buttonTop + (BUTTON_HEIGHT + 10) * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.targetInstanceButton);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> saveAndClose())
                .bounds(buttonLeft, centerY + 59, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xCC0B1120, 0xCC05070D);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - 71;
        int panelRight = panelLeft + PANEL_WIDTH;
        int panelBottom = panelTop + PANEL_HEIGHT;

        graphics.fill(panelLeft - 2, panelTop - 2, panelRight + 2, panelBottom + 2, 0xFF1C2235);
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF2B3554);
        graphics.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelTop + 18, 0xFF394874);
        graphics.fill(panelLeft + 1, panelBottom - 2, panelRight - 1, panelBottom - 1, 0xFF182034);
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    private void updateButtonState() {
        debugToggleButton.setMessage(debugToggleText());
        teleportToggleButton.setMessage(teleportToggleText());
        targetInstanceButton.setMessage(targetInstanceText());
    }

    private void saveAndClose() {
        FishingModConfigStore.saveConfig(config);
        FishingModClient.reloadConfigFromDisk();
        Minecraft.getInstance().setScreen(parent);
    }

    private Component debugToggleText() {
        return Component.literal("Debug Logging: " + onOff(config.debugLogging));
    }

    private Component teleportToggleText() {
        return Component.literal("Teleport Automation: " + onOff(Boolean.TRUE.equals(config.teleportEnabled)));
    }

    private Component targetInstanceText() {
        return Component.literal("Target Fishtance Instance: " + config.targetInstance());
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
