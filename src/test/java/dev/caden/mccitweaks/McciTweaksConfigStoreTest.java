package dev.caden.mccitweaks;

import com.fishingmod.FishingModConfig;
import dev.caden.mccitweaks.materialmarket.MaterialMarketInventorySource;
import dev.caden.mccnoflash.FlashMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class McciTweaksConfigStoreTest {
    @TempDir
    Path configDirectory;

    @Test
    void createsDefaultsWhenNoConfigExists() {
        McciTweaksConfig config = McciTweaksConfigStore.loadFromDirectory(configDirectory);

        assertNotNull(config.automation);
        assertNotNull(config.noFlash);
        assertNotNull(config.materialMarket);
        assertEquals("instance join 11", config.automation.instanceCommand);
        assertEquals(FlashMode.DEFAULT, config.noFlash.globalMode);
        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.materialMarket.inventorySource);
        assertEquals("", config.materialMarket.apiKey);
        assertTrue(Files.exists(configDirectory.resolve(McciTweaksConfigStore.CONFIG_FILE_NAME)));
    }

    @Test
    void importsBothLegacyFilesWithoutChangingThem() throws Exception {
        Path automationPath = configDirectory.resolve(McciTweaksConfigStore.LEGACY_AUTOMATION_FILE_NAME);
        Path noFlashPath = configDirectory.resolve(McciTweaksConfigStore.LEGACY_NO_FLASH_FILE_NAME);
        String automationJson = """
            {
              "enabled": false,
              "instanceCommand": "instance join 7",
              "postTeleportDelayMs": 2100
            }
            """;
        String noFlashJson = """
            {
              "enabled": false,
              "globalMode": "STATIC_GLOW",
              "journalMode": "NORMAL_NO_FLASH"
            }
            """;
        Files.writeString(automationPath, automationJson);
        Files.writeString(noFlashPath, noFlashJson);

        McciTweaksConfig config = McciTweaksConfigStore.loadFromDirectory(configDirectory);

        assertFalse(config.automation.enabled);
        assertEquals("instance join 11", config.automation.instanceCommand);
        assertEquals(2100, config.automation.postTeleportDelayMs);
        assertFalse(config.noFlash.enabled);
        assertEquals(FlashMode.STATIC_GLOW, config.noFlash.globalMode);
        assertEquals(FlashMode.NORMAL_NO_FLASH, config.noFlash.journalMode);
        assertEquals(automationJson, Files.readString(automationPath));
        assertEquals(noFlashJson, Files.readString(noFlashPath));
    }

    @Test
    void existingCombinedConfigIsAuthoritativeAndAddsMaterialMarketDefaults() throws Exception {
        Files.writeString(configDirectory.resolve(McciTweaksConfigStore.CONFIG_FILE_NAME), """
            {
              "automation": {
                "enabled": true,
                "instanceCommand": "instance join 9"
              },
              "noFlash": {
                "enabled": true,
                "globalMode": "NORMAL_NO_FLASH"
              },
              "materialMarket": {
                "enabled": true
              }
            }
            """);
        Files.writeString(configDirectory.resolve(McciTweaksConfigStore.LEGACY_AUTOMATION_FILE_NAME), """
            {"enabled": false, "instanceCommand": "instance join 99"}
            """);

        McciTweaksConfig config = McciTweaksConfigStore.loadFromDirectory(configDirectory);

        assertTrue(config.automation.enabled);
        assertEquals("instance join 9", config.automation.instanceCommand);
        assertEquals(9, config.automation.targetInstance());
        assertEquals(FlashMode.NORMAL_NO_FLASH, config.noFlash.globalMode);
        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.materialMarket.inventorySource);
        assertEquals("", config.materialMarket.apiKey);
    }

    @Test
    void persistsApiModeAndKey() {
        McciTweaksConfig config = new McciTweaksConfig();
        config.materialMarket.inventorySource = MaterialMarketInventorySource.API;
        config.materialMarket.apiKey = "saved-key";

        McciTweaksConfigStore.saveToDirectory(configDirectory, config);
        McciTweaksConfig loaded = McciTweaksConfigStore.loadFromDirectory(configDirectory);

        assertEquals(MaterialMarketInventorySource.API, loaded.materialMarket.inventorySource);
        assertEquals("saved-key", loaded.materialMarket.apiKey);
    }

    @Test
    void malformedLegacySectionFallsBackIndependently() throws Exception {
        Files.writeString(
            configDirectory.resolve(McciTweaksConfigStore.LEGACY_AUTOMATION_FILE_NAME),
            "{not valid json"
        );
        Files.writeString(
            configDirectory.resolve(McciTweaksConfigStore.LEGACY_NO_FLASH_FILE_NAME),
            """
            {"enabled": false, "profileMode": "STATIC_GLOW"}
            """
        );

        McciTweaksConfig config = McciTweaksConfigStore.loadFromDirectory(configDirectory);

        assertTrue(config.automation.enabled);
        assertEquals("instance join 11", config.automation.instanceCommand);
        assertFalse(config.noFlash.enabled);
        assertEquals(FlashMode.STATIC_GLOW, config.noFlash.profileMode);
    }

    @Test
    void cyclesTargetFishtanceInstanceAndKeepsCommandInSync() {
        FishingModConfig config = FishingModConfig.defaults();

        assertEquals(11, config.targetInstance());
        config.cycleTargetInstance();
        assertEquals(8, config.targetInstance());
        assertEquals("instance join 8", config.commandForTargetInstance());

        config.cycleTargetInstance();
        config.cycleTargetInstance();
        config.cycleTargetInstance();
        assertEquals(11, config.targetInstance());
        assertTrue(config.keywordsForTargetInstance().contains("fishtance #11"));
    }

    @Test
    void repairsUnsupportedTargetInstance() {
        FishingModConfig config = FishingModConfig.defaults();
        config.targetInstance = 18;
        config.instanceCommand = "instance join 9";

        config.applyDefaults();

        assertEquals(9, config.targetInstance());
        assertEquals("instance join 9", config.instanceCommand);
        assertTrue(config.currentInstanceKeywords.contains("fishtance #9"));
    }
}
