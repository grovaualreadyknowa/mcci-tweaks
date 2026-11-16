package dev.caden.mccitweaks.chat

internal data class TabBounds(
    val tab: ChatTab,
    val label: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

internal object TabLayout {
    private const val BUTTON_HEIGHT = 12

    fun create(maxWidth: Int, chatBottom: Int, measureText: (String) -> Int): List<TabBounds> {
        if (maxWidth <= 0) return emptyList()

        val tabs = ChatTab.entries
        val full = tabs.map { it.label }
        val compact = tabs.map { it.narrowLabel }
        val (labels, padding) = when {
            totalWidth(full, 4, measureText) <= maxWidth -> full to 4
            totalWidth(full, 2, measureText) <= maxWidth -> full to 2
            else -> compact to 1
        }

        val preferredWidths = labels.map { measureText(it) + padding * 2 }
        val widths = if (preferredWidths.sum() <= maxWidth) preferredWidths else equalWidths(maxWidth, tabs.size)

        var x = 0
        return tabs.indices.map { index ->
            TabBounds(tabs[index], labels[index], x, chatBottom + 2, widths[index], BUTTON_HEIGHT)
                .also { x += widths[index] }
        }
    }

    private fun totalWidth(labels: List<String>, padding: Int, measureText: (String) -> Int): Int =
        labels.sumOf { measureText(it) + padding * 2 }

    private fun equalWidths(maxWidth: Int, count: Int): List<Int> {
        val base = maxWidth / count
        val remainder = maxWidth % count
        return List(count) { index -> base + if (index < remainder) 1 else 0 }
    }
}
