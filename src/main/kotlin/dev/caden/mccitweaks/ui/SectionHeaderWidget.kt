package dev.caden.mccitweaks.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.network.chat.Component

/** Collapsible category header; the whole row toggles the section it belongs to. */
class SectionHeaderWidget(private val title: Component, private val onToggle: () -> Unit) :
    AbstractButton(0, 0, 310, 18, title) {

    var isExpanded: Boolean = true

    override fun onPress(input: InputWithModifiers) {
        isExpanded = !isExpanded
        onToggle()
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val font = Minecraft.getInstance().font
        val textY = y + (height - 9) / 2 + 1
        graphics.centeredText(font, title, x + width / 2, textY, TEXT_COLOR)
        extractArrow(graphics, x + 6, y + height / 2)
    }

    private fun extractArrow(graphics: GuiGraphicsExtractor, x: Int, centerY: Int) {
        if (isExpanded) {
            for (row in 0 until 3) {
                graphics.fill(x + row, centerY - 2 + row, x + 6 - row, centerY - 1 + row, ARROW_COLOR)
            }
        } else {
            for (column in 0 until 3) {
                graphics.fill(x + 1 + column, centerY - 3 + column, x + 2 + column, centerY + 3 - column, ARROW_COLOR)
            }
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    companion object {
        private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val ARROW_COLOR = 0xFFD0D0D0.toInt()
    }
}
