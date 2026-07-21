package com.dokeraj.androtainer

import com.dokeraj.androtainer.models.AlertState
import com.dokeraj.androtainer.models.ContainerThreshold
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertStateJsonTest {
    @Test
    fun `alert state round-trips through gson with mutable maps intact`() {
        val state = AlertState(
            lastAlertEpochByKey = mutableMapOf("cpu:app" to 1000L, "mem:db" to 2000L),
            cpuStreakByName = mutableMapOf("app" to 2),
            lastUnhealthyByName = mutableMapOf("db" to true))

        val back = Gson().fromJson(Gson().toJson(state), AlertState::class.java)
        assertEquals(state, back)

        // maps must stay mutable after deserialization (evaluator mutates them)
        back.cpuStreakByName["app"] = 3
        assertEquals(3, back.cpuStreakByName["app"])
    }

    @Test
    fun `thresholds round-trip including null metrics`() {
        val list = listOf(
            ContainerThreshold("app", cpuPct = 80.0, memMib = null),
            ContainerThreshold("db", cpuPct = null, memMib = 512.0))
        val back = Gson().fromJson(Gson().toJson(list), Array<ContainerThreshold>::class.java).toList()
        assertEquals(list, back)
    }
}
