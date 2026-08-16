package com.guru.otprelay.data

/** The one rule that decides whether a message is relayed. */
object OtpMatch {

    /**
     * "OTP" as a word, in any case. Letters either side disqualify it, so "hotpot" and "otpppp"
     * are not treated as codes, while the shapes senders actually use — "OTP:", "OTP-123456",
     * "(OTP)", "your otp is" — still match. Digits and punctuation next to it are fine, because
     * "OTP123456" is a real thing banks send.
     *
     * Compiled once: this runs on the path a message takes to the modem.
     */
    private val KEYWORD = Regex("(?<![A-Za-z])otp(?![A-Za-z])", RegexOption.IGNORE_CASE)

    /** A long SMS arrives as several parts and the keyword may sit in any of them. */
    fun join(parts: List<String?>): String = parts.joinToString("") { it.orEmpty() }

    fun matches(body: String): Boolean = KEYWORD.containsMatchIn(body)
}
