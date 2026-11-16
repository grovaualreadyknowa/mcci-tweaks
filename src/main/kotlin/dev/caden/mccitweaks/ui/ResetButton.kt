package dev.caden.mccitweaks.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.network.chat.Component

/** Small square button that restores a single option to its default value. */
class ResetButton(optionLabel: Component, private val onReset: () -> Unit) :
    AbstractButton(0, 0, SIZE, SIZE, Component.literal("Reset")) {

    init {
        setTooltip(Tooltip.create(Component.literal("Reset ").append(optionLabel)))
    }

    override fun onPress(input: InputWithModifiers) {
        onReset()
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractDefaultSprite(graphics)

        val color = if (active) ICON_ACTIVE else ICON_INACTIVE
        val left = x + (width - ICON[0].length) / 2
        val top = y + (height - ICON.size) / 2
        ICON.forEachIndexed { row, line ->
            line.forEachIndexed { column, pixel ->
                if (pixel == '#') {
                    graphics.fill(left + column, top + row, left + column + 1, top + row + 1, color)
                }
            }
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    companion object {
        const val SIZE = 20

        /** 8x8 circular arrow, drawn with plain rectangles so no extra texture has to ship with the mod. */
        private val ICON = listOf(
            "..####..",
            ".##..##.",
            "##....#.",
            "##...###",
            "##....#.",
            ".##..##.",
            "..####..",
            "........"
        )
        private const val ICON_ACTIVE = 0xFFDDDDDD.toInt()
        private const val ICON_INACTIVE = 0xFF6E6E6E.toInt()
    }
}
