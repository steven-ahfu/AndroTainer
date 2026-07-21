package com.dokeraj.androtainer.models

/** Live stats for one container — always fetched fresh, never passed via safe-args. */
data class KontainerStats(
    val cpuPct: Double,
    val memUsedBytes: Long,
    val memLimitBytes: Long,
) {
    val memPct: Double
        get() = if (memLimitBytes > 0) memUsedBytes.toDouble() / memLimitBytes * 100.0 else 0.0

    val memUsedMib: Double
        get() = memUsedBytes / MIB

    val memLimitMib: Double
        get() = memLimitBytes / MIB

    companion object {
        private const val MIB = 1048576.0
    }
}

/** Health/OOM extras from the inspect endpoint, shown on the details page. */
data class KontainerInspectInfo(
    val healthStatus: String?,
    val failingStreak: Int?,
    val oomKilled: Boolean,
    val exitCode: Int?,
    val restartCount: Int,
    val startedAt: String?,
)
