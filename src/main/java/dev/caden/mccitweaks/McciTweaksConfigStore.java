package dev.caden.mccitweaks;

import com.fishingmod.FishingModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.caden.mccnoflash.MccNoFlashConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class McciTweaksConfigStore {
    public static final String CONFIG_FILE_NAME = "mcci-tweaks.json";
    public static final String LEGACY_AUTOMATION_FILE_NAME = "fishtance-instance-switcher.json";
    public static final String LEGACY_NO_FLASH_FILE_NAME = "mcc-no-flash.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static McciTweaksConfig current;

    private McciTweaksConfigStore() {
    }

    public static synchronized McciTweaksConfig load() {
        current = loadFromDirectory(FabricLoader.getInstance().getConfigDir());
        return current;
    }

    public static synchronized McciTweaksConfig get() {
        if (current == null) {
            return load();
        }
        return current;
    }

    public static synchronized void save(McciTweaksConfig config) {
        config.applyDefaults();
        current = config;
        saveToDirectory(FabricLoader.getInstance().getConfigDir(), config);
    }

    static McciTweaksConfig loadFromDirectory(Path configDirectory) {
        Path combinedPath = configDirectory.resolve(CONFIG_FILE_NAME);
        if (Files.exists(combinedPath)) {
            McciTweaksConfig loaded = readConfig(combinedPath, McciTweaksConfig.class);
            if (loaded == null) {
                McciTweaksMod.LOGGER.warn("Could not read {}; using defaults.", combinedPath);
                loaded = new McciTweaksConfig();
            }
            loaded.applyDefaults();
            saveToDirectory(configDirectory, loaded);
            return loaded;
        }

        McciTweaksConfig migrated = new McciTweaksConfig();

        Path legacyAutomationPath = configDirectory.resolve(LEGACY_AUTOMATION_FILE_NAME);
        if (Files.exists(legacyAutomationPath)) {
            FishingModConfig automation = readConfig(legacyAutomationPath, FishingModConfig.class);
            if (automation != null) {
                automation.applyDefaults();
                migrated.automation = automation;
                McciTweaksMod.LOGGER.info("Imported legacy automation settings from {}", legacyAutomationPath);
            } else {
                McciTweaksMod.LOGGER.warn("Could not import {}; using automation defaults.", legacyAutomationPath);
            }
        }

        Path legacyNoFlashPath = configDirectory.resolve(LEGACY_NO_FLASH_FILE_NAME);
        if (Files.exists(legacyNoFlashPath)) {
            MccNoFlashConfig noFlash = readConfig(legacyNoFlashPath, MccNoFlashConfig.class);
            if (noFlash != null) {
                noFlash.ensureDefaults();
                migrated.noFlash = noFlash;
                McciTweaksMod.LOGGER.info("Imported legacy no-flash settings from {}", legacyNoFlashPath);
            } else {
                McciTweaksMod.LOGGER.warn("Could not import {}; using no-flash defaults.", legacyNoFlashPath);
            }
        }

        migrated.applyDefaults();
        saveToDirectory(configDirectory, migrated);
        return migrated;
    }

    static void saveToDirectory(Path configDirectory, McciTweaksConfig config) {
        Path combinedPath = configDirectory.resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(configDirectory);
            Files.writeString(combinedPath, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            McciTweaksMod.LOGGER.error("Failed to write config at {}", combinedPath, exception);
        }
    }

    private static <T> T readConfig(Path path, Class<T> type) {
        try {
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (Exception exception) {
            McciTweaksMod.LOGGER.warn("Failed to parse {}", path, exception);
            return null;
        }
    }
}
