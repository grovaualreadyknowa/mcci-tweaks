package dev.caden.mccitweaks

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class McciTweaksMod : ModInitializer {
    override fun onInitialize() {
        McciTweaksConfigStore.load()
        LOGGER.info("Initialized MCCI Tweaks")
    }

    companion object {
        const val MOD_ID: String = "mcci_tweaks"

        @JvmField
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
