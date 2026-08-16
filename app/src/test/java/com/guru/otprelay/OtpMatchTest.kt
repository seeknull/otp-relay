package com.guru.otprelay

import com.guru.otprelay.data.DurationOption
import com.guru.otprelay.data.OtpMatch
import com.guru.otprelay.data.Target
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class OtpMatchTest {

    @Test
    fun `matches regardless of case`() {
        assertTrue(OtpMatch.matches("Your OTP is 123456"))
        assertTrue(OtpMatch.matches("your otp is 123456"))
        assertTrue(OtpMatch.matches("Otp"))
    }

    @Test
    fun `matches when the keyword is inside a longer word`() {
        // Deliberate: senders write "OTP:", "OTP-", "yourOTP". A word boundary would miss those.
        assertTrue(OtpMatch.matches("yourOTP:123456"))
    }

    @Test
    fun `ignores messages without the keyword`() {
        assertFalse(OtpMatch.matches("Your verification code is 123456"))
        assertFalse(OtpMatch.matches(""))
    }

    @Test
    fun `joins the parts of a long message before matching`() {
        val body = OtpMatch.join(listOf("Dear customer, your one time pass", "word OTP is 123456"))

        assertEquals("Dear customer, your one time password OTP is 123456", body)
        assertTrue(OtpMatch.matches(body))
    }

    @Test
    fun `finds the keyword when it lands only in a later part`() {
        assertTrue(OtpMatch.matches(OtpMatch.join(listOf("first part text ", "second has OTP 999"))))
    }

    @Test
    fun `treats a missing part as empty rather than crashing`() {
        assertEquals("ab", OtpMatch.join(listOf("a", null, "b")))
    }
}

class DurationOptionTest {

    @Test
    fun `every option is labelled`() {
        DurationOption.entries.forEach {
            assertEquals(it.label, DurationOption.labelFor(it.millis))
        }
    }

    @Test
    fun `an unknown duration is described in minutes`() {
        assertEquals("7 min", DurationOption.labelFor(7 * 60_000L))
    }

    @Test
    fun `an odd duration from a link snaps to the closest option`() {
        assertEquals(DurationOption.M5, DurationOption.nearest(4 * 60_000L))
        assertEquals(DurationOption.M30, DurationOption.nearest(25 * 60_000L))
        assertEquals(DurationOption.D1, DurationOption.nearest(30L * 60 * 60_000L))
    }

    @Test
    fun `the six offered durations are the ones asked for`() {
        assertEquals(
            listOf("5 min", "15 min", "30 min", "1 hour", "6 hours", "1 day"),
            DurationOption.entries.map { it.label },
        )
    }
}

class TargetTest {

    @Test
    fun `a name is shown when there is one`() {
        assertEquals("Amma", Target("+911234567890", "Amma").display)
    }

    @Test
    fun `the number is shown when there is no usable name`() {
        assertEquals("+911234567890", Target("+911234567890", null).display)
        assertEquals("+911234567890", Target("+911234567890", "  ").display)
    }
}
