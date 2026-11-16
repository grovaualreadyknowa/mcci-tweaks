package dev.caden.mccitweaks.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.ResettableOptionWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

/** A full width config row: the option name is drawn on the left, the current value on the right. */
abstract class OptionWidget protected constructor(private val label: Component) :
    AbstractButton(0, 0, 310, 20, label), ResettableOptionWidget {

    fun label(): Component = label

    /** Whether the current value already matches the default, used to grey out the reset button. */
    abstract fun isDefault(): Boolean

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractDefaultSprite(graphics)

        val font = Minecraft.getInstance().font
        val color = if (active) ACTIVE_TEXT else INACTIVE_TEXT
        val textY = y + (height - 9) / 2 + 1
        graphics.text(font, label, x + PADDING, textY, color)
        extractValue(graphics, font, textY, color)
    }

    /** Draws the value, right aligned inside the row. */
    protected abstract fun extractValue(graphics: GuiGraphicsExtractor, font: Font, textY: Int, color: Int)

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    companion object {
        const val ACTIVE_TEXT: Int = 0xFFFFFFFF.toInt()
        const val INACTIVE_TEXT: Int = 0xFFA0A0A0.toInt()
        const val PADDING: Int = 8
    }
}
