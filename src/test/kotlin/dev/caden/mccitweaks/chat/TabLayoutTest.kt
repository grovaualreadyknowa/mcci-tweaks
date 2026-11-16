package dev.caden.mccitweaks.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabLayoutTest {
    private val measure: (String) -> Int = { it.length * 6 }

    @Test
    fun `uses requested labels when they fit`() {
        val bounds = TabLayout.create(maxWidth = 320, chatBottom = 100, measureText = measure)

        assertEquals(ChatTab.entries.map(ChatTab::label), bounds.map(TabBounds::label))
        assertEquals(102, bounds.first().top)
        assertEquals(12, bounds.first().height)
        assertContiguousWithin(bounds, 320)
    }

    @Test
    fun `uses one letter labels at minimum chat width`() {
        val bounds = TabLayout.create(maxWidth = 40, chatBottom = 200, measureText = measure)

        assertEquals(listOf("A", "Y", "R", "P", "L", "T", "B"), bounds.map(TabBounds::label))
        assertEquals(40, bounds.sumOf(TabBounds::width))
        assertContiguousWithin(bounds, 40)
    }

    @Test
    fun `scaled local widths remain bounded and clickable`() {
        val bounds = TabLayout.create(maxWidth = 160, chatBottom = 300, measureText = measure)
        val pm = bounds.single { it.tab == ChatTab.PM }

        assertTrue(pm.contains(pm.left + 0.5f, pm.top + 0.5f))
        assertFalse(pm.contains(pm.right.toFloat(), pm.top + 0.5f))
        assertContiguousWithin(bounds, 160)
    }

    private fun assertContiguousWithin(bounds: List<TabBounds>, maxWidth: Int) {
        bounds.zipWithNext().forEach { (left, right) -> assertEquals(left.right, right.left) }
        assertTrue(bounds.first().left >= 0)
        assertTrue(bounds.last().right <= maxWidth)
    }
}
