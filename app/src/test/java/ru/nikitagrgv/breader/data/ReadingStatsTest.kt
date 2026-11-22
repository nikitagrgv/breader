package ru.nikitagrgv.breader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingStatsTest {
    private fun stats(progress: Float, words: Int?, times: Map<Int, Long>) = ReadingStats(
        timeByPercent = times.entries.fold(emptyList()) { list, (percent, ms) -> list.plusTime(percent, ms) },
        totalMs = times.values.sum(),
        progress = progress,
        wordCount = words,
    )

    @Test
    fun `time is added to its percent`() {
        val times = emptyList<Long>().plusTime(3, 100).plusTime(3, 50).plusTime(99, 1)
        assertEquals(PERCENTS, times.size)
        assertEquals(150L, times[3])
        assertEquals(1L, times[99])
        assertEquals(151L, times.sum())
    }

    @Test
    fun `percent of a position`() {
        assertEquals(0, percentOf(0f))
        assertEquals(17, percentOf(0.1799f))
        // The end of the book is in the last percent.
        assertEquals(99, percentOf(1f))
    }

    @Test
    fun `speed is told by the finished percents read at a plausible pace`() {
        // 1,000 words a percent: 10 minutes is 6,000 words an hour, 5 minutes is 12,000.
        val stats = stats(
            progress = 0.035f,
            words = 100_000,
            times = mapOf(0 to 600_000L, 1 to 300_000L, 2 to 10_000L, 3 to 120_000L),
        )
        assertEquals(6_000.0, stats.speeds[0]!!, 0.01)
        assertEquals(12_000.0, stats.speeds[1]!!, 0.01)
        // 1,000 words in 10 s was skipped through.
        assertNull(stats.speeds[2])
        // Still being read.
        assertNull(stats.speeds[3])
        assertNull(stats.speeds[4])
        // 2,000 words in 15 minutes.
        assertEquals(8_000.0, stats.averageSpeed!!, 0.01)
        // 96,500 words left.
        assertEquals(96_500 / 8_000.0 * 3_600_000, stats.timeLeftMs!!.toDouble(), 1_000.0)
        assertEquals(3_500, stats.wordsRead)
        assertEquals(1_030_000L, stats.trackedMs)
    }

    @Test
    fun `without counted words there's no speed`() {
        val stats = stats(progress = 0.5f, words = null, times = mapOf(0 to 600_000L))
        assertNull(stats.speeds[0])
        assertNull(stats.averageSpeed)
        assertNull(stats.timeLeftMs)
        assertNull(stats.wordsRead)
    }
}
