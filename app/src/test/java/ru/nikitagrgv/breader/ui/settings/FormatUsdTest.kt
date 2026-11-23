package ru.nikitagrgv.breader.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUsdTest {
    @Test
    fun `cents from a dollar up`() {
        assertEquals("$1.00", formatUsd(0.9999965))
        assertEquals("$1.00", formatUsd(1.0))
        assertEquals("$33.55", formatUsd(33.54651598))
        assertEquals("$1234.50", formatUsd(1234.5))
    }

    @Test
    fun `two significant digits below a dollar`() {
        assertEquals("$0.45", formatUsd(0.45348402))
        assertEquals("$0.50", formatUsd(0.5))
        assertEquals("$0.012", formatUsd(0.0123))
        assertEquals("$0.00000035", formatUsd(3.5e-7))
        assertEquals("$0.000067", formatUsd(6.72e-5))
        assertEquals("$0.00001", formatUsd(1e-5))
    }

    @Test
    fun `zero and debt`() {
        assertEquals("$0.00", formatUsd(0.0))
        assertEquals("-$0.012", formatUsd(-0.0123))
    }
}
