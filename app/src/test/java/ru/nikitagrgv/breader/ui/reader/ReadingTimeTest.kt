package ru.nikitagrgv.breader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingTimeTest {
    private fun minutes(days: Long = 0, hours: Long = 0, minutes: Long = 0, seconds: Long = 0) =
        (((days * 24 + hours) * 60 + minutes) * 60 + seconds) * 1000

    @Test
    fun `nothing is shown under a minute`() {
        assertNull(formatReadingTime(0))
        assertNull(formatReadingTime(minutes(seconds = 59)))
    }

    @Test
    fun `larger units come in when there are some`() {
        assertEquals("1m", formatReadingTime(minutes(minutes = 1, seconds = 59)))
        assertEquals("47m", formatReadingTime(minutes(minutes = 47)))
        assertEquals("1h", formatReadingTime(minutes(hours = 1)))
        assertEquals("1h 12m", formatReadingTime(minutes(hours = 1, minutes = 12)))
        assertEquals("4d 5h 7m", formatReadingTime(minutes(days = 4, hours = 5, minutes = 7)))
        assertEquals("124d 5h 7m", formatReadingTime(minutes(days = 124, hours = 5, minutes = 7)))
    }

    @Test
    fun `zero units in between are left out`() {
        assertEquals("1d", formatReadingTime(minutes(days = 1)))
        assertEquals("2d 3m", formatReadingTime(minutes(days = 2, minutes = 3)))
    }
}
