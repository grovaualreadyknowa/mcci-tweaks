package dev.caden.mccitweaks;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McciTweaksMod implements ModInitializer {
    public static final String MOD_ID = "mcci_tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        McciTweaksConfigStore.load();
        LOGGER.info("Initialized MCCI Tweaks");
    }
}
