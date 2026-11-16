package dev.caden.mccitweaks.chat

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

/** Recognizes MCCI server notices labelled "Info:" by their technical marker or icon prefix. */
object InfoMessageFilter {
    private val technicalPlayerHead = Regex("""^\[MCCTabPlayer#\d+\s+head]\s*""", RegexOption.IGNORE_CASE)

    fun isInfoMessage(message: GuiMessage): Boolean = isInfoMessage(message.content())

    fun isInfoMessage(component: Component): Boolean {
        val message = component.string.trimStart()
        val technicalHead = technicalPlayerHead.find(message)
        val text = technicalHead?.let { message.substring(it.range.last + 1) } ?: message
        var offset = 0
        var hasIcon = false

        fun skipIconAndWhitespace() {
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                if (!codePoint.isPrivateUse() && !Character.isWhitespace(codePoint)) break
                hasIcon = hasIcon || codePoint.isPrivateUse()
                offset += Character.charCount(codePoint)
            }
        }

        fun skipWhitespace() {
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                if (!Character.isWhitespace(codePoint)) break
                offset += Character.charCount(codePoint)
            }
        }

        skipIconAndWhitespace()

        // MCCI's large multiline notices wrap the icon in square brackets: "[icon] Info:".
        if (!hasIcon && offset < text.length && text[offset] == '[') {
            val bracketOffset = offset
            offset++
            skipIconAndWhitespace()

            if (hasIcon && offset < text.length && text[offset] == ']') {
                offset++
            } else {
                hasIcon = false
                offset = bracketOffset
            }
        }
        skipWhitespace()

        // The marker or icon distinguishes server notices from a player named "Info".
        if (technicalHead == null && !hasIcon) return false

        return text.regionMatches(offset, "Info:", 0, "Info:".length, ignoreCase = true)
    }

    private fun Int.isPrivateUse(): Boolean =
        this in 0xE000..0xF8FF || this in 0xF0000..0xFFFFD || this in 0x100000..0x10FFFD
}
