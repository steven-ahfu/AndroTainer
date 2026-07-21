package com.dokeraj.androtainer.models.retrofit

import com.google.gson.annotations.SerializedName

/** Subset of the Docker stats payload (GET .../containers/{id}/stats?stream=false).
 * With stream=false (and no one-shot) the daemon takes two samples, so precpu_stats
 * is populated and CPU% is computable from a single call. */
data class PContainerStatsResponse(
    @SerializedName("cpu_stats") val cpuStats: PCpuStats?,
    @SerializedName("precpu_stats") val precpuStats: PCpuStats?,
    @SerializedName("memory_stats") val memoryStats: PMemoryStats?,
)

data class PCpuStats(
    @SerializedName("cpu_usage") val cpuUsage: PCpuUsage?,
    @SerializedName("system_cpu_usage") val systemCpuUsage: Long?,
    @SerializedName("online_cpus") val onlineCpus: Int?,
)

data class PCpuUsage(
    @SerializedName("total_usage") val totalUsage: Long?,
    @SerializedName("percpu_usage") val percpuUsage: List<Long>?,
)

data class PMemoryStats(
    @SerializedName("usage") val usage: Long?,
    @SerializedName("limit") val limit: Long?,
    @SerializedName("stats") val stats: PMemoryDetail?,
)

data class PMemoryDetail(
    @SerializedName("inactive_file") val inactiveFile: Long?,
)
