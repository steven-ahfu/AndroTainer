package com.dokeraj.androtainer.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dokeraj.androtainer.R
import com.dokeraj.androtainer.globalvars.MonitorStore
import com.dokeraj.androtainer.interfaces.KontainerRetrofit
import com.dokeraj.androtainer.models.ContainerStateType
import com.dokeraj.androtainer.models.HealthState
import com.dokeraj.androtainer.models.Kontainer
import com.dokeraj.androtainer.models.retrofit.NetworkMapper
import com.dokeraj.androtainer.models.retrofit.StatsNetworkMapper
import com.dokeraj.androtainer.util.NotificationHelper
import com.dokeraj.androtainer.util.ThresholdEvaluator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/** Periodic background poll: fetches stats for containers the user has set
 * thresholds on and posts local notifications when a threshold is exceeded.
 * Scheduled via MainActiviy.syncMonitorWork(). */
@HiltWorker
class StatsMonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kontainerRetrofit: KontainerRetrofit,
    private val networkMapper: NetworkMapper,
    private val statsNetworkMapper: StatsNetworkMapper,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val thresholds = MonitorStore.readThresholds(applicationContext)
        if (thresholds.isEmpty()) return Result.success()

        val credential = MonitorStore.readCurrentCredential(applicationContext)
            ?: return Result.success()

        val state = MonitorStore.readAlertState(applicationContext)
        val now = Instant.now().epochSecond

        /** JWT users whose session expired get one cooldown-gated nudge and a
         * graceful skip — background re-login with a password is deliberately
         * out of scope. API-key credentials never expire (sentinel semantics). */
        if (!MonitorStore.isCredentialUsable(credential)) {
            if (!credential.isUsingApiKey) {
                staleAuthAlert(state, now)?.let { NotificationHelper.notify(applicationContext, it) }
                MonitorStore.writeAlertState(applicationContext, state)
            }
            return Result.success()
        }

        return try {
            val baseUrl = credential.serverUrl.removeSuffix("/")
            val endpointId = credential.currentEndpoint.id.toString()
            val listUrl = applicationContext.getString(R.string.getDockerContainers)
                .replace("{baseUrl}", baseUrl)
                .replace("{endpointId}", endpointId)

            val response = if (!credential.isUsingApiKey)
                kontainerRetrofit.listDockerContainers(credential.jwt, listUrl, 1)
            else
                kontainerRetrofit.listDockerContainersApiKey(credential.jwt, listUrl, 1)

            // networkMapper normalization keeps names aligned with what the UI shows
            val containers: List<Kontainer> = networkMapper.mapFromEntityList(response)
            val thresholdByName = thresholds.associateBy { it.containerName }

            for (container in containers) {
                // watchdog parity: transition-based unhealthy alert (all containers)
                ThresholdEvaluator.evaluateHealthTransition(
                    container.name,
                    container.health == HealthState.UNHEALTHY,
                    container.status,
                    state,
                    now)?.let { NotificationHelper.notify(applicationContext, it) }

                val threshold = thresholdByName[container.name] ?: continue
                if (container.state != ContainerStateType.RUNNING) continue

                val statsUrl = applicationContext.getString(R.string.getContainerStats)
                    .replace("{baseUrl}", baseUrl)
                    .replace("{endpointId}", endpointId)
                    .replace("{containerId}", container.id)

                val stats = try {
                    val dto = if (!credential.isUsingApiKey)
                        kontainerRetrofit.getContainerStats(credential.jwt, statsUrl, false)
                    else
                        kontainerRetrofit.getContainerStatsApiKey(credential.jwt, statsUrl, false)
                    statsNetworkMapper.mapStats(dto)
                } catch (e: Exception) {
                    continue // one container failing must not kill the poll
                }

                ThresholdEvaluator.evaluate(container.name, stats, threshold, state, now)
                    .forEach { NotificationHelper.notify(applicationContext, it) }
            }

            MonitorStore.writeAlertState(applicationContext, state)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun staleAuthAlert(
        state: com.dokeraj.androtainer.models.AlertState,
        now: Long,
    ): ThresholdEvaluator.Alert? {
        val key = "auth:stale"
        val last = state.lastAlertEpochByKey[key] ?: 0L
        if (now - last < ThresholdEvaluator.DEFAULT_COOLDOWN_SEC) return null
        state.lastAlertEpochByKey[key] = now
        return ThresholdEvaluator.Alert(key,
            "Container monitoring paused",
            "Your Portainer session expired — open AndroTainer and log in to resume threshold alerts.")
    }

    companion object {
        const val UNIQUE_WORK_NAME = "stats-monitor"
    }
}
