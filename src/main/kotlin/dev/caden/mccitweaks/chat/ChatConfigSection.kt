package dev.caden.mccitweaks.chat

import dev.caden.mccitweaks.ui.ConfigList
import dev.caden.mccitweaks.ui.CycleWidget
import dev.caden.mccitweaks.ui.ToggleWidget
import net.minecraft.network.chat.Component

/** Builds the Chat tab of the MCCI Tweaks config screen. */
object ChatConfigSection {
    private const val HINT_COLOR = 0xFFB8C4E0.toInt()

    fun addTo(list: ConfigList, config: ChatSeparatorConfig) {
        config.applyDefaults()

        list.beginSection(Component.literal("Chat Separator"))
        list.addOption(ToggleWidget(Component.literal("Enabled"), { config.enabled }, { config.enabled = it }, true))
        list.addOption(
            CycleWidget(
                Component.literal("Tab Visibility"),
                ChatSeparatorConfig.TabVisibility.entries,
                { config.tabVisibility },
                { config.tabVisibility = it },
                { Component.literal(it.displayName) },
                ChatSeparatorConfig.TabVisibility.OPEN_CHAT_ONLY
            )
        )
        list.addOption(
            ToggleWidget(Component.literal("Colored Tab Labels"), { config.coloredTabLabels }, { config.coloredTabLabels = it }, false)
        )
        list.addOption(
            ToggleWidget(Component.literal("Mute Info Messages"), { config.muteInfoMessages }, { config.muteInfoMessages = it }, false)
        )
        list.addText({ Component.literal("Adds All, Party, Rooms, PM, Local, Trade & Plobby tabs. Only active on MCC Island.") }, { HINT_COLOR })

        list.beginSection(Component.literal("Show in All Chat"))
        for (tab in ChatTab.entries) {
            if (tab == ChatTab.ALL) continue
            list.addOption(
                ToggleWidget(
                    Component.literal(tab.label),
                    { tab !in config.hiddenFromAllChat },
                    { shown -> if (shown) config.hiddenFromAllChat.remove(tab) else config.hiddenFromAllChat.add(tab) },
                    true
                )
            )
        }

        list.beginSection(Component.literal("Mute Crown Levels"))
        for (crown in CrownColor.crownLevels) {
            list.addOption(
                ToggleWidget(
                    Component.literal("Crown Level ${crown.label}").withStyle { it.withColor(crown.rgb) },
                    { crown in config.mutedCrownColors },
                    { muted -> if (muted) config.mutedCrownColors.add(crown) else config.mutedCrownColors.remove(crown) },
                    false
                )
            )
        }
        list.addText({ Component.literal("Muted messages stay in history and come back when unmuted.") }, { HINT_COLOR })
    }
}
