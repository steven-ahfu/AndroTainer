package com.dokeraj.androtainer.util

import java.util.Locale

/** Parses Docker's human status strings ("Up 3 hours (healthy)") into approximate
 * uptime seconds, purely for ordering — no extra network calls. */
object UptimeParser {
    private val UNIT_SECONDS: Map<String, Long> = mapOf(
        "second" to 1L,
        "minute" to 60L,
        "hour" to 3600L,
        "day" to 86400L,
        "week" to 604800L,
        "month" to 2592000L,
        "year" to 31536000L
    )

    private val UP_REGEX = Regex("""^Up (\d+) (second|minute|hour|day|week|month|year)s?""")

    /** null when the container is not up (Exited/Created/...) or the phrasing is unknown */
    fun uptimeSeconds(status: String): Long? {
        val cleaned = status.trim()
            .replace(Regex("""\s*\((healthy|unhealthy|health: starting)\)"""), "")
            .trim()

        if (!cleaned.startsWith("Up", ignoreCase = false)) return null

        val lower = cleaned.lowercase(Locale.US)
        return when {
            lower.startsWith("up less than a second") -> 0L
            lower.startsWith("up about a minute") -> 60L
            lower.startsWith("up about an hour") -> 3600L
            else -> UP_REGEX.find(cleaned)?.let { m ->
                val amount = m.groupValues[1].toLongOrNull() ?: return@let null
                val unit = UNIT_SECONDS[m.groupValues[2].lowercase(Locale.US)] ?: return@let null
                amount * unit
            } ?: 0L // "Up" with unknown phrasing: treat as just started
        }
    }
}
