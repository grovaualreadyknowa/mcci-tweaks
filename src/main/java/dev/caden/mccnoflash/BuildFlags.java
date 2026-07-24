package dev.caden.mccnoflash;

import java.io.InputStream;
import java.util.Properties;

public final class BuildFlags {
    public static final boolean COMMANDS_ENABLED = readCommandsEnabled();

    private BuildFlags() {
    }

    private static boolean readCommandsEnabled() {
        try (InputStream input = BuildFlags.class.getClassLoader().getResourceAsStream("mcci_tweaks_build.properties")) {
            if (input == null) {
                return true;
            }

            Properties properties = new Properties();
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty("commands_enabled", "true"));
        } catch (Exception e) {
            MccNoFlashMod.LOGGER.warn("Failed to read build flags. Falling back to commands enabled.", e);
            return true;
        }
    }
}
