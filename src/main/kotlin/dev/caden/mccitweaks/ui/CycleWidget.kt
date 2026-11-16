package dev.caden.mccitweaks.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

/** A config row that cycles through a fixed list of values, showing the current one on the right. */
class CycleWidget<T : Any>(
    label: Component,
    values: List<T>,
    private val getter: () -> T?,
    private val setter: (T) -> Unit,
    private val stringifier: (T) -> Component,
    private val defaultValue: T
) : OptionWidget(label) {
    private val values: List<T> = values.toList()

    init {
        updateMessage()
    }

    override fun onPress(input: InputWithModifiers) {
        val index = getter()?.let { values.indexOf(it) } ?: -1
        val next = Mth.positiveModulo(index + if (input.hasShiftDown()) -1 else 1, values.size)
        setValue(values[next])
    }

    override fun resetValue() {
        setValue(defaultValue)
    }

    override fun isDefault(): Boolean = defaultValue == getter()

    private fun setValue(value: T) {
        setter(value)
        updateMessage()
    }

    private fun updateMessage() {
        message = CommonComponents.optionNameValue(label(), valueText())
    }

    private fun valueText(): Component = getter()?.let(stringifier) ?: CommonComponents.EMPTY

    override fun extractValue(graphics: GuiGraphicsExtractor, font: Font, textY: Int, color: Int) {
        val value = valueText()
        val valueX = x + width - PADDING - font.width(value)
        graphics.text(font, value, valueX, textY, if (active) VALUE_COLOR else INACTIVE_TEXT)
    }

    companion object {
        private const val VALUE_COLOR = 0xFFA9C0FF.toInt()
    }
}
