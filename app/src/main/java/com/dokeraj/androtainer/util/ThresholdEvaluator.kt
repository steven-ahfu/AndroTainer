package com.dokeraj.androtainer.util

import com.dokeraj.androtainer.models.AlertState
import com.dokeraj.androtainer.models.ContainerThreshold
import com.dokeraj.androtainer.models.KontainerStats

/** Pure threshold evaluation, ported from the proven watchdog.py logic:
 * CPU alerts require a sustained streak (spiky metric), memory alerts fire
 * immediately, and every alert key is gated by a cooldown. The evaluator
 * mutates [AlertState]; the caller persists it. */
object ThresholdEvaluator {

    data class Alert(val key: String, val title: String, val message: String)

    const val DEFAULT_COOLDOWN_SEC = 1800L
    const val DEFAULT_CPU_SUSTAIN_POLLS = 3

    fun evaluate(
        name: String,
        stats: KontainerStats,
        threshold: ContainerThreshold,
        state: AlertState,
        nowEpochSec: Long,
        cooldownSec: Long = DEFAULT_COOLDOWN_SEC,
        cpuSustainPolls: Int = DEFAULT_CPU_SUSTAIN_POLLS,
    ): List<Alert> {
        val alerts = mutableListOf<Alert>()

        threshold.cpuPct?.let { cpuLimit ->
            val streak = if (stats.cpuPct > cpuLimit)
                (state.cpuStreakByName[name] ?: 0) + 1
            else 0
            state.cpuStreakByName[name] = streak

            if (streak >= cpuSustainPolls) {
                fire(state, "cpu:$name", nowEpochSec, cooldownSec) {
                    Alert(it, "$name: high CPU",
                        String.format(java.util.Locale.US,
                            "CPU %.0f%% is over your %.0f%% threshold (%d polls in a row)",
                            stats.cpuPct, cpuLimit, streak))
                }?.let(alerts::add)
            }
        }

        threshold.memMib?.let { memLimit ->
            if (stats.memUsedMib > memLimit) {
                fire(state, "mem:$name", nowEpochSec, cooldownSec) {
                    Alert(it, "$name: high memory",
                        String.format(java.util.Locale.US,
                            "Memory %.0f MiB is over your %.0f MiB threshold",
                            stats.memUsedMib, memLimit))
                }?.let(alerts::add)
            }
        }

        return alerts
    }

    /** transition-based unhealthy alert (fires only on healthy -> unhealthy) */
    fun evaluateHealthTransition(
        name: String,
        isUnhealthy: Boolean,
        statusText: String,
        state: AlertState,
        nowEpochSec: Long,
        cooldownSec: Long = DEFAULT_COOLDOWN_SEC,
    ): Alert? {
        val was = state.lastUnhealthyByName[name] ?: false
        state.lastUnhealthyByName[name] = isUnhealthy
        if (!isUnhealthy || was) return null
        return fire(state, "health:$name", nowEpochSec, cooldownSec) {
            Alert(it, "$name is unhealthy", "Healthcheck failing: $statusText")
        }
    }

    /** cooldown gate: builds + records the alert only if the key has cooled down */
    private fun fire(
        state: AlertState,
        key: String,
        nowEpochSec: Long,
        cooldownSec: Long,
        build: (String) -> Alert,
    ): Alert? {
        // a key that has never alerted always fires — do not treat "absent" as epoch 0
        val last = state.lastAlertEpochByKey[key]
        if (last != null && nowEpochSec - last < cooldownSec) return null
        state.lastAlertEpochByKey[key] = nowEpochSec
        return build(key)
    }
}
