package dev.caden.mccitweaks.chat

/**
 * Chat Separator settings, stored as the `chat` section of mcci-tweaks.json. Field names match the old
 * standalone chatsep.json so that file can be imported as-is.
 */
class ChatSeparatorConfig {
    var enabled: Boolean = true
    var tabVisibility: TabVisibility = TabVisibility.OPEN_CHAT_ONLY
    var mutedCrownColors: MutableSet<CrownColor> = LinkedHashSet()
    var muteInfoMessages: Boolean = false
    var coloredTabLabels: Boolean = false
    var hiddenFromAllChat: MutableSet<ChatTab> = LinkedHashSet()
    var tabLabelColors: MutableMap<ChatTab, Int> = LinkedHashMap()

    enum class TabVisibility(val displayName: String) {
        OPEN_CHAT_ONLY("Open chat only"),
        ALWAYS_VISIBLE("Always visible")
    }

    /** Gson can leave fields or collection entries null for missing or unknown values. */
    @Suppress("SENSELESS_COMPARISON", "UNCHECKED_CAST")
    fun applyDefaults() {
        if (tabVisibility == null) tabVisibility = TabVisibility.OPEN_CHAT_ONLY

        mutedCrownColors = (mutedCrownColors as Set<CrownColor?>? ?: emptySet())
            .filterNotNull()
            .mapTo(LinkedHashSet<CrownColor>(), CrownColor::canonical)

        hiddenFromAllChat = (hiddenFromAllChat as Set<ChatTab?>? ?: emptySet())
            .filterNotNullTo(LinkedHashSet<ChatTab>())
            .apply { remove(ChatTab.ALL) }

        val colors = LinkedHashMap<ChatTab, Int>()
        (tabLabelColors as Map<ChatTab?, Int?>? ?: emptyMap()).forEach { (tab, color) ->
            if (tab != null && tab != ChatTab.ALL && tab != ChatTab.LOCAL && color != null && color in 0x000000..0xFFFFFF) {
                colors[tab] = color
            }
        }
        tabLabelColors = colors
    }

    fun copy(): ChatSeparatorConfig = ChatSeparatorConfig().also { it.copyFrom(this) }

    fun copyFrom(other: ChatSeparatorConfig) {
        enabled = other.enabled
        tabVisibility = other.tabVisibility
        mutedCrownColors = LinkedHashSet(other.mutedCrownColors)
        muteInfoMessages = other.muteInfoMessages
        coloredTabLabels = other.coloredTabLabels
        hiddenFromAllChat = LinkedHashSet(other.hiddenFromAllChat)
        tabLabelColors = LinkedHashMap(other.tabLabelColors)
    }
}
