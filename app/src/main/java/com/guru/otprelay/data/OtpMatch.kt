package com.guru.otprelay.data

/** The one rule that decides whether a message is relayed. */
object OtpMatch {

    private const val KEYWORD = "otp"

    /** A long SMS arrives as several parts and the keyword may sit in any of them. */
    fun join(parts: List<String?>): String = parts.joinToString("") { it.orEmpty() }

    fun matches(body: String): Boolean = body.contains(KEYWORD, ignoreCase = true)
}
