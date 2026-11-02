package ru.nikitagrgv.breader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.TextPosition

class SelectionHistoryTest {
    private fun range(paragraph: Int, start: Int, end: Int) =
        BookRange(TextPosition(paragraph, start), TextPosition(paragraph, end))

    private val a = range(0, 0, 5)
    private val b = range(1, 0, 5)
    private val c = range(2, 0, 5)

    private fun SelectionHistory.steps() = stepsBack to stepsForward

    @Test
    fun `a book starts on top of its history`() {
        val history = SelectionHistory(listOf(a, b))
        assertEquals(2 to 0, history.steps())
        assertNull(history.current)
        assertFalse(history.isWalking)
    }

    @Test
    fun `there's nowhere to go in an empty history`() {
        assertEquals(0 to 0, SelectionHistory().steps())
    }

    @Test
    fun `what's selected is on top, with nowhere to go forward`() {
        val history = SelectionHistory(listOf(a)).selected(b)
        assertEquals(b, history.current)
        assertEquals(1 to 0, history.steps())
    }

    @Test
    fun `going back from a selection and forward again ends on it`() {
        val top = SelectionHistory(listOf(a)).selected(b)
        val back = top.back()
        assertEquals(a, back.current)
        assertEquals(0 to 1, back.steps())
        assertTrue(back.isWalking)
        val forward = back.forward()
        assertEquals(b, forward.current)
        assertEquals(1 to 0, forward.steps())
        assertFalse(forward.isWalking)
    }

    @Test
    fun `going back with nothing selected and forward again ends with nothing selected`() {
        val back = SelectionHistory(listOf(a, b)).back()
        assertEquals(b, back.current)
        assertEquals(1 to 1, back.steps())
        val forward = back.forward()
        assertNull(forward.current)
        assertFalse(forward.isWalking)
        assertEquals(2 to 0, forward.steps())
    }

    @Test
    fun `clearing the selection on top leaves the reader above the newest entry`() {
        val cleared = SelectionHistory(listOf(a)).selected(b).cleared()
        assertNull(cleared.current)
        assertEquals(2 to 0, cleared.steps())
        assertEquals(b, cleared.back().current)
    }

    @Test
    fun `clearing the selection in a walk keeps the walk`() {
        val cleared = SelectionHistory(listOf(a, b, c)).back().back().cleared()
        assertEquals(b, cleared.current)
        assertEquals(1 to 2, cleared.steps())
        assertEquals(a, cleared.back().current)
        assertEquals(c, cleared.forward().current)
    }

    @Test
    fun `the way back is a step at a time and all at once`() {
        val history = SelectionHistory(listOf(a, b, c)).back().back().back()
        assertEquals(a, history.current)
        val home = history.toHome()
        assertNull(home.current)
        assertFalse(home.isWalking)
    }

    @Test
    fun `changing the selection updates its entry instead of adding one`() {
        val wider = range(1, 0, 9)
        val history = SelectionHistory(listOf(a)).selected(b).selected(wider)
        assertEquals(listOf(a, wider), history.entries)
        assertEquals(wider, history.current)
    }

    @Test
    fun `changing a selection reached in a walk stays in its place`() {
        val wider = range(1, 0, 9)
        val history = SelectionHistory(listOf(a, b, c)).back().back().selected(wider)
        assertEquals(listOf(a, wider, c), history.entries)
        assertEquals(1 to 2, history.steps())
    }

    @Test
    fun `selecting elsewhere while something is selected adds an entry`() {
        val history = SelectionHistory().selected(a).selected(b)
        assertEquals(listOf(a, b), history.entries)
    }

    @Test
    fun `selecting over the old place after clearing adds an entry`() {
        val inside = range(0, 1, 3)
        val history = SelectionHistory().selected(a).cleared().selected(inside)
        assertEquals(listOf(a, inside), history.entries)
    }

    @Test
    fun `a new selection in a walk goes on top and nothing is lost`() {
        val history = SelectionHistory(listOf(a, b)).back().back().cleared().selected(c)
        assertEquals(listOf(a, b, c), history.entries)
        assertEquals(c, history.current)
        assertEquals(2 to 0, history.steps())
        assertFalse(history.isWalking)
    }

    @Test
    fun `selecting the same thing again moves it to the top`() {
        val history = SelectionHistory(listOf(a, b, c)).selected(a)
        assertEquals(listOf(b, c, a), history.entries)
        assertEquals(a, history.current)
    }

    @Test
    fun `what a walk selects doesn't change the history`() {
        val walk = SelectionHistory(listOf(a, b)).back()
        assertEquals(walk, walk.selected(b))
    }

    @Test
    fun `selecting again what the walk has cleared picks it up again`() {
        val walk = SelectionHistory(listOf(a, b, c)).back().back()
        val again = walk.cleared().selected(b)
        assertEquals(walk, again)
    }

    @Test
    fun `the history is limited, losing the oldest`() {
        var history = SelectionHistory()
        for (i in 0 until 205) history = history.cleared().selected(range(i, 0, 1))
        assertEquals(200, history.entries.size)
        assertEquals(range(5, 0, 1), history.entries.first())
        assertEquals(range(204, 0, 1), history.current)
    }

    @Test
    fun `a place that's gone is dropped without moving the walk`() {
        val walk = SelectionHistory(listOf(a, b, c)).back().back()
        assertEquals(b, walk.current)
        val history = walk.without(a)
        assertEquals(listOf(b, c), history.entries)
        assertEquals(b, history.current)
        assertEquals(0 to 2, history.steps())
    }
}
