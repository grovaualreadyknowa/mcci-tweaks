package dev.caden.mccitweaks

import com.fishingmod.FishingModConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import dev.caden.mccitweaks.chat.ChatSeparatorConfig
import dev.caden.mccnoflash.MccNoFlashConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import net.fabricmc.loader.api.FabricLoader

object McciTweaksConfigStore {
    const val CONFIG_FILE_NAME = "mcci-tweaks.json"
    const val LEGACY_AUTOMATION_FILE_NAME = "fishtance-instance-switcher.json"
    const val LEGACY_NO_FLASH_FILE_NAME = "mcc-no-flash.json"
    const val LEGACY_CHAT_SEPARATOR_FILE_NAME = "chatsep.json"

    private val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private var current: McciTweaksConfig? = null

    @JvmStatic
    @Synchronized
    fun load(): McciTweaksConfig = loadFromDirectory(FabricLoader.getInstance().configDir).also { current = it }

    @JvmStatic
    @Synchronized
    fun get(): McciTweaksConfig = current ?: load()

    @JvmStatic
    @Synchronized
    fun save(config: McciTweaksConfig) {
        config.applyDefaults()
        current = config
        saveToDirectory(FabricLoader.getInstance().configDir, config)
    }

    internal fun loadFromDirectory(configDirectory: Path): McciTweaksConfig {
        val combinedPath = configDirectory.resolve(CONFIG_FILE_NAME)
        if (combinedPath.exists()) {
            var loaded = readConfig(combinedPath, McciTweaksConfig::class.java)
            if (loaded == null) {
                McciTweaksMod.LOGGER.warn("Could not read {}; using defaults.", combinedPath)
                loaded = McciTweaksConfig()
            } else if (!hasSection(combinedPath, "chat")) {
                importLegacyChatSeparator(configDirectory, loaded)
            }
            loaded.applyDefaults()
            saveToDirectory(configDirectory, loaded)
            return loaded
        }

        val migrated = McciTweaksConfig()

        val legacyAutomationPath = configDirectory.resolve(LEGACY_AUTOMATION_FILE_NAME)
        if (legacyAutomationPath.exists()) {
            val automation = readConfig(legacyAutomationPath, FishingModConfig::class.java)
            if (automation != null) {
                automation.applyDefaults()
                migrated.automation = automation
                McciTweaksMod.LOGGER.info("Imported legacy automation settings from {}", legacyAutomationPath)
            } else {
                McciTweaksMod.LOGGER.warn("Could not import {}; using automation defaults.", legacyAutomationPath)
            }
        }

        val legacyNoFlashPath = configDirectory.resolve(LEGACY_NO_FLASH_FILE_NAME)
        if (legacyNoFlashPath.exists()) {
            val noFlash = readConfig(legacyNoFlashPath, MccNoFlashConfig::class.java)
            if (noFlash != null) {
                noFlash.ensureDefaults()
                migrated.noFlash = noFlash
                McciTweaksMod.LOGGER.info("Imported legacy no-flash settings from {}", legacyNoFlashPath)
            } else {
                McciTweaksMod.LOGGER.warn("Could not import {}; using no-flash defaults.", legacyNoFlashPath)
            }
        }

        importLegacyChatSeparator(configDirectory, migrated)

        migrated.applyDefaults()
        saveToDirectory(configDirectory, migrated)
        return migrated
    }

    /** Imports settings from the standalone Chat Separator mod (chatsep.json), leaving that file untouched. */
    private fun importLegacyChatSeparator(configDirectory: Path, config: McciTweaksConfig) {
        val legacyChatPath = configDirectory.resolve(LEGACY_CHAT_SEPARATOR_FILE_NAME)
        if (!legacyChatPath.exists()) return

        val chat = readConfig(legacyChatPath, ChatSeparatorConfig::class.java)
        if (chat != null) {
            chat.applyDefaults()
            config.chat = chat
            McciTweaksMod.LOGGER.info("Imported legacy Chat Separator settings from {}", legacyChatPath)
        } else {
            McciTweaksMod.LOGGER.warn("Could not import {}; using chat defaults.", legacyChatPath)
        }
    }

    private fun hasSection(path: Path, section: String): Boolean = try {
        val root = JsonParser.parseString(path.readText())
        root.isJsonObject && root.asJsonObject.has(section)
    } catch (_: Exception) {
        true
    }

    internal fun saveToDirectory(configDirectory: Path, config: McciTweaksConfig) {
        val combinedPath = configDirectory.resolve(CONFIG_FILE_NAME)
        try {
            Files.createDirectories(configDirectory)
            combinedPath.writeText(GSON.toJson(config))
        } catch (exception: IOException) {
            McciTweaksMod.LOGGER.error("Failed to write config at {}", combinedPath, exception)
        }
    }

    private fun <T> readConfig(path: Path, type: Class<T>): T? = try {
        GSON.fromJson(path.readText(), type)
    } catch (exception: Exception) {
        McciTweaksMod.LOGGER.warn("Failed to parse {}", path, exception)
        null
    }
}
