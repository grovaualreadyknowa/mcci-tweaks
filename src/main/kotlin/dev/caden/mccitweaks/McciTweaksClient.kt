package dev.caden.mccitweaks

import com.fishingmod.FishingModClient
import dev.caden.mccitweaks.chat.ChatSeparator
import dev.caden.mccitweaks.materialmarket.MaterialMarketInventory
import dev.caden.mccnoflash.MccNoFlashClient
import dev.caden.mccnoflash.MccNoFlashState
import net.fabricmc.api.ClientModInitializer

class McciTweaksClient : ClientModInitializer {
    override fun onInitializeClient() {
        McciTweaksConfigStore.get()
        MccNoFlashState.get().loadConfig()
        FishingModClient().onInitializeClient()
        MccNoFlashClient().onInitializeClient()
        MaterialMarketInventory.register()
        ChatSeparator.initialize()
        McciTweaksMod.LOGGER.info("Initialized MCCI Tweaks client features")
    }
}
