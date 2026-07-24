package dev.caden.mccitweaks;

import com.fishingmod.FishingModConfig;
import dev.caden.mccitweaks.materialmarket.MaterialMarketConfig;
import dev.caden.mccnoflash.MccNoFlashConfig;

public final class McciTweaksConfig {
    public FishingModConfig automation = FishingModConfig.defaults();
    public MccNoFlashConfig noFlash = new MccNoFlashConfig();
    public MaterialMarketConfig materialMarket = new MaterialMarketConfig();

    public void applyDefaults() {
        if (this.automation == null) {
            this.automation = FishingModConfig.defaults();
        } else {
            this.automation.applyDefaults();
        }

        if (this.noFlash == null) {
            this.noFlash = new MccNoFlashConfig();
        } else {
            this.noFlash.ensureDefaults();
        }

        if (this.materialMarket == null) {
            this.materialMarket = new MaterialMarketConfig();
        } else {
            this.materialMarket.applyDefaults();
        }
    }
}
