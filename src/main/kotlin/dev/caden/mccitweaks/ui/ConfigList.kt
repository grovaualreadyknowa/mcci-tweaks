package dev.caden.mccitweaks.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component

/**
 * Scrolling list of config rows grouped into collapsible sections. Each option row shows the option
 * name on the left, the value on the right and a reset button just outside the row.
 */
class ConfigList(minecraft: Minecraft, width: Int, height: Int, y: Int) :
    ContainerObjectSelectionList<ConfigList.Entry>(minecraft, width, height, y, OPTION_HEIGHT) {

    private val sections = ArrayList<Section>()
    private val tickHandlers = ArrayList<() -> Unit>()
    private var currentSection: Section? = null

    init {
        centerListVertically = false
    }

    override fun getRowWidth(): Int = ROW_WIDTH

    fun beginSection(title: Component, visible: () -> Boolean = { true }) {
        val section = Section(title, if (sections.isEmpty()) 0 else HEADER_TOP_PADDING, ::refresh, visible)
        sections += section
        currentSection = section
    }

    fun addOption(widget: OptionWidget, visible: () -> Boolean = { true }) {
        addRow(OptionEntry(widget), OPTION_HEIGHT, visible)
    }

    fun addWidget(widget: AbstractWidget, visible: () -> Boolean = { true }) {
        addRow(WidgetEntry(widget), OPTION_HEIGHT, visible)
    }

    fun addText(text: () -> Component, color: () -> Int, visible: () -> Boolean = { true }) {
        addRow(TextEntry(text, color), TEXT_HEIGHT, visible)
    }

    /** Registers a callback run every client tick while the owning screen is open. */
    fun addTickHandler(handler: () -> Unit) {
        tickHandlers += handler
    }

    fun tick() {
        tickHandlers.forEach { it() }
    }

    private fun addRow(entry: Entry, height: Int, visible: () -> Boolean) {
        if (currentSection == null) {
            beginSection(Component.empty())
        }
        currentSection!!.rows += Row(entry, height, visible)
    }

    /** Rebuilds the visible entries after a section was collapsed or a row changed visibility. */
    fun refresh() {
        val scroll = scrollAmount()
        setFocused(null)
        clearEntries()

        for (section in sections) {
            if (!section.visible()) continue
            addEntry(section.header, HEADER_HEIGHT + section.header.paddingTop)
            if (!section.header.widget.isExpanded) continue
            for (row in section.rows) {
                if (row.visible()) {
                    addEntry(row.entry, row.height)
                }
            }
        }

        setScrollAmount(scroll)
        // Collapsing a section can shrink the content, so re-run the layout with the clamped scroll.
        updateSizeAndPosition(getWidth(), getHeight(), getX(), getY())
    }

    private data class Row(val entry: Entry, val height: Int, val visible: () -> Boolean)

    private class Section(title: Component, paddingTop: Int, onToggle: () -> Unit, val visible: () -> Boolean) {
        val header = SectionEntry(title, paddingTop, onToggle)
        val rows = ArrayList<Row>()
    }

    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    private class SectionEntry(title: Component, val paddingTop: Int, onToggle: () -> Unit) : Entry() {
        val widget = SectionHeaderWidget(title, onToggle)

        override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float) {
            widget.setWidth(contentWidth)
            widget.setPosition(contentX, contentY + paddingTop)
            widget.extractRenderState(graphics, mouseX, mouseY, delta)
        }

        override fun children(): List<GuiEventListener> = listOf(widget)

        override fun narratables(): List<NarratableEntry> = listOf(widget)
    }

    private class OptionEntry(private val widget: OptionWidget) : Entry() {
        private val reset = ResetButton(widget.label()) { widget.resetValue() }

        override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float) {
            val left = contentX
            val right = contentRight
            val middle = contentYMiddle

            widget.setWidth(right - left - ResetButton.SIZE - CONTROL_GAP)
            widget.setPosition(left, middle - widget.getHeight() / 2)
            reset.setPosition(right - ResetButton.SIZE, middle - ResetButton.SIZE / 2)
            reset.active = widget.active && !widget.isDefault()

            widget.extractRenderState(graphics, mouseX, mouseY, delta)
            reset.extractRenderState(graphics, mouseX, mouseY, delta)
        }

        override fun children(): List<GuiEventListener> = listOf(widget, reset)

        override fun narratables(): List<NarratableEntry> = listOf(widget, reset)
    }

    private class WidgetEntry(private val widget: AbstractWidget) : Entry() {
        override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float) {
            widget.setWidth(contentWidth)
            widget.setPosition(contentX, contentYMiddle - widget.getHeight() / 2)
            widget.extractRenderState(graphics, mouseX, mouseY, delta)
        }

        override fun children(): List<GuiEventListener> = listOf(widget)

        override fun narratables(): List<NarratableEntry> = listOf(widget)
    }

    private class TextEntry(private val text: () -> Component, private val color: () -> Int) : Entry() {
        override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float) {
            graphics.centeredText(Minecraft.getInstance().font, text(), contentXMiddle, contentYMiddle - 4, color())
        }

        override fun children(): List<GuiEventListener> = emptyList()

        override fun narratables(): List<NarratableEntry> = emptyList()
    }

    companion object {
        const val ROW_WIDTH = 340

        private const val OPTION_HEIGHT = 25
        private const val TEXT_HEIGHT = 12
        private const val HEADER_HEIGHT = 20
        private const val HEADER_TOP_PADDING = 10
        private const val CONTROL_GAP = 4
    }
}
