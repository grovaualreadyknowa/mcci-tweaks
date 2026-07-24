package dev.caden.mccitweaks;

import com.fishingmod.FishingModClient;
import dev.caden.mccitweaks.materialmarket.MaterialMarketInventory;
import dev.caden.mccnoflash.MccNoFlashClient;
import dev.caden.mccnoflash.MccNoFlashState;
import net.fabricmc.api.ClientModInitializer;

public final class McciTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        McciTweaksConfigStore.get();
        MccNoFlashState.get().loadConfig();
        new FishingModClient().onInitializeClient();
        new MccNoFlashClient().onInitializeClient();
        MaterialMarketInventory.register();
        McciTweaksMod.LOGGER.info("Initialized MCCI Tweaks client features");
    }
}
