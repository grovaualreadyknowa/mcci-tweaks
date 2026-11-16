package dev.caden.mccitweaks.chat

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

/** Sorts an incoming MCCI chat line into one of the chat tabs, or `null` for All-only messages. */
object ChatMessageClassifier {
    private val privateMessagePrefix = Regex("""^\[[^\]\r\n]*\bPM\s+(?:From|To)\b[^\]\r\n]*]""", RegexOption.IGNORE_CASE)
    private val tradePrefix = Regex("""^\[Trade]""", RegexOption.IGNORE_CASE)
    private val partyPrefix = Regex("""^\[Party]""", RegexOption.IGNORE_CASE)
    private val plobbyPrefix = Regex("""^\[Plobby]""", RegexOption.IGNORE_CASE)
    private val technicalPlayerHeadPrefix = Regex("""^\[MCCTabPlayer#\d+\s+head]\s*""", RegexOption.IGNORE_CASE)
    private val roomPrefix = Regex("""^\[[A-Za-z][A-Za-z0-9_-]{1,15}]""")

    fun classify(message: GuiMessage): ChatTab? = classify(message.content())

    fun classify(component: Component): ChatTab? {
        val plainText = component.string.trimStart()
        val playerText = technicalPlayerHeadPrefix.replaceFirst(plainText, "")

        return when {
            privateMessagePrefix.containsMatchIn(plainText) -> ChatTab.PM
            tradePrefix.containsMatchIn(plainText) -> ChatTab.TRADE
            partyPrefix.containsMatchIn(plainText) -> ChatTab.PARTY
            plobbyPrefix.containsMatchIn(plainText) -> ChatTab.PLOBBY
            isRoomMessage(plainText) -> ChatTab.ROOMS
            looksLikeMcciPlayerMessage(playerText) && hasWhiteBody(component) -> ChatTab.LOCAL
            else -> null
        }
    }

    private fun isRoomMessage(plainText: String): Boolean {
        val prefix = roomPrefix.find(plainText) ?: return false
        return looksLikePlayerMessage(plainText.substring(prefix.range.last + 1).trimStart())
    }

    private fun looksLikePlayerMessage(text: String): Boolean = text.indexOf(": ") > 0

    private fun looksLikeMcciPlayerMessage(text: String): Boolean =
        looksLikePlayerMessage(text) && text.firstOrNull()?.isPrivateUse() == true

    internal fun Char.isPrivateUse(): Boolean = code in 0xE000..0xF8FF

    internal fun hasWhiteBody(component: Component): Boolean {
        val finalRun = component.toFlatList().lastOrNull { run -> run.string.isNotBlank() } ?: return false
        val color = finalRun.style.color?.value
        return color == null || color == 0xFFFFFF
    }
}
