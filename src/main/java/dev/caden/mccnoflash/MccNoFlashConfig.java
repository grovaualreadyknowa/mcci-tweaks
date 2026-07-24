package dev.caden.mccnoflash;

import dev.caden.mccitweaks.McciTweaksConfig;
import dev.caden.mccitweaks.McciTweaksConfigStore;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class MccNoFlashConfig {
    public boolean enabled = true;
    public boolean debugLogging = false;
    public FlashMode globalMode = FlashMode.DEFAULT;
    public FlashMode journalMode = FlashMode.DEFAULT;
    public FlashMode pocketMode = FlashMode.DEFAULT;
    public FlashMode navigatorMode = FlashMode.DEFAULT;
    public FlashMode anglrPanelMode = FlashMode.DEFAULT;
    public FlashMode profileMode = FlashMode.DEFAULT;

    public static MccNoFlashConfig load() {
        return McciTweaksConfigStore.get().noFlash;
    }

    public void save() {
        McciTweaksConfig combined = McciTweaksConfigStore.get();
        combined.noFlash = this;
        McciTweaksConfigStore.save(combined);
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(McciTweaksConfigStore.CONFIG_FILE_NAME);
    }

    public void ensureDefaults() {
        if (this.globalMode == null) {
            this.globalMode = FlashMode.DEFAULT;
        }
        if (this.journalMode == null) {
            this.journalMode = FlashMode.DEFAULT;
        }
        if (this.pocketMode == null) {
            this.pocketMode = FlashMode.DEFAULT;
        }
        if (this.navigatorMode == null) {
            this.navigatorMode = FlashMode.DEFAULT;
        }
        if (this.anglrPanelMode == null) {
            this.anglrPanelMode = FlashMode.DEFAULT;
        }
        if (this.profileMode == null) {
            this.profileMode = FlashMode.DEFAULT;
        }
    }
}
