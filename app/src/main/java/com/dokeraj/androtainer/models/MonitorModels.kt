package com.dokeraj.androtainer.models

/** User-set alert thresholds, keyed by container NAME (ids change on recreate).
 * A null metric means "no threshold for this metric". */
data class ContainerThreshold(
    val containerName: String,
    val cpuPct: Double? = null,
    val memMib: Double? = null,
)

/** Worker-side alerting state, persisted between polls (watchdog.py port):
 * per-key cooldown timestamps, CPU sustain streaks and health transitions. */
data class AlertState(
    val lastAlertEpochByKey: MutableMap<String, Long> = mutableMapOf(),
    val cpuStreakByName: MutableMap<String, Int> = mutableMapOf(),
    val lastUnhealthyByName: MutableMap<String, Boolean> = mutableMapOf(),
)
