package dev.caden.mccitweaks

import com.fishingmod.FishingModConfig
import dev.caden.mccitweaks.chat.ChatSeparatorConfig
import dev.caden.mccitweaks.materialmarket.MaterialMarketConfig
import dev.caden.mccnoflash.MccNoFlashConfig

class McciTweaksConfig {
    var automation: FishingModConfig = FishingModConfig.defaults()
    var noFlash: MccNoFlashConfig = MccNoFlashConfig()
    var materialMarket: MaterialMarketConfig = MaterialMarketConfig()
    var chat: ChatSeparatorConfig = ChatSeparatorConfig()

    /** Sections can be missing (null) when an older or hand-edited file is loaded through Gson. */
    @Suppress("SENSELESS_COMPARISON")
    fun applyDefaults() {
        if (automation == null) automation = FishingModConfig.defaults() else automation.applyDefaults()
        if (noFlash == null) noFlash = MccNoFlashConfig() else noFlash.ensureDefaults()
        if (materialMarket == null) materialMarket = MaterialMarketConfig() else materialMarket.applyDefaults()
        if (chat == null) chat = ChatSeparatorConfig() else chat.applyDefaults()
    }
}
