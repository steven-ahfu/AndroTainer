package com.dokeraj.androtainer.models.retrofit

import com.dokeraj.androtainer.models.KontainerInspectInfo
import com.dokeraj.androtainer.models.KontainerStats
import com.dokeraj.androtainer.util.StatsCalculator
import javax.inject.Inject

class StatsNetworkMapper @Inject constructor() {
    fun mapStats(entity: PContainerStatsResponse): KontainerStats =
        StatsCalculator.calculate(entity)

    fun mapInspect(entity: PContainerInspectResponse): KontainerInspectInfo =
        KontainerInspectInfo(
            healthStatus = entity.state?.health?.status,
            failingStreak = entity.state?.health?.failingStreak,
            oomKilled = entity.state?.oomKilled ?: false,
            exitCode = entity.state?.exitCode,
            restartCount = entity.restartCount ?: 0,
            startedAt = entity.state?.startedAt)
}
