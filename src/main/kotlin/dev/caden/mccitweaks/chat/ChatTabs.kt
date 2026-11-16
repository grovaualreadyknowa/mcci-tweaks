package dev.caden.mccitweaks.chat

import java.util.function.Predicate
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

/** Holds the selected chat tab and decides which messages the chat window shows. */
object ChatTabs {
    const val HISTORY_LIMIT = 1_000

    @JvmStatic
    var selected: ChatTab = ChatTab.ALL
        private set

    @JvmStatic
    fun isVisible(message: GuiMessage): Boolean {
        if (!ChatSeparator.isEnabled()) return true
        val config = ChatSeparator.config()

        val content = message.content()
        if (CrownMessageClassifier.isMuted(content, config.mutedCrownColors)) return false
        if (config.muteInfoMessages && InfoMessageFilter.isInfoMessage(content)) return false

        val classified = ChatMessageClassifier.classify(content)
        ChatTabColors.remember(classified, content)
        return matchesTab(selected, classified, config.hiddenFromAllChat)
    }

    /** Pure version of [isVisible] used by tests. */
    internal fun shouldDisplay(
        tab: ChatTab,
        component: Component,
        mutedCrownColors: Set<CrownColor>,
        muteInfoMessages: Boolean = false,
        hiddenFromAllChat: Set<ChatTab> = emptySet()
    ): Boolean =
        !CrownMessageClassifier.isMuted(component, mutedCrownColors) &&
            !(muteInfoMessages && InfoMessageFilter.isInfoMessage(component)) &&
            matchesTab(tab, ChatMessageClassifier.classify(component), hiddenFromAllChat)

    private fun matchesTab(tab: ChatTab, classified: ChatTab?, hiddenFromAllChat: Set<ChatTab>): Boolean =
        if (tab == ChatTab.ALL) classified !in hiddenFromAllChat else classified == tab

    @JvmStatic
    fun select(tab: ChatTab, chat: ChatComponent) {
        if (selected == tab) return
        selected = tab
        refresh(chat)
    }

    @JvmStatic
    fun refresh(chat: ChatComponent) {
        chat.setVisibleMessageFilter(Predicate(::isVisible))
        chat.resetChatScroll()
    }

    @JvmStatic
    fun resetSelection() {
        selected = ChatTab.ALL
    }
}
