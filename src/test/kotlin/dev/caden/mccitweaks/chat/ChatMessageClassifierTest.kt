package dev.caden.mccitweaks.chat

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatMessageClassifierTest {
    @Test
    fun `recognizes private messages with decorative prefix glyphs`() {
        assertEquals(ChatTab.PM, classify("[✉ PM From] isleofgasoline: hello"))
        assertEquals(ChatTab.PM, classify("[♙ PM To] isleofgasoline: hello"))
        assertEquals(ChatTab.PM, classify("[pm from] player: hello"))
    }

    @Test
    fun `recognizes trade case insensitively`() {
        assertEquals(ChatTab.TRADE, classify("[Trade] Uniaro: looking for flower wings"))
        assertEquals(ChatTab.TRADE, classify("[trade] Uniaro: buying materials"))
    }

    @Test
    fun `recognizes party and plobby before generic rooms`() {
        assertEquals(ChatTab.PARTY, classify("[Party] Uniaro: ready to play"))
        assertEquals(ChatTab.PARTY, classify("[party] Uniaro: joining now"))
        assertEquals(ChatTab.PLOBBY, classify("[Plobby] Uniaro: hello lobby"))
        assertEquals(ChatTab.PLOBBY, classify("[plobby] Uniaro: welcome"))
    }

    @Test
    fun `recognizes known and arbitrary bracketed rooms`() {
        assertEquals(ChatTab.ROOMS, classify("[BREW] brewhe: cute"))
        assertEquals(ChatTab.ROOMS, classify("[BREWHE] brewhe: ok expected"))
        assertEquals(ChatTab.ROOMS, classify("[Raid_7] Player: ready"))
        assertEquals(
            ChatTab.ROOMS,
            classify("[BREWHE] [MCCTabPlayer#07 head]brewhe: i need to feed my cats")
        )
    }

    @Test
    fun `ignores MCCI player head metadata before local chat`() {
        assertEquals(
            ChatTab.LOCAL,
            classify("[MCCTabPlayer#07 head]brewhe: blame drp")
        )
    }

    @Test
    fun `does not treat MCCI metadata or server notices as rooms`() {
        assertNull(classify("[MCCTabPlayer#08 head]frootbewwies has entered the lobby."))
        assertNull(classify("[isleofgasoline head]isleofgasoline has gone offline."))
        assertNull(classify("[] A Strong Tide has begun on this island. While active: +20% Strong Hook"))
        assertNull(classify("[A] A Strong Tide has begun on this island. While active: +20% Strong Hook"))
    }

    @Test
    fun `uses category prefixes before the white body rule`() {
        val whiteTrade = Component.literal("[Trade] ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal("white body").withStyle(ChatFormatting.WHITE))
        val whiteRoom = Component.literal("[BREW] player: ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal("white body").withStyle(ChatFormatting.WHITE))

        assertEquals(ChatTab.TRADE, ChatMessageClassifier.classify(whiteTrade))
        assertEquals(ChatTab.ROOMS, ChatMessageClassifier.classify(whiteRoom))
    }

    @Test
    fun `recognizes white and default colored local bodies`() {
        val whiteBody = Component.literal("[MCCTabPlayer#01 head]rank player: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("there is a fishing spot here").withStyle(ChatFormatting.WHITE))

        assertEquals(ChatTab.LOCAL, ChatMessageClassifier.classify(whiteBody))
        assertEquals(ChatTab.LOCAL, classify("player: just doin some fishing"))
    }

    @Test
    fun `ignores trailing whitespace when finding the body color`() {
        val pinkBody = Component.literal("player: stay purple forever").withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal("   "))

        assertNull(ChatMessageClassifier.classify(pinkBody))
    }

    @Test
    fun `colored unmatched chat remains all only`() {
        val global = Component.literal(
            "[MCCTabPlayer#25 head]isleofgasoline: this can sound mean"
        ).withStyle(ChatFormatting.LIGHT_PURPLE)
        assertNull(ChatMessageClassifier.classify(global))
    }

    private fun classify(text: String): ChatTab? =
        ChatMessageClassifier.classify(Component.literal(text))
}
