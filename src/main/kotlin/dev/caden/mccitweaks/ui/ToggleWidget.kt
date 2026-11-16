package dev.caden.mccitweaks.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB

/** A boolean config row rendered with a checkbox on the right hand side. */
class ToggleWidget(
    label: Component,
    private val getter: () -> Boolean,
    private val setter: (Boolean) -> Unit,
    private val defaultValue: Boolean
) : OptionWidget(label) {

    init {
        updateMessage()
    }

    override fun onPress(input: InputWithModifiers) {
        setValue(!getter())
    }

    override fun resetValue() {
        setValue(defaultValue)
    }

    override fun isDefault(): Boolean = getter() == defaultValue

    private fun setValue(value: Boolean) {
        setter(value)
        updateMessage()
    }

    private fun updateMessage() {
        message = CommonComponents.optionNameValue(
            label(),
            if (getter()) CommonComponents.OPTION_ON else CommonComponents.OPTION_OFF
        )
    }

    override fun extractValue(graphics: GuiGraphicsExtractor, font: Font, textY: Int, color: Int) {
        val selected = getter()
        val highlighted = isHoveredOrFocused
        val sprite = when {
            selected && highlighted -> CHECKBOX_SELECTED_HIGHLIGHTED
            selected -> CHECKBOX_SELECTED
            highlighted -> CHECKBOX_HIGHLIGHTED
            else -> CHECKBOX
        }

        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            sprite,
            x + width - PADDING - BOX_SIZE,
            y + (height - BOX_SIZE) / 2,
            BOX_SIZE,
            BOX_SIZE,
            ARGB.white(alpha)
        )
    }

    companion object {
        private val CHECKBOX = Identifier.withDefaultNamespace("widget/checkbox")
        private val CHECKBOX_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_highlighted")
        private val CHECKBOX_SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected")
        private val CHECKBOX_SELECTED_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted")
        private const val BOX_SIZE = 13
    }
}
