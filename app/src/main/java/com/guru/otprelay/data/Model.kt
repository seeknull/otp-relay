package com.guru.otprelay.data

enum class ForwardStatus { PENDING, SENT, FAILED }

enum class DurationOption(val label: String, val millis: Long) {
    M5("5 min", 5 * 60_000L),
    M15("15 min", 15 * 60_000L),
    M30("30 min", 30 * 60_000L),
    H1("1 hour", 60 * 60_000L),
    H6("6 hours", 6 * 60 * 60_000L),
    D1("1 day", 24 * 60 * 60_000L);

    companion object {
        fun labelFor(millis: Long): String =
            entries.firstOrNull { it.millis == millis }?.label ?: "${millis / 60_000} min"

        fun nearest(millis: Long): DurationOption =
            entries.minBy { kotlin.math.abs(it.millis - millis) }
    }
}

data class Target(val number: String, val name: String? = null) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: number
}

data class Session(
    val id: Long,
    val target: Target,
    val startedAt: Long,
    val expiresAt: Long,
) {
    fun isActive(now: Long = System.currentTimeMillis()) = now < expiresAt
}

data class Preset(val target: Target, val durationMillis: Long)

data class LogEntry(
    val id: Long,
    val sessionId: Long,
    val arrivedAt: Long,
    val from: String,
    val body: String,
    val to: String,
    val latencyMs: Long,
    val status: ForwardStatus,
    val error: String?,
)
