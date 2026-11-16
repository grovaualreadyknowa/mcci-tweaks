package dev.caden.mccitweaks.chat

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatFiltersTest {
    @Test
    fun `detects a styled MCCI crown glyph before a player name`() {
        val message = playerChat(0xFF5555)

        assertEquals(CrownColor.LEVEL_60_69, CrownMessageClassifier.crownColor(message))
        assertTrue(CrownMessageClassifier.isMuted(message, setOf(CrownColor.LEVEL_60_69)))
        assertFalse(CrownMessageClassifier.isMuted(message, setOf(CrownColor.LEVEL_70_79)))
    }

    @Test
    fun `does not treat a MCCI server notice as player chat`() {
        val notice = Component.literal("[MCCTabPlayer#08 head]")
            .append(Component.literal("").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("").withStyle(ChatFormatting.RED))
            .append(Component.literal("Player has entered the lobby."))

        assertNull(CrownMessageClassifier.crownColor(notice))
    }

    @Test
    fun `does not infer a crown from ordinary colored chat text`() {
        assertNull(CrownMessageClassifier.crownColor(Component.literal("player: this text is red").withStyle(ChatFormatting.RED)))
    }

    @Test
    fun `combines crown mute with every chat tab`() {
        val message = Component.literal("[Trade] ").append(playerChat(0x55FFFF, ""))

        ChatTab.entries.forEach { tab ->
            assertFalse(ChatTabs.shouldDisplay(tab, message, setOf(CrownColor.LEVEL_70_79)))
        }
        assertTrue(ChatTabs.shouldDisplay(ChatTab.ALL, message, emptySet()))
    }

    @Test
    fun `maps every current MCCI crown glyph to its level band`() {
        val crowns = mapOf(
            '' to CrownColor.LEVEL_0_9,
            '' to CrownColor.LEVEL_10_19,
            '' to CrownColor.LEVEL_20_29,
            '' to CrownColor.LEVEL_30_39,
            '' to CrownColor.LEVEL_40_49,
            '' to CrownColor.LEVEL_50_59,
            '' to CrownColor.LEVEL_60_69,
            '' to CrownColor.LEVEL_70_79,
            '' to CrownColor.LEVEL_80_89,
            '' to CrownColor.LEVEL_90_99,
            '' to CrownColor.LEVEL_100_PLUS
        )

        crowns.forEach { (glyph, level) ->
            assertEquals(level, CrownMessageClassifier.crownColor(playerChat(0xFFFFFF, glyph.toString())))
        }
    }

    @Test
    fun `mutes exact low-level messages captured from live MCCI chat`() {
        val levelZero = Component.literal("[MCCTabPlayer#09 head]co5ine: why am I being beat up")
        val levelTen = Component.literal("[MCCTabPlayer#32 head]Kaithegoodguy: idk lol")
        val muted = setOf(CrownColor.LEVEL_0_9, CrownColor.LEVEL_10_19)

        assertEquals(CrownColor.LEVEL_0_9, CrownMessageClassifier.crownColor(levelZero))
        assertEquals(CrownColor.LEVEL_10_19, CrownMessageClassifier.crownColor(levelTen))
        assertTrue(CrownMessageClassifier.isMuted(levelZero, muted))
        assertTrue(CrownMessageClassifier.isMuted(levelTen, muted))
    }

    @Test
    fun `recognizes MCCI Info notices`() {
        val notice = Component.literal("[MCCTabPlayer#08 head]Info: Found a bug? Use /bugreport")
        assertTrue(InfoMessageFilter.isInfoMessage(notice))
        assertFalse(ChatTabs.shouldDisplay(ChatTab.ALL, notice, emptySet(), muteInfoMessages = true))

        assertTrue(InfoMessageFilter.isInfoMessage(Component.literal(String(Character.toChars(0xF0001)) + " Info: A server notice")))
        assertTrue(InfoMessageFilter.isInfoMessage(Component.literal("[MCCTabPlayer#8 head] Info: A server notice")))

        val multiline = Component.literal("        \n[] Info:\n\n Found a bug? Use /bugreport and send us a report.\n        ")
        assertTrue(InfoMessageFilter.isInfoMessage(multiline))
        assertFalse(ChatTabs.shouldDisplay(ChatTab.ALL, multiline, emptySet(), muteInfoMessages = true))
    }

    @Test
    fun `does not mute ordinary players mentioning Info`() {
        val playerMessage = Component.literal("player: Info: this is useful")
        assertFalse(InfoMessageFilter.isInfoMessage(playerMessage))
        assertTrue(ChatTabs.shouldDisplay(ChatTab.ALL, playerMessage, emptySet(), muteInfoMessages = true))
        assertFalse(InfoMessageFilter.isInfoMessage(Component.literal("Info: hello")))
    }

    @Test
    fun `shared session history limit is one thousand`() {
        assertEquals(1_000, ChatTabs.HISTORY_LIMIT)
    }

    @Test
    fun `all chat can hide a category without hiding its own tab`() {
        val plobby = Component.literal("[Plobby] Player: hello")
        assertFalse(ChatTabs.shouldDisplay(ChatTab.ALL, plobby, emptySet(), hiddenFromAllChat = setOf(ChatTab.PLOBBY)))
        assertTrue(ChatTabs.shouldDisplay(ChatTab.PLOBBY, plobby, emptySet(), hiddenFromAllChat = setOf(ChatTab.PLOBBY)))
        assertTrue(
            ChatTabs.shouldDisplay(
                ChatTab.ALL,
                Component.literal("Server restarting soon"),
                emptySet(),
                hiddenFromAllChat = setOf(ChatTab.PLOBBY)
            )
        )
    }

    @Test
    fun `uses MCCI tab colors before any messages are observed`() {
        ChatTabColors.restore(emptyMap()) { _, _ -> }

        assertEquals(0xFFFFFF, ChatTabColors.colorFor(ChatTab.ALL))
        assertEquals(0xAAABFE, ChatTabColors.colorFor(ChatTab.PARTY))
        assertEquals(0xFFFFFF, ChatTabColors.colorFor(ChatTab.LOCAL))
        assertEquals(0x00BB99, ChatTabColors.colorFor(ChatTab.TRADE))
    }

    private fun playerChat(rgb: Int, crown: String = ""): Component =
        Component.literal("[MCCTabPlayer#07 head]")
            .append(Component.literal("").withStyle(Style.EMPTY.withColor(0x555555)))
            .append(Component.literal("").withStyle(Style.EMPTY.withColor(rgb)))
            .append(Component.literal("$crownplayer: hello").withStyle(Style.EMPTY.withColor(0xFFFFFF)))
}
