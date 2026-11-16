package dev.caden.mccnoflash

import java.util.Properties

object BuildFlags {
    @JvmField
    val COMMANDS_ENABLED: Boolean = readCommandsEnabled()

    private fun readCommandsEnabled(): Boolean = try {
        BuildFlags::class.java.classLoader.getResourceAsStream("mcci_tweaks_build.properties")?.use { input ->
            Properties().apply { load(input) }.getProperty("commands_enabled", "true").toBoolean()
        } ?: true
    } catch (e: Exception) {
        MccNoFlashMod.LOGGER.warn("Failed to read build flags. Falling back to commands enabled.", e)
        true
    }
}
