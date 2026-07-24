package dev.caden.mccnoflash;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MccNoFlashConfigScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 320;
    private static final int MAX_CONTROL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 238;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final MccNoFlashState state;

    private boolean enabled;
    private FlashMode globalMode;
    private FlashMode journalMode;
    private FlashMode pocketMode;
    private FlashMode navigatorMode;
    private FlashMode anglrPanelMode;
    private FlashMode profileMode;
    private boolean debugLogging;
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;

    public MccNoFlashConfigScreen(Screen parent) {
        super(Component.literal("MCCI Tweaks - No Flash"));
        this.parent = parent;
        this.state = MccNoFlashState.get();

        MccNoFlashConfig config = this.state.config();
        config.ensureDefaults();
        this.enabled = config.enabled;
        this.globalMode = config.globalMode;
        this.journalMode = config.journalMode;
        this.pocketMode = config.pocketMode;
        this.navigatorMode = config.navigatorMode;
        this.anglrPanelMode = config.anglrPanelMode;
        this.profileMode = config.profileMode;
        this.debugLogging = config.debugLogging;
    }

    public static Screen create(Screen parent) {
        return new MccNoFlashConfigScreen(parent);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(MAX_PANEL_WIDTH, this.width - 16);
        int controlWidth = Math.min(MAX_CONTROL_WIDTH, panelWidth - 20);
        this.panelLeft = (this.width - panelWidth) / 2;
        this.panelTop = Math.max(1, (this.height - PANEL_HEIGHT) / 2);
        this.panelRight = this.panelLeft + panelWidth;
        this.panelBottom = this.panelTop + PANEL_HEIGHT;

        int left = (this.width - controlWidth) / 2;
        int y = this.panelTop + 27;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.enabled)
            .create(left, y, controlWidth, BUTTON_HEIGHT, Component.literal("Enabled"),
                (button, value) -> this.enabled = value));
        y += ROW_HEIGHT;

        this.addRenderableWidget(CycleButton.builder(FlashMode::getDisplayName, this.globalMode)
            .withValues(FlashMode.values())
            .create(left, y, controlWidth, BUTTON_HEIGHT, Component.literal("Global Flash Mode"),
                (button, value) -> this.globalMode = value));
        y += ROW_HEIGHT;

        this.addRenderableWidget(CycleButton.builder(FlashMode::getDisplayName, this.journalMode)
            .withValues(FlashMode.values())
            .create(left, y, controlWidth, BUTTON_HEIGHT, Component.literal("Journal Mode"),
                (button, value) -> this.journalMode = value));
        y += ROW_HEIGHT;

        this.addRenderableWidget(CycleButton.builder(FlashMode::getDisplayName, this.pocketMode)
            .withValues(FlashMode.values())
            .create(left, y, controlWidth, BUTTON_HEIGHT, Component.literal("Pocket Menu Mode"),
                (button, value) -> this.pocketMode = value));
        y += ROW_HEIGHT;

        this.addRenderableWidget(CycleButton.builder(FlashMode::getDisplayName, this.navigatorMode)
            .withValues(FlashMode.values())
            .create(left, y, controlWidth, BUTTON_HEIGHT, Component.literal("Navigator Mode"),
                (button, value) -> this.navigatorMode = value));
        y += ROW_HEIGHT;

        this.addRenderableWidget(CycleButton.builder(FlashMode::getDisplayName, this.anglrPanelMode)
            .withValues(FlashMode.values())
            .create(left, y, controlWidth, BUTTON_HEIGHT, Component.literal("A.N.G.L.R Panel Mode"),
                (button, value) -> this.anglrPanelMode = value));
        y += ROW_HEIGHT;

        this.addRenderableWidget(CycleButton.builder(FlashMode::getDisplayName, this.profileMode)
            .withValues(FlashMode.values())
            .create(left, y, controlWidth, BUTTON_HEIGHT, Component.literal("My Profile Mode"),
                (button, value) -> this.profileMode = value));
        y += ROW_HEIGHT;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.debugLogging)
            .create(left, y, controlWidth, BUTTON_HEIGHT, Component.literal("Debug Logging"),
                (button, value) -> this.debugLogging = value));

        int actionY = this.panelTop + 210;
        int actionWidth = (controlWidth - 10) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.applyAndClose())
            .bounds(left, actionY, actionWidth, BUTTON_HEIGHT)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
            .bounds(left + controlWidth - actionWidth, actionY, actionWidth, BUTTON_HEIGHT)
            .build());
    }

    private void applyAndClose() {
        MccNoFlashConfig config = this.state.config();
        config.enabled = this.enabled;
        config.globalMode = this.globalMode;
        config.journalMode = this.journalMode;
        config.pocketMode = this.pocketMode;
        config.navigatorMode = this.navigatorMode;
        config.anglrPanelMode = this.anglrPanelMode;
        config.profileMode = this.profileMode;
        config.debugLogging = this.debugLogging;
        config.ensureDefaults();
        this.state.saveConfig();
        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xCC0B1120, 0xCC05070D);
        graphics.fill(
            this.panelLeft - 2,
            this.panelTop - 2,
            this.panelRight + 2,
            this.panelBottom + 2,
            0xFF1C2235
        );
        graphics.fill(this.panelLeft, this.panelTop, this.panelRight, this.panelBottom, 0xFF2B3554);
        graphics.fill(
            this.panelLeft + 1,
            this.panelTop + 1,
            this.panelRight - 1,
            this.panelTop + 21,
            0xFF394874
        );
        graphics.fill(
            this.panelLeft + 1,
            this.panelBottom - 2,
            this.panelRight - 1,
            this.panelBottom - 1,
            0xFF182034
        );
        graphics.centeredText(this.font, this.title, this.width / 2, this.panelTop + 7, 0xFFFFFF);
    }
}
