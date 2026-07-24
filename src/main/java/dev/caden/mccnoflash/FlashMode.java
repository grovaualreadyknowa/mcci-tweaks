package dev.caden.mccnoflash;

import net.minecraft.network.chat.Component;

public enum FlashMode {
    DEFAULT("Default"),
    NORMAL_NO_FLASH("No Flashing"),
    STATIC_GLOW("Static Glow");

    private final Component displayName;

    FlashMode(String displayName) {
        this.displayName = Component.literal(displayName);
    }

    public Component getDisplayName() {
        return this.displayName;
    }
}
