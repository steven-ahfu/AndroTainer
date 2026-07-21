package com.dokeraj.androtainer.util

import com.dokeraj.androtainer.models.KontainerStats
import com.dokeraj.androtainer.models.retrofit.PContainerStatsResponse

/** Docker stats math — mirrors `docker stats` / the Portainer UI:
 * cpu% = (cpu_delta / system_delta) * online_cpus * 100
 * mem_used = usage - inactive_file  (page cache excluded, cgroup v2) */
object StatsCalculator {
    fun calculate(dto: PContainerStatsResponse): KontainerStats {
        val cpuTotal = dto.cpuStats?.cpuUsage?.totalUsage ?: 0L
        val precpuTotal = dto.precpuStats?.cpuUsage?.totalUsage ?: 0L
        val cpuDelta = cpuTotal - precpuTotal

        val systemDelta = (dto.cpuStats?.systemCpuUsage ?: 0L) -
                (dto.precpuStats?.systemCpuUsage ?: 0L)

        val ncpu = dto.cpuStats?.onlineCpus
            ?: dto.cpuStats?.cpuUsage?.percpuUsage?.size?.takeIf { it > 0 }
            ?: 1

        val cpuPct = if (systemDelta > 0L)
            cpuDelta.toDouble() / systemDelta * ncpu * 100.0
        else 0.0

        val usage = dto.memoryStats?.usage ?: 0L
        val inactiveFile = dto.memoryStats?.stats?.inactiveFile ?: 0L
        val memUsed = (usage - inactiveFile).coerceAtLeast(0L)
        val memLimit = dto.memoryStats?.limit ?: 0L

        return KontainerStats(
            cpuPct = cpuPct,
            memUsedBytes = memUsed,
            memLimitBytes = memLimit)
    }
}
