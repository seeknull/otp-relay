package com.guru.otprelay.data

/** The one rule that decides whether a message is relayed. */
object OtpMatch {

    /**
     * The phrasings senders actually use for a one-time code. Each alternative must stand as its
     * own word: letters either side disqualify it, so "hotpot" and "otpppp" are not treated as
     * codes, while digits and punctuation next to it are fine, because "OTP123456" is a real
     * thing banks send. Words inside a phrase may be joined by spaces or hyphens in any mix
     * ("one time password", "One-Time-Password", "onetime passcode").
     *
     * Deliberately absent: bare "code", "password" and "pin" (far too common in ordinary
     * messages), "PIN code" (an Indian postal code), and "confirmation code" (usually a booking
     * reference). Also absent, by choice: "auth/authentication code", "login code",
     * "security code" and "single-use code" — the phrasings Google, Instagram and Microsoft use
     * for account codes, which unlock a whole account rather than one transaction. A miss
     * forwards nothing, so each phrase here has to earn its false positives.
     */
    private val PHRASES = listOf(
        // OTP, otp, OTPs, OTP123456, (OTP)
        """otps?""",
        // one time password / One-Time-Passcode / one time pass code / one-time PIN / one-time code
        """one[\s-]*time[\s-]*(?:pass[\s-]*(?:word|code)|code|pin)s?""",
        // verification code, 2FA code, sign-in code
        """(?:verification|sign[\s-]*in|2fa)[\s-]*(?:code|pin)s?""",
        // Your passcode is 4321
        """passcodes?""",
    )

    /** Compiled once: this runs on the path a message takes to the modem. */
    private val KEYWORD = Regex(
        PHRASES.joinToString("|", prefix = """(?<![A-Za-z])(?:""", postfix = """)(?![A-Za-z])"""),
        RegexOption.IGNORE_CASE,
    )

    /** A long SMS arrives as several parts and the keyword may sit in any of them. */
    fun join(parts: List<String?>): String = parts.joinToString("") { it.orEmpty() }

    fun matches(body: String): Boolean = KEYWORD.containsMatchIn(body)
}
