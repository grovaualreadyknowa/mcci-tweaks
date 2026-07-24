package com.fishingmod;

import dev.caden.mccitweaks.McciTweaksConfig;
import dev.caden.mccitweaks.McciTweaksConfigStore;

public final class FishingModConfigStore {
    private FishingModConfigStore() {
    }

    public static FishingModConfig loadConfig() {
        return McciTweaksConfigStore.get().automation;
    }

    public static void saveConfig(FishingModConfig config) {
        McciTweaksConfig combined = McciTweaksConfigStore.get();
        combined.automation = config;
        McciTweaksConfigStore.save(combined);
    }
}
