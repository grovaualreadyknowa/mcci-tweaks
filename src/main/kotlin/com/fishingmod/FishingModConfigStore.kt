package com.fishingmod

import dev.caden.mccitweaks.McciTweaksConfigStore

object FishingModConfigStore {
    @JvmStatic
    fun loadConfig(): FishingModConfig = McciTweaksConfigStore.get().automation

    @JvmStatic
    fun saveConfig(config: FishingModConfig) {
        val combined = McciTweaksConfigStore.get()
        combined.automation = config
        McciTweaksConfigStore.save(combined)
    }
}
