package dev.caden.mccitweaks.chat

import dev.caden.mccitweaks.McciTweaksConfigStore
import java.util.Locale
import net.minecraft.client.Minecraft

/** Entry point for the Chat Separator feature; its config lives inside the combined MCCI Tweaks config. */
object ChatSeparator {
    @JvmStatic
    fun initialize() {
        ChatTabColors.restore(config().tabLabelColors, ::rememberTabLabelColor)
    }

    @JvmStatic
    fun config(): ChatSeparatorConfig = McciTweaksConfigStore.get().chat

    /** True only when the feature is toggled on AND the player is connected to MCC Island. */
    @JvmStatic
    fun isEnabled(): Boolean = config().enabled && isOnMccServer()

    @JvmStatic
    fun isOnMccServer(): Boolean =
        Minecraft.getInstance()?.currentServer?.ip?.lowercase(Locale.ROOT)?.contains("mccisland.net") == true

    private fun rememberTabLabelColor(tab: ChatTab, color: Int) {
        val config = config()
        if (config.tabLabelColors[tab] == color) return
        config.tabLabelColors[tab] = color
        McciTweaksConfigStore.save(McciTweaksConfigStore.get())
    }

    /** Re-applies the current filters to the chat window after a setting changed. */
    @JvmStatic
    fun refreshChat() {
        val gui = Minecraft.getInstance()?.gui ?: return
        if (!isEnabled()) {
            ChatTabs.resetSelection()
        }
        ChatTabs.refresh(gui.hud.chat)
    }
}
