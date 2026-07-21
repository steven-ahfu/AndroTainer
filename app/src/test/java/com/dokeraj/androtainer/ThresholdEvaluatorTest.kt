package com.dokeraj.androtainer

import com.dokeraj.androtainer.models.AlertState
import com.dokeraj.androtainer.models.ContainerThreshold
import com.dokeraj.androtainer.models.KontainerStats
import com.dokeraj.androtainer.util.ThresholdEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdEvaluatorTest {

    private fun stats(cpuPct: Double = 0.0, memMib: Double = 0.0) = KontainerStats(
        cpuPct = cpuPct,
        memUsedBytes = (memMib * 1048576).toLong(),
        memLimitBytes = 0L)

    private val threshold = ContainerThreshold("app", cpuPct = 80.0, memMib = 500.0)

    @Test
    fun `cpu alert requires sustained streak`() {
        val state = AlertState()
        val high = stats(cpuPct = 95.0)

        assertTrue(ThresholdEvaluator.evaluate("app", high, threshold, state, 1000L).isEmpty())
        assertTrue(ThresholdEvaluator.evaluate("app", high, threshold, state, 1100L).isEmpty())
        val third = ThresholdEvaluator.evaluate("app", high, threshold, state, 1200L)
        assertEquals(1, third.size)
        assertEquals("cpu:app", third[0].key)
    }

    @Test
    fun `dip below threshold resets the cpu streak`() {
        val state = AlertState()
        val high = stats(cpuPct = 95.0)
        val low = stats(cpuPct = 10.0)

        ThresholdEvaluator.evaluate("app", high, threshold, state, 1000L)
        ThresholdEvaluator.evaluate("app", high, threshold, state, 1100L)
        ThresholdEvaluator.evaluate("app", low, threshold, state, 1200L)
        // streak was reset — two more highs still are not enough
        assertTrue(ThresholdEvaluator.evaluate("app", high, threshold, state, 1300L).isEmpty())
        assertTrue(ThresholdEvaluator.evaluate("app", high, threshold, state, 1400L).isEmpty())
        assertEquals(1, ThresholdEvaluator.evaluate("app", high, threshold, state, 1500L).size)
    }

    @Test
    fun `memory alert fires immediately`() {
        val state = AlertState()
        val alerts = ThresholdEvaluator.evaluate("app", stats(memMib = 600.0), threshold, state, 1000L)
        assertEquals(1, alerts.size)
        assertEquals("mem:app", alerts[0].key)
    }

    @Test
    fun `cooldown suppresses repeat alerts then expires`() {
        val state = AlertState()
        val hot = stats(memMib = 600.0)

        assertEquals(1, ThresholdEvaluator.evaluate("app", hot, threshold, state, 1000L).size)
        // within cooldown (1800s) — suppressed
        assertTrue(ThresholdEvaluator.evaluate("app", hot, threshold, state, 1000L + 900L).isEmpty())
        // after cooldown — fires again
        assertEquals(1, ThresholdEvaluator.evaluate("app", hot, threshold, state, 1000L + 1801L).size)
    }

    @Test
    fun `both metrics can alert in one poll`() {
        val state = AlertState()
        // pre-seed cpu streak at 2
        ThresholdEvaluator.evaluate("app", stats(cpuPct = 95.0), threshold, state, 100L)
        ThresholdEvaluator.evaluate("app", stats(cpuPct = 95.0), threshold, state, 200L)

        val alerts = ThresholdEvaluator.evaluate("app",
            stats(cpuPct = 95.0, memMib = 600.0), threshold, state, 300L)
        assertEquals(2, alerts.size)
        assertEquals(setOf("cpu:app", "mem:app"), alerts.map { it.key }.toSet())
    }

    @Test
    fun `null metrics never alert`() {
        val state = AlertState()
        val noThresholds = ContainerThreshold("app")
        val alerts = ThresholdEvaluator.evaluate("app",
            stats(cpuPct = 100.0, memMib = 10000.0), noThresholds, state, 1000L)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `health alert fires on transition only`() {
        val state = AlertState()
        assertNotNull(ThresholdEvaluator.evaluateHealthTransition("app", true, "unhealthy", state, 1000L))
        // still unhealthy — no repeat
        assertNull(ThresholdEvaluator.evaluateHealthTransition("app", true, "unhealthy", state, 5000L))
        // recovers, then fails again after cooldown — fires
        assertNull(ThresholdEvaluator.evaluateHealthTransition("app", false, "healthy", state, 6000L))
        assertNotNull(ThresholdEvaluator.evaluateHealthTransition("app", true, "unhealthy", state, 6000L + 1801L))
    }
}
