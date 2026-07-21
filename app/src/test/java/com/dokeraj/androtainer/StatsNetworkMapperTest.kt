package com.dokeraj.androtainer

import com.dokeraj.androtainer.models.retrofit.PContainerInspectResponse
import com.dokeraj.androtainer.models.retrofit.StatsNetworkMapper
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsNetworkMapperTest {
    private val mapper = StatsNetworkMapper()

    @Test
    fun `maps full inspect payload`() {
        val json = """
        {
          "RestartCount": 4,
          "State": {
            "Status": "exited",
            "OOMKilled": true,
            "ExitCode": 137,
            "StartedAt": "2026-07-15T10:00:00Z",
            "Health": {"Status": "unhealthy", "FailingStreak": 3}
          }
        }
        """.trimIndent()
        val info = mapper.mapInspect(Gson().fromJson(json, PContainerInspectResponse::class.java))

        assertEquals("unhealthy", info.healthStatus)
        assertEquals(3, info.failingStreak)
        assertTrue(info.oomKilled)
        assertEquals(137, info.exitCode)
        assertEquals(4, info.restartCount)
        assertEquals("2026-07-15T10:00:00Z", info.startedAt)
    }

    @Test
    fun `tolerates absent health block and defaults`() {
        val json = """{"State": {"Status": "running", "ExitCode": 0}}"""
        val info = mapper.mapInspect(Gson().fromJson(json, PContainerInspectResponse::class.java))

        assertNull(info.healthStatus)
        assertNull(info.failingStreak)
        assertFalse(info.oomKilled)
        assertEquals(0, info.restartCount)
    }
}
