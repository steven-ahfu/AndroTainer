package com.dokeraj.androtainer

import com.dokeraj.androtainer.models.retrofit.PContainerStatsResponse
import com.dokeraj.androtainer.util.StatsCalculator
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCalculatorTest {

    private fun statsJson(
        cpuTotal: Long,
        precpuTotal: Long,
        systemCpu: Long?,
        precpuSystem: Long?,
        onlineCpus: Int?,
        percpu: List<Long>? = null,
        memUsage: Long? = null,
        memLimit: Long? = null,
        inactiveFile: Long? = null,
    ): PContainerStatsResponse {
        val json = """
        {
          "cpu_stats": {
            "cpu_usage": {"total_usage": $cpuTotal ${percpu?.let { ""","percpu_usage": $it""" } ?: ""}},
            ${systemCpu?.let { """"system_cpu_usage": $it,""" } ?: ""}
            ${onlineCpus?.let { """"online_cpus": $it""" } ?: """"online_cpus": null"""}
          },
          "precpu_stats": {
            "cpu_usage": {"total_usage": $precpuTotal},
            ${precpuSystem?.let { """"system_cpu_usage": $it""" } ?: """"system_cpu_usage": null"""}
          },
          "memory_stats": {
            ${memUsage?.let { """"usage": $it,""" } ?: ""}
            ${memLimit?.let { """"limit": $it,""" } ?: ""}
            "stats": {${inactiveFile?.let { """"inactive_file": $it""" } ?: ""}}
          }
        }
        """.trimIndent()
        return Gson().fromJson(json, PContainerStatsResponse::class.java)
    }

    @Test
    fun `computes cpu pct and mem from live-verified reference sample`() {
        // Golden values from PORTAINER_API_REFERENCE: immich_server returned
        // cpu%=2.15, mem_used=479.9 MiB, limit=19852.1 MiB (usage - inactive_file)
        val memUsed = (479.9 * 1048576).toLong()
        val inactive = (100.0 * 1048576).toLong()
        val dto = statsJson(
            cpuTotal = 1_021_500_000L,
            precpuTotal = 1_000_000_000L,
            systemCpu = 5_000_000_000L,
            precpuSystem = 4_000_000_000L,
            onlineCpus = 1,
            memUsage = memUsed + inactive,
            memLimit = (19852.1 * 1048576).toLong(),
            inactiveFile = inactive)

        val stats = StatsCalculator.calculate(dto)
        assertEquals(2.15, stats.cpuPct, 0.001)
        assertEquals(479.9, stats.memUsedMib, 0.1)
        assertEquals(19852.1, stats.memLimitMib, 0.1)
        assertEquals(479.9 / 19852.1 * 100.0, stats.memPct, 0.01)
    }

    @Test
    fun `zero system delta yields zero cpu`() {
        val dto = statsJson(
            cpuTotal = 500L, precpuTotal = 100L,
            systemCpu = 1000L, precpuSystem = 1000L,
            onlineCpus = 4)
        assertEquals(0.0, StatsCalculator.calculate(dto).cpuPct, 0.0)
    }

    @Test
    fun `missing online_cpus falls back to percpu list size`() {
        val dto = statsJson(
            cpuTotal = 200L, precpuTotal = 100L,
            systemCpu = 2000L, precpuSystem = 1000L,
            onlineCpus = null,
            percpu = listOf(50L, 50L, 50L, 50L))
        // (100/1000) * 4 * 100 = 40%
        assertEquals(40.0, StatsCalculator.calculate(dto).cpuPct, 0.001)
    }

    @Test
    fun `missing percpu and online_cpus falls back to one core`() {
        val dto = statsJson(
            cpuTotal = 200L, precpuTotal = 100L,
            systemCpu = 2000L, precpuSystem = 1000L,
            onlineCpus = null)
        assertEquals(10.0, StatsCalculator.calculate(dto).cpuPct, 0.001)
    }

    @Test
    fun `missing inactive_file uses raw usage`() {
        val dto = statsJson(
            cpuTotal = 0L, precpuTotal = 0L, systemCpu = null, precpuSystem = null,
            onlineCpus = 1,
            memUsage = 1048576L, memLimit = 2097152L)
        val stats = StatsCalculator.calculate(dto)
        assertEquals(1.0, stats.memUsedMib, 0.001)
        assertEquals(50.0, stats.memPct, 0.001)
    }

    @Test
    fun `zero or absent limit yields zero mem pct`() {
        val dto = statsJson(
            cpuTotal = 0L, precpuTotal = 0L, systemCpu = null, precpuSystem = null,
            onlineCpus = 1, memUsage = 1048576L)
        assertEquals(0.0, StatsCalculator.calculate(dto).memPct, 0.0)
    }
}
