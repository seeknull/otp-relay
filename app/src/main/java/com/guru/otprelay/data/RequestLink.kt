package com.guru.otprelay.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A shareable link that asks for a forwarding session. You generate one per person and send it to
 * them; they send it back whenever they need an OTP. Opening or pasting it only pre-fills the
 * approval prompt — nothing starts until you approve.
 *
 * Deliberately plain string handling rather than android.net.Uri so it can be unit tested.
 */
object RequestLink {

    const val SCHEME = "otprelay"
    const val HOST = "request"
    private const val PREFIX = "$SCHEME://$HOST"

    /**
     * The tappable form. Chat apps only linkify https, and Android only hands an https link to an
     * app when Digital Asset Links verification passes, which needs assetlinks.json served from
     * the host root. That file is static, so GitHub Pages hosts it and no server is involved.
     *
     * The payload lives in the fragment because fragments are never sent in the HTTP request:
     * if the app is not installed and the browser opens the page, the recipient's number still
     * never reaches GitHub's logs.
     */
    const val WEB_HOST = "seeknull.github.io"
    const val WEB_PATH = "/r/"
    private const val WEB_PREFIX = "https://$WEB_HOST$WEB_PATH"

    private const val MIN_MINUTES = 1L
    private const val MAX_MINUTES = 60L
    private const val DEFAULT_MINUTES = 15L

    /** The link to share: tappable in chat apps, opens straight into the approval prompt. */
    fun build(target: Target, durationMillis: Long): String =
        WEB_PREFIX + "#" + payload(target, durationMillis)

    /** The scheme form, kept as a fallback for when verification is not set up. */
    fun buildScheme(target: Target, durationMillis: Long): String =
        "$PREFIX?" + payload(target, durationMillis)

    // Only the number travels. A name in the link would be attacker-controlled text shown next
    // to a number, which is exactly how someone would dress up a stranger's number as a friend.
    private fun payload(target: Target, durationMillis: Long): String =
        "to=" + encode(target.number) + "&mins=" + (durationMillis / 60_000)

    /** Accepts either form, bare or embedded in text, so a pasted chat message works. */
    fun parse(text: String?): Preset? {
        if (text == null) return null

        val start = listOf(text.indexOf(WEB_PREFIX), text.indexOf(PREFIX))
            .filter { it >= 0 }
            .minOrNull() ?: return null
        val link = text.substring(start).substringBefore(' ').substringBefore('\n')

        // Query for the scheme form, fragment for the web form.
        val query = link.substringAfter('#', "")
            .takeIf { it.isNotEmpty() }
            ?: link.substringAfter('?', "").takeIf { it.isNotEmpty() }
            ?: return null
        val params = query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', "")
            if (key.isEmpty()) null else key to decode(pair.substringAfter('=', ""))
        }.toMap()

        val number = params["to"].orEmpty().filter { it.isDigit() || it == '+' }
        if (number.count(Char::isDigit) < MIN_DIGITS) return null

        val minutes = (params["mins"]?.toLongOrNull() ?: DEFAULT_MINUTES)
            .coerceIn(MIN_MINUTES, MAX_MINUTES)

        return Preset(Target(number), minutes * 60_000L)
    }

    const val MIN_DIGITS = 6

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: IllegalArgumentException) {
        value
    }
}
