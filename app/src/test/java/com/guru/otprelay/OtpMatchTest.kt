package com.guru.otprelay

import com.guru.otprelay.data.DurationOption
import com.guru.otprelay.data.OtpMatch
import com.guru.otprelay.data.Target
import com.guru.otprelay.ui.colorFor
import com.guru.otprelay.ui.colorForMillis
import com.guru.otprelay.ui.emojiAssignments
import com.guru.otprelay.ui.emojiFor
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
        assertEquals(DurationOption.H1, DurationOption.nearest(30L * 60 * 60_000L))
    }

    @Test
    fun `the offered durations are the ones asked for`() {
        assertEquals(
            listOf("5 min", "15 min", "30 min", "1 hour"),
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

class MarksTest {

    @Test
    fun `the same person always gets the same emoji`() {
        assertEquals(emojiFor("+919876543210"), emojiFor("+919876543210"))
    }

    @Test
    fun `saved contacts never share an emoji`() {
        // A plain hash collides often enough to matter with only a handful of contacts.
        val numbers = List(12) { "+9199000000${it.toString().padStart(2, '0')}" }
        val marks = emojiAssignments(numbers)

        assertEquals(numbers.size, marks.values.toSet().size)
    }

    @Test
    fun `an assignment does not depend on the order contacts were added`() {
        val numbers = listOf("+919000000001", "+919000000002", "+919000000003")

        assertEquals(emojiAssignments(numbers), emojiAssignments(numbers.reversed()))
    }

    @Test
    fun `formatting differences do not change the emoji`() {
        // These are one person, so they must not look like two.
        val expected = emojiFor("+91 98765 43210")
        assertEquals(expected, emojiFor("9876543210"))
        assertEquals(expected, emojiFor("098765-43210"))
    }

    @Test
    fun `every duration has its own colour`() {
        val colours = DurationOption.entries.map { colorFor(it) }
        assertEquals(colours.size, colours.toSet().size)
    }

    @Test
    fun `an unknown duration still gets a colour rather than crashing`() {
        assertEquals(colorForMillis(7 * 60_000L), colorForMillis(9 * 60_000L))
    }
}
