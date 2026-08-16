package com.guru.otprelay.ui

import androidx.compose.ui.graphics.Color
import com.guru.otprelay.data.DurationOption
import com.guru.otprelay.data.normalizeNumber

/**
 * A distinct emoji per contact and a colour per duration, so a quick action can be recognised at a
 * glance without reading it. Both are derived, never stored, so they survive a reinstall.
 */
private val EMOJI = listOf(
    "🦊", "🐼", "🦉", "🐝", "🦜", "🐳", "🦋", "🐙",
    "🌵", "🍀", "🌻", "🍁", "⭐", "🎈", "🎯", "🧩",
)

/** The mark a number gets on its own. Two contacts can land on the same one. */
private fun rawEmoji(number: String): String {
    val key = normalizeNumber(number).ifEmpty { number }
    return EMOJI[((key.hashCode().toLong() and 0xFFFFFFFFL) % EMOJI.size).toInt()]
}

/**
 * Assigns every saved contact a different emoji. A plain hash collides surprisingly often — with
 * a handful of contacts it is roughly a coin flip — and two people sharing a mark defeats the
 * point of having one, so collisions are nudged to the next free emoji.
 *
 * Sorted first so the result depends on who is saved, not on the order they were added.
 */
fun emojiAssignments(numbers: List<String>): Map<String, String> {
    val used = mutableSetOf<String>()
    return numbers
        .map { normalizeNumber(it).ifEmpty { it } }
        .distinct()
        .sorted()
        .associateWith { key ->
            var index = EMOJI.indexOf(rawEmoji(key))
            var attempts = 0
            while (EMOJI[index] in used && attempts < EMOJI.size) {
                index = (index + 1) % EMOJI.size
                attempts++
            }
            EMOJI[index].also { used += it }
        }
}

/** Falls back to the plain mark for numbers no longer saved, such as old history rows. */
fun emojiFor(number: String, assignments: Map<String, String> = emptyMap()): String {
    val key = normalizeNumber(number).ifEmpty { number }
    return assignments[key] ?: rawEmoji(key)
}

// Four hues spread right around the wheel: green, blue, orange, magenta. Neighbouring hues such
// as blue and teal read as the same colour at chip size, which is what these replace.
fun colorFor(option: DurationOption): Color = when (option) {
    DurationOption.M5 -> Color(0xFF21A179)
    DurationOption.M15 -> Color(0xFF2D7DD2)
    DurationOption.M30 -> Color(0xFFF5A623)
    DurationOption.H1 -> Color(0xFFC2185B)
}

fun colorForMillis(millis: Long): Color =
    DurationOption.entries.firstOrNull { it.millis == millis }
        ?.let(::colorFor)
        ?: Color(0xFF7A8699)
