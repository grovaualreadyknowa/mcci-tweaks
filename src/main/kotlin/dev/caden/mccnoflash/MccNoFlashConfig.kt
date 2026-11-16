package dev.caden.mccnoflash

import dev.caden.mccitweaks.McciTweaksConfigStore
import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

class MccNoFlashConfig {
    var enabled: Boolean = true
    var debugLogging: Boolean = false
    var globalMode: FlashMode = FlashMode.DEFAULT
    var journalMode: FlashMode = FlashMode.DEFAULT
    var pocketMode: FlashMode = FlashMode.DEFAULT
    var navigatorMode: FlashMode = FlashMode.DEFAULT
    var anglrPanelMode: FlashMode = FlashMode.DEFAULT
    var profileMode: FlashMode = FlashMode.DEFAULT

    fun save() {
        val combined = McciTweaksConfigStore.get()
        combined.noFlash = this
        McciTweaksConfigStore.save(combined)
    }

    /** Gson can leave enum fields null when the file has an unknown or missing value. */
    @Suppress("SENSELESS_COMPARISON")
    fun ensureDefaults() {
        if (globalMode == null) globalMode = FlashMode.DEFAULT
        if (journalMode == null) journalMode = FlashMode.DEFAULT
        if (pocketMode == null) pocketMode = FlashMode.DEFAULT
        if (navigatorMode == null) navigatorMode = FlashMode.DEFAULT
        if (anglrPanelMode == null) anglrPanelMode = FlashMode.DEFAULT
        if (profileMode == null) profileMode = FlashMode.DEFAULT
    }

    fun copy(): MccNoFlashConfig = MccNoFlashConfig().also { it.copyFrom(this) }

    fun copyFrom(other: MccNoFlashConfig) {
        enabled = other.enabled
        debugLogging = other.debugLogging
        globalMode = other.globalMode
        journalMode = other.journalMode
        pocketMode = other.pocketMode
        navigatorMode = other.navigatorMode
        anglrPanelMode = other.anglrPanelMode
        profileMode = other.profileMode
    }

    companion object {
        @JvmStatic
        fun load(): MccNoFlashConfig = McciTweaksConfigStore.get().noFlash

        @JvmStatic
        fun getConfigPath(): Path =
            FabricLoader.getInstance().configDir.resolve(McciTweaksConfigStore.CONFIG_FILE_NAME)
    }
}
