package com.guru.otprelay.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayClock = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

fun formatClock(millis: Long): String = clock.format(Date(millis))

fun formatDayClock(millis: Long): String = dayClock.format(Date(millis))

fun formatLatency(millis: Long): String =
    if (millis < 1000) "${millis} ms" else String.format(Locale.getDefault(), "%.1f s", millis / 1000.0)

fun formatRemaining(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m left"
        minutes > 0 -> "${minutes}m ${seconds}s left"
        else -> "${seconds}s left"
    }
}
