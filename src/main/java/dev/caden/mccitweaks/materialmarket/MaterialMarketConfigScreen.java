package dev.caden.mccitweaks.materialmarket;

import dev.caden.mccitweaks.McciTweaksConfig;
import dev.caden.mccitweaks.McciTweaksConfigStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class MaterialMarketConfigScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 340;
    private static final int MAX_CONTROL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 228;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final MaterialMarketConfig config;
    private Button enabledButton;
    private Button sourceButton;
    private MaskedEditBox apiKeyBox;
    private Button syncButton;
    private Button clearButton;
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;

    public MaterialMarketConfigScreen(Screen parent) {
        super(Component.literal("MCCI Tweaks - Material Market Inventory"));
        this.parent = parent;
        this.config = McciTweaksConfigStore.get().materialMarket;
        this.config.applyDefaults();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(MAX_PANEL_WIDTH, this.width - 16);
        int controlWidth = Math.min(MAX_CONTROL_WIDTH, panelWidth - 20);
        this.panelLeft = (this.width - panelWidth) / 2;
        this.panelTop = Math.max(6, (this.height - PANEL_HEIGHT) / 2);
        this.panelRight = this.panelLeft + panelWidth;
        this.panelBottom = this.panelTop + PANEL_HEIGHT;
        int left = (this.width - controlWidth) / 2;

        this.enabledButton = Button.builder(enabledText(), button -> {
            this.config.enabled = !this.config.enabled;
            updateControls();
        }).bounds(left, this.panelTop + 28, controlWidth, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.enabledButton);

        this.sourceButton = Button.builder(sourceText(), button -> {
            this.config.inventorySource = this.config.inventorySource.next();
            updateControls();
        }).bounds(left, this.panelTop + 53, controlWidth, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.sourceButton);

        this.apiKeyBox = new MaskedEditBox(
            this.font,
            left,
            this.panelTop + 78,
            controlWidth,
            BUTTON_HEIGHT,
            Component.literal("MCC Island API key")
        );
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setHint(Component.literal("Paste MCC Island API key"));
        this.apiKeyBox.setValue(this.config.apiKey);
        this.apiKeyBox.setResponder(value -> {
            this.config.apiKey = value;
            MaterialMarketApiSync.keyChanged();
            updateControls();
        });
        this.addRenderableWidget(this.apiKeyBox);

        this.syncButton = Button.builder(Component.literal("Sync Now / Test API"), button -> {
            this.config.apiKey = this.apiKeyBox.getValue().trim();
            this.apiKeyBox.setValue(this.config.apiKey);
            MaterialMarketApiSync.requestManual();
            updateControls();
        }).bounds(left, this.panelTop + 103, controlWidth, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.syncButton);

        this.clearButton = Button.builder(clearText(), button -> {
            MaterialMarketInventory.clearLocalData();
            updateControls();
        }).bounds(left, this.panelTop + 131, controlWidth, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.clearButton);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> saveAndClose())
            .bounds(left, this.panelTop + 159, controlWidth, BUTTON_HEIGHT)
            .build());

        updateControls();
    }

    @Override
    public void tick() {
        updateControls();
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
        graphics.centeredText(this.font, this.title, this.width / 2, this.panelTop + 7, 0xFFFFFF);

        if (this.config.usesApiInventory()) {
            graphics.centeredText(
                this.font,
                "Get a key at gateway.noxcrew.com",
                this.width / 2,
                this.panelTop + 185,
                0xFFB8C4E0
            );
            graphics.centeredText(
                this.font,
                "In MCCI, enable the Infinibag API setting.",
                this.width / 2,
                this.panelTop + 197,
                0xFFB8C4E0
            );

            MaterialMarketApiSync.StatusSnapshot snapshot = MaterialMarketApiSync.status();
            graphics.centeredText(
                this.font,
                snapshot.message(),
                this.width / 2,
                this.panelTop + 212,
                statusColor(snapshot.type())
            );
        } else {
            graphics.centeredText(
                this.font,
                "Traditional mode scans Recipe Book and Fusion Forge counts.",
                this.width / 2,
                this.panelTop + 190,
                0xFFB8C4E0
            );
        }
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    private void updateControls() {
        if (this.enabledButton == null) {
            return;
        }

        boolean apiMode = this.config.usesApiInventory();
        boolean syncing = MaterialMarketApiSync.isSyncInProgress();
        this.enabledButton.setMessage(enabledText());
        this.sourceButton.setMessage(sourceText());
        this.sourceButton.active = !syncing;
        this.apiKeyBox.setVisible(apiMode);
        this.apiKeyBox.setEditable(apiMode && !syncing);
        this.syncButton.visible = apiMode;
        this.syncButton.active = apiMode
            && this.config.enabled
            && !this.apiKeyBox.getValue().isBlank()
            && !syncing;
        this.syncButton.setMessage(Component.literal(syncing ? "Syncing..." : "Sync Now / Test API"));
        this.clearButton.setMessage(clearText());
        this.clearButton.active = !syncing;
    }

    private void saveAndClose() {
        if (this.apiKeyBox != null) {
            this.config.apiKey = this.apiKeyBox.getValue().trim();
        }
        McciTweaksConfig combined = McciTweaksConfigStore.get();
        combined.materialMarket = this.config;
        McciTweaksConfigStore.save(combined);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private Component enabledText() {
        return Component.literal("Material Market Inventory: " + (this.config.enabled ? "ON" : "OFF"));
    }

    private Component sourceText() {
        String source = this.config.usesApiInventory() ? "API" : "Traditional";
        return Component.literal("Inventory Source: " + source);
    }

    private Component clearText() {
        return Component.literal("Clear Saved Counts (" + MaterialMarketInventory.trackedMaterialCount() + " tracked)");
    }

    private static int statusColor(MaterialMarketApiSync.DisplayStatus status) {
        return switch (status) {
            case SUCCESS -> 0xFF80E080;
            case ERROR -> 0xFFFF8080;
            case SYNCING -> 0xFFFFFF80;
            case READY, NOT_CONFIGURED -> 0xFFB8C4E0;
        };
    }

    private static final class MaskedEditBox extends EditBox {
        private MaskedEditBox(
            net.minecraft.client.gui.Font font,
            int x,
            int y,
            int width,
            int height,
            Component narration
        ) {
            super(font, x, y, width, height, narration);
            this.addFormatter((text, offset) ->
                FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY)
            );
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.literal("MCC Island API key field; contents hidden");
        }
    }
}
