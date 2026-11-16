package dev.caden.mccitweaks.materialmarket

import dev.caden.mccitweaks.ui.ConfigList
import dev.caden.mccitweaks.ui.CycleWidget
import dev.caden.mccitweaks.ui.ToggleWidget
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

/** Builds the Material Market rows of the config screen; lives here so the API sync stays internal. */
object MaterialMarketConfigSection {
    private const val HINT_COLOR = 0xFFB8C4E0.toInt()
    private val API_KEY_LABEL: Component = Component.literal("MCC Island API Key")
    private val SYNC_LABEL: Component = Component.literal("Sync Now / Test API")

    @JvmStatic
    fun addTo(list: ConfigList, config: MaterialMarketConfig, font: Font) {
        config.applyDefaults()

        list.beginSection(Component.literal("Material Market Inventory"))
        list.addOption(ToggleWidget(Component.literal("Enabled"), { config.enabled }, { config.enabled = it }, true))

        val source = CycleWidget(
            Component.literal("Inventory Source"),
            MaterialMarketInventorySource.entries,
            { config.inventorySource },
            { value ->
                config.inventorySource = value
                list.refresh()
            },
            { value -> Component.literal(if (value == MaterialMarketInventorySource.API) "API" else "Traditional") },
            MaterialMarketInventorySource.TRADITIONAL
        )
        list.addOption(source)

        val clearButton = Button.builder(clearText()) { button ->
            MaterialMarketInventory.clearLocalData()
            button.message = clearText()
        }.build()
        list.addWidget(clearButton)
        list.addText(
            { Component.literal("Traditional mode scans Recipe Book and Fusion Forge counts.") },
            { HINT_COLOR },
            config::usesTraditionalInventory
        )

        list.beginSection(API_KEY_LABEL, config::usesApiInventory)

        val apiKeyBox = MaskedEditBox(font).apply {
            setMaxLength(256)
            setHint(Component.literal("Paste MCC Island API key"))
            value = config.apiKey
            setResponder { value ->
                config.apiKey = value
                MaterialMarketApiSync.keyChanged()
            }
        }
        list.addWidget(apiKeyBox)

        val syncButton = Button.builder(SYNC_LABEL) {
            config.apiKey = apiKeyBox.value.trim()
            apiKeyBox.value = config.apiKey
            MaterialMarketApiSync.requestManual()
        }.build()
        list.addWidget(syncButton)

        list.addText({ Component.literal("Get a key at gateway.noxcrew.com") }, { HINT_COLOR })
        list.addText({ Component.literal("In MCCI, enable the Infinibag API setting.") }, { HINT_COLOR })
        list.addText(
            { Component.literal(MaterialMarketApiSync.status().message) },
            { statusColor(MaterialMarketApiSync.status().type) }
        )

        list.addTickHandler {
            val syncing = MaterialMarketApiSync.isSyncInProgress()
            source.active = !syncing
            clearButton.active = !syncing
            clearButton.message = clearText()
            apiKeyBox.setEditable(!syncing)
            syncButton.active = config.enabled && apiKeyBox.value.isNotBlank() && !syncing
            syncButton.message = if (syncing) Component.literal("Syncing...") else SYNC_LABEL
        }
    }

    /** Lets the config screen tell the sync task that the stored key was replaced from outside this section. */
    @JvmStatic
    fun notifyApiKeyChanged() {
        MaterialMarketApiSync.keyChanged()
    }

    private fun clearText(): Component =
        Component.literal("Clear Saved Counts (${MaterialMarketInventory.trackedMaterialCount()} tracked)")

    private fun statusColor(status: MaterialMarketApiSync.DisplayStatus): Int = when (status) {
        MaterialMarketApiSync.DisplayStatus.SUCCESS -> 0xFF80E080.toInt()
        MaterialMarketApiSync.DisplayStatus.ERROR -> 0xFFFF8080.toInt()
        MaterialMarketApiSync.DisplayStatus.SYNCING -> 0xFFFFFF80.toInt()
        MaterialMarketApiSync.DisplayStatus.READY, MaterialMarketApiSync.DisplayStatus.NOT_CONFIGURED -> HINT_COLOR
    }

    private class MaskedEditBox(font: Font) : EditBox(font, 0, 0, 310, 20, API_KEY_LABEL) {
        init {
            addFormatter { text, _ -> FormattedCharSequence.forward("*".repeat(text.length), Style.EMPTY) }
        }

        override fun createNarrationMessage(): MutableComponent =
            Component.literal("MCC Island API key field; contents hidden")
    }
}
