package dev.caden.mccitweaks

import com.fishingmod.FishingModConfig
import dev.caden.mccitweaks.chat.ChatSeparatorConfig
import dev.caden.mccitweaks.chat.ChatTab
import dev.caden.mccitweaks.chat.CrownColor
import dev.caden.mccitweaks.materialmarket.MaterialMarketInventorySource
import dev.caden.mccnoflash.FlashMode
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class McciTweaksConfigStoreTest {
    @TempDir
    lateinit var configDirectory: Path

    @Test
    fun `creates defaults when no config exists`() {
        val config = McciTweaksConfigStore.loadFromDirectory(configDirectory)

        assertNotNull(config.automation)
        assertNotNull(config.noFlash)
        assertNotNull(config.materialMarket)
        assertNotNull(config.chat)
        assertEquals("instance join 11", config.automation.instanceCommand)
        assertEquals(FlashMode.DEFAULT, config.noFlash.globalMode)
        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.materialMarket.inventorySource)
        assertEquals("", config.materialMarket.apiKey)
        assertTrue(config.chat.enabled)
        assertTrue(Files.exists(configDirectory.resolve(McciTweaksConfigStore.CONFIG_FILE_NAME)))
    }

    @Test
    fun `imports both legacy files without changing them`() {
        val automationPath = configDirectory.resolve(McciTweaksConfigStore.LEGACY_AUTOMATION_FILE_NAME)
        val noFlashPath = configDirectory.resolve(McciTweaksConfigStore.LEGACY_NO_FLASH_FILE_NAME)
        val automationJson = """
            {
              "enabled": false,
              "instanceCommand": "instance join 7",
              "postTeleportDelayMs": 2100
            }
        """.trimIndent()
        val noFlashJson = """
            {
              "enabled": false,
              "globalMode": "STATIC_GLOW",
              "journalMode": "NORMAL_NO_FLASH"
            }
        """.trimIndent()
        Files.writeString(automationPath, automationJson)
        Files.writeString(noFlashPath, noFlashJson)

        val config = McciTweaksConfigStore.loadFromDirectory(configDirectory)

        assertFalse(config.automation.enabled)
        assertEquals("instance join 11", config.automation.instanceCommand)
        assertEquals(2100, config.automation.postTeleportDelayMs)
        assertFalse(config.noFlash.enabled)
        assertEquals(FlashMode.STATIC_GLOW, config.noFlash.globalMode)
        assertEquals(FlashMode.NORMAL_NO_FLASH, config.noFlash.journalMode)
        assertEquals(automationJson, Files.readString(automationPath))
        assertEquals(noFlashJson, Files.readString(noFlashPath))
    }

    @Test
    fun `existing combined config is authoritative and adds material market defaults`() {
        Files.writeString(
            configDirectory.resolve(McciTweaksConfigStore.CONFIG_FILE_NAME),
            """
            {
              "automation": {"enabled": true, "instanceCommand": "instance join 9"},
              "noFlash": {"enabled": true, "globalMode": "NORMAL_NO_FLASH"},
              "materialMarket": {"enabled": true}
            }
            """
        )
        Files.writeString(
            configDirectory.resolve(McciTweaksConfigStore.LEGACY_AUTOMATION_FILE_NAME),
            """{"enabled": false, "instanceCommand": "instance join 99"}"""
        )

        val config = McciTweaksConfigStore.loadFromDirectory(configDirectory)

        assertTrue(config.automation.enabled)
        assertEquals("instance join 9", config.automation.instanceCommand)
        assertEquals(9, config.automation.targetInstance())
        assertEquals(FlashMode.NORMAL_NO_FLASH, config.noFlash.globalMode)
        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.materialMarket.inventorySource)
        assertEquals("", config.materialMarket.apiKey)
    }

    @Test
    fun `persists api mode and key`() {
        val config = McciTweaksConfig()
        config.materialMarket.inventorySource = MaterialMarketInventorySource.API
        config.materialMarket.apiKey = "saved-key"

        McciTweaksConfigStore.saveToDirectory(configDirectory, config)
        val loaded = McciTweaksConfigStore.loadFromDirectory(configDirectory)

        assertEquals(MaterialMarketInventorySource.API, loaded.materialMarket.inventorySource)
        assertEquals("saved-key", loaded.materialMarket.apiKey)
    }

    @Test
    fun `malformed legacy section falls back independently`() {
        Files.writeString(configDirectory.resolve(McciTweaksConfigStore.LEGACY_AUTOMATION_FILE_NAME), "{not valid json")
        Files.writeString(
            configDirectory.resolve(McciTweaksConfigStore.LEGACY_NO_FLASH_FILE_NAME),
            """{"enabled": false, "profileMode": "STATIC_GLOW"}"""
        )

        val config = McciTweaksConfigStore.loadFromDirectory(configDirectory)

        assertTrue(config.automation.enabled)
        assertEquals("instance join 11", config.automation.instanceCommand)
        assertFalse(config.noFlash.enabled)
        assertEquals(FlashMode.STATIC_GLOW, config.noFlash.profileMode)
    }

    @Test
    fun `cycles target fishtance instance and keeps command in sync`() {
        val config = FishingModConfig.defaults()

        assertEquals(11, config.targetInstance())
        config.cycleTargetInstance()
        assertEquals(8, config.targetInstance())
        assertEquals("instance join 8", config.commandForTargetInstance())

        repeat(3) { config.cycleTargetInstance() }
        assertEquals(11, config.targetInstance())
        assertTrue(config.keywordsForTargetInstance().contains("fishtance #11"))
    }

    @Test
    fun `repairs unsupported target instance`() {
        val config = FishingModConfig.defaults()
        config.targetInstance = 18
        config.instanceCommand = "instance join 9"

        config.applyDefaults()

        assertEquals(9, config.targetInstance())
        assertEquals("instance join 9", config.instanceCommand)
        assertTrue(config.currentInstanceKeywords.contains("fishtance #9"))
    }

    @Test
    fun `imports legacy chat separator settings into existing config`() {
        Files.writeString(configDirectory.resolve(McciTweaksConfigStore.CONFIG_FILE_NAME), "{}")
        Files.writeString(
            configDirectory.resolve(McciTweaksConfigStore.LEGACY_CHAT_SEPARATOR_FILE_NAME),
            """
            {
              "tabVisibility": "ALWAYS_VISIBLE",
              "mutedCrownColors": ["RED", "LEVEL_0_9", "NOT_A_REAL_LEVEL"],
              "muteInfoMessages": true,
              "hiddenFromAllChat": ["ALL", "PLOBBY"],
              "tabLabelColors": {"LOCAL": 1, "PARTY": 11185150}
            }
            """
        )

        val config = McciTweaksConfigStore.loadFromDirectory(configDirectory)

        assertTrue(config.chat.enabled)
        assertEquals(ChatSeparatorConfig.TabVisibility.ALWAYS_VISIBLE, config.chat.tabVisibility)
        assertEquals(setOf(CrownColor.LEVEL_60_69, CrownColor.LEVEL_0_9), config.chat.mutedCrownColors)
        assertTrue(config.chat.muteInfoMessages)
        assertEquals(setOf(ChatTab.PLOBBY), config.chat.hiddenFromAllChat)
        assertEquals(mapOf(ChatTab.PARTY to 0xAAABFE), config.chat.tabLabelColors)
    }

    @Test
    fun `does not reimport chat separator once migrated`() {
        Files.writeString(
            configDirectory.resolve(McciTweaksConfigStore.CONFIG_FILE_NAME),
            """{"chat": {"muteInfoMessages": false}}"""
        )
        Files.writeString(
            configDirectory.resolve(McciTweaksConfigStore.LEGACY_CHAT_SEPARATOR_FILE_NAME),
            """{"muteInfoMessages": true}"""
        )

        assertFalse(McciTweaksConfigStore.loadFromDirectory(configDirectory).chat.muteInfoMessages)
    }
}
