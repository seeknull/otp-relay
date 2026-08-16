package com.guru.otprelay

import com.guru.otprelay.data.RequestLink
import com.guru.otprelay.data.Target
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class RequestLinkTest {

    @Test
    fun `the shared link is an https link so chat apps make it tappable`() {
        val link = RequestLink.build(Target("+911234567890", "Amma"), 15 * 60_000L)
        assertTrue(link.startsWith("https://"), link)
    }

    @Test
    fun `the payload sits in the fragment so it never reaches the host`() {
        val link = RequestLink.build(Target("+911234567890", "Amma"), 15 * 60_000L)

        val beforeFragment = link.substringBefore('#')
        assertFalse(beforeFragment.contains("1234567890"), "number leaked into the request: $link")
        assertFalse(beforeFragment.contains("Amma"), "name leaked into the request: $link")
        assertFalse(beforeFragment.contains("?"), "no query string should be sent: $link")
        assertTrue(link.substringAfter('#').contains("to="), link)
    }

    @Test
    fun `the scheme form still parses for older shared links`() {
        val link = RequestLink.buildScheme(Target("+911234567890", "Amma"), 30 * 60_000L)
        val parsed = RequestLink.parse(link)!!

        assertTrue(link.startsWith("otprelay://"), link)
        assertEquals("+911234567890", parsed.target.number)
        assertEquals(30 * 60_000L, parsed.durationMillis)
    }

    @Test
    fun `round trips number and duration`() {
        val link = RequestLink.build(Target("+911234567890", "Amma"), 30 * 60_000L)
        val parsed = RequestLink.parse(link)!!

        assertEquals("+911234567890", parsed.target.number)
        assertEquals(30 * 60_000L, parsed.durationMillis)
    }

    /**
     * A name in the link would be text the sender controls, shown beside a number: exactly how a
     * stranger's number would be dressed up as a friend. The name always comes from the phone book.
     */
    @Test
    fun `never carries a name, even when the target has one`() {
        val link = RequestLink.build(Target("+911234567890", "Amma"), 30 * 60_000L)

        assertFalse(link.contains("name="), link)
        assertFalse(link.contains("Amma"), link)
        assertNull(RequestLink.parse(link)!!.target.name)
    }

    @Test
    fun `ignores a name someone adds to a link by hand`() {
        val parsed = RequestLink.parse("otprelay://request?to=%2B911234567890&name=Trustworthy&mins=5")!!
        assertNull(parsed.target.name)
    }

    @Test
    fun `finds a link embedded in a pasted chat message`() {
        val link = RequestLink.build(Target("+911234567890", "Amma"), 15 * 60_000L)
        val parsed = RequestLink.parse("hey send me an otp please $link thanks")!!
        assertEquals("+911234567890", parsed.target.number)
        assertEquals(15 * 60_000L, parsed.durationMillis)
    }

    @Test
    fun `stops at a newline so trailing text is not swallowed`() {
        val link = RequestLink.build(Target("+911234567890", null), 5 * 60_000L)
        assertEquals("+911234567890", RequestLink.parse("$link\nsecond line")!!.target.number)
    }

    @Test
    fun `strips formatting characters from the number`() {
        val parsed = RequestLink.parse("otprelay://request?to=%2B91-123-456-7890&mins=5")!!
        assertEquals("+911234567890", parsed.target.number)
    }

    @Test
    fun `rejects anything that is not a request link`() {
        assertNull(RequestLink.parse(null))
        assertNull(RequestLink.parse(""))
        assertNull(RequestLink.parse("https://example.com/r/#to=%2B911234567890"))
        assertNull(RequestLink.parse("otprelay://other?to=+911234567890"))
    }

    @Test
    fun `rejects a number with too few digits`() {
        assertNull(RequestLink.parse("otprelay://request?to=12345&mins=5"))
    }

    @Test
    fun `defaults to fifteen minutes when duration is missing or unreadable`() {
        assertEquals(
            15 * 60_000L,
            RequestLink.parse("otprelay://request?to=%2B911234567890")!!.durationMillis,
        )
        assertEquals(
            15 * 60_000L,
            RequestLink.parse("otprelay://request?to=%2B911234567890&mins=abc")!!.durationMillis,
        )
    }

    @Test
    fun `clamps an out of range duration to the longest session offered`() {
        val parsed = RequestLink.parse("otprelay://request?to=%2B911234567890&mins=99999")!!
        assertEquals(60 * 60_000L, parsed.durationMillis)
    }

    @Test
    fun `clamps a zero or negative duration up to one minute`() {
        assertEquals(
            60_000L,
            RequestLink.parse("otprelay://request?to=%2B911234567890&mins=0")!!.durationMillis,
        )
        assertEquals(
            60_000L,
            RequestLink.parse("otprelay://request?to=%2B911234567890&mins=-5")!!.durationMillis,
        )
    }
}
