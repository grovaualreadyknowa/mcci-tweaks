package dev.caden.mccitweaks.chat

import com.mojang.blaze3d.platform.cursor.CursorTypes
import kotlin.math.ceil
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ChatComponent
import org.joml.Matrix3x2f
import org.joml.Vector2f

/** Draws the clickable tab row just below the chat window. */
object ChatTabBar {
    private const val CHAT_X = 4f
    private const val SELECTED_BACKGROUND = 0xD02A405A.toInt()
    private const val HOVERED_BACKGROUND = 0xC04A6078.toInt()
    private const val IDLE_BACKGROUND = 0xA0000000.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, displayMode: ChatComponent.DisplayMode) {
        if (!ChatSeparator.isEnabled()) return
        val config = ChatSeparator.config()
        if (!displayMode.foreground && config.tabVisibility != ChatSeparatorConfig.TabVisibility.ALWAYS_VISIBLE) return

        val minecraft = Minecraft.getInstance()
        val geometry = geometry(graphics.guiHeight())
        val bounds = TabLayout.create(geometry.maxWidth, geometry.chatBottom, minecraft.font::width)
        if (bounds.isEmpty()) return

        graphics.pose().pushMatrix()
        graphics.pose().scale(geometry.scale, geometry.scale)
        graphics.pose().translate(CHAT_X, 0f)

        val localMouse = Vector2f()
        graphics.pose().invert(Matrix3x2f()).transformPosition(mouseX.toFloat(), mouseY.toFloat(), localMouse)

        var hovering = false
        for (button in bounds) {
            val hovered = displayMode.foreground && button.contains(localMouse.x, localMouse.y)
            hovering = hovering || hovered
            val background = when {
                button.tab == ChatTabs.selected -> SELECTED_BACKGROUND
                hovered -> HOVERED_BACKGROUND
                else -> IDLE_BACKGROUND
            }

            graphics.fill(button.left, button.top, button.right, button.bottom, background)
            val textX = button.left + (button.width - minecraft.font.width(button.label)) / 2
            val textColor = if (config.coloredTabLabels) {
                ChatTabColors.colorFor(button.tab) or 0xFF000000.toInt()
            } else {
                TEXT_COLOR
            }
            graphics.text(minecraft.font, button.label, textX, button.top + 2, textColor, true)
        }

        if (hovering) {
            graphics.requestCursor(CursorTypes.POINTING_HAND)
        }

        graphics.pose().popMatrix()
    }

    @JvmStatic
    fun handleClick(mouseX: Double, mouseY: Double): Boolean {
        if (!ChatSeparator.isEnabled()) return false

        val minecraft = Minecraft.getInstance()
        val geometry = geometry(minecraft.window.guiScaledHeight)
        val bounds = TabLayout.create(geometry.maxWidth, geometry.chatBottom, minecraft.font::width)

        val localMouse = Vector2f()
        Matrix3x2f()
            .scale(geometry.scale, geometry.scale)
            .translate(CHAT_X, 0f)
            .invert(Matrix3x2f())
            .transformPosition(mouseX.toFloat(), mouseY.toFloat(), localMouse)

        val clicked = bounds.firstOrNull { it.contains(localMouse.x, localMouse.y) } ?: return false
        ChatTabs.select(clicked.tab, minecraft.gui.hud.chat)
        return true
    }

    private fun geometry(guiHeight: Int): Geometry {
        val minecraft = Minecraft.getInstance()
        val scale = minecraft.options.chatScale().get().toFloat()
        val width = ChatComponent.getWidth(minecraft.options.chatWidth().get())
        return Geometry(
            scale = scale,
            maxWidth = ceil(width / scale).toInt(),
            chatBottom = floor((guiHeight - 40) / scale).toInt()
        )
    }

    private data class Geometry(val scale: Float, val maxWidth: Int, val chatBottom: Int)
}
