package dev.caden.mccitweaks.chat

import java.util.EnumMap
import net.minecraft.network.chat.Component

/** Keeps tab labels aligned with the actual colors sent by the server. */
object ChatTabColors {
    private val observedColors = EnumMap<ChatTab, Int>(ChatTab::class.java)
    private var onColorObserved: (ChatTab, Int) -> Unit = { _, _ -> }

    fun colorFor(tab: ChatTab): Int =
        if (tab == ChatTab.LOCAL) 0xFFFFFF else observedColors[tab] ?: tab.fallbackTextColor

    fun remember(tab: ChatTab?, message: Component) {
        if (tab == null || tab == ChatTab.ALL || tab == ChatTab.LOCAL) return

        val color = message.toFlatList()
            .firstOrNull { run -> run.string.isNotBlank() }
            ?.style
            ?.color
            ?.value
            ?: return

        if (observedColors.put(tab, color) != color) {
            onColorObserved(tab, color)
        }
    }

    fun restore(colors: Map<ChatTab, Int>, onColorObserved: (ChatTab, Int) -> Unit) {
        observedColors.clear()
        observedColors.putAll(colors.filterKeys { it != ChatTab.LOCAL })
        this.onColorObserved = onColorObserved
    }
}
