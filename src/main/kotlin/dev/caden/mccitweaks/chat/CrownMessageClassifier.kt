package dev.caden.mccitweaks.chat

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

/** Identifies the Champion's Crown glyph in an MCCI player-chat prefix. */
object CrownMessageClassifier {
    private val technicalPlayerHead = Regex("""\[MCCTabPlayer#\d+\s+head]\s*""", RegexOption.IGNORE_CASE)

    fun crownColor(message: GuiMessage): CrownColor? = crownColor(message.content())

    fun crownColor(component: Component): CrownColor? {
        val rawText = component.string
        val plainText = rawText.trimStart()
        val leadingWhitespace = rawText.length - plainText.length
        val technicalHead = technicalPlayerHead.find(plainText)
        val playerText = technicalHead
            ?.let { marker -> plainText.substring(marker.range.last + 1).trimStart() }
            ?: plainText
        if (!looksLikeMcciPlayerChat(playerText)) return null

        val prefixEnd = rawText.indexOf(": ")
        val crownStart = technicalHead?.range?.last?.plus(leadingWhitespace + 1)
            ?: rawText.indexOfFirst { !it.isWhitespace() }
        for (characterIndex in crownStart until prefixEnd) {
            CrownColor.fromGlyph(rawText[characterIndex])?.let { return it }
        }
        return null
    }

    fun isMuted(component: Component, mutedCrownColors: Set<CrownColor>): Boolean =
        mutedCrownColors.isNotEmpty() && crownColor(component) in mutedCrownColors

    private fun looksLikeMcciPlayerChat(text: String): Boolean =
        text.isNotEmpty() && text[0].code in 0xE000..0xF8FF && text.indexOf(": ") > 0
}
