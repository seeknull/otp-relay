package com.guru.otprelay

import com.guru.otprelay.data.ForwardStatus
import com.guru.otprelay.data.LogEntry
import com.guru.otprelay.data.Store
import com.guru.otprelay.data.Target
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test

class StoreTest {

    private lateinit var disk: FakeKeyValueStore

    private val amma = Target("+911234567890", "Amma")
    private val anna = Target("+919876543210", null)

    @Before
    fun setUp() {
        disk = FakeKeyValueStore()
        Store.load(disk)
    }

    private fun log(id: Long, sessionId: Long, status: ForwardStatus, error: String? = null) =
        LogEntry(
            id = id,
            sessionId = sessionId,
            arrivedAt = 1_000L,
            from = "VM-HDFCBK",
            body = "Your OTP is 123456",
            to = amma.number,
            latencyMs = 12,
            status = status,
            error = error,
        )

    @Test
    fun `a started session is active and lands in history`() {
        val session = Store.startSession(amma, 15 * 60_000L)

        assertEquals(session, Store.currentSession())
        assertEquals(listOf(session), Store.sessions.value)
        assertEquals(amma, session.target)
    }

    @Test
    fun `a session that has run out is not current`() {
        Store.startSession(amma, 60_000L, now = 0L)
        assertNull(Store.currentSession())
    }

    @Test
    fun `reloading from disk restores an unexpired session`() {
        Store.startSession(amma, 60 * 60_000L)
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertEquals(amma.number, Store.currentSession()?.target?.number)
        assertEquals("Amma", Store.currentSession()?.target?.name)
    }

    @Test
    fun `reloading from disk drops a session that expired while away`() {
        Store.startSession(amma, 60_000L, now = 0L)
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertNull(Store.currentSession())
    }

    @Test
    fun `ending a session clears it and records when it really stopped`() {
        val session = Store.startSession(amma, 60 * 60_000L)
        Store.endSession(now = session.startedAt + 1000L)

        assertNull(Store.currentSession())
        assertNull(Store.active.value)
        assertEquals(session.startedAt + 1000L, Store.sessions.value.single().expiresAt)
    }

    @Test
    fun `ending twice is harmless`() {
        Store.startSession(amma, 60 * 60_000L)
        Store.endSession()
        val before = disk.writeCount
        Store.endSession()

        assertEquals(before, disk.writeCount)
    }

    @Test
    fun `starting a second session closes the first so they never overlap`() {
        val first = Store.startSession(amma, 60 * 60_000L)
        val second = Store.startSession(anna, 60 * 60_000L, now = first.startedAt + 5_000L)

        assertEquals(second.id, Store.currentSession()?.id)
        val closed = Store.sessions.value.first { it.id == first.id }
        assertEquals(first.startedAt + 5_000L, closed.expiresAt)
        assertFalse(closed.isActive(first.startedAt + 6_000L))
    }

    @Test
    fun `a send result promotes a pending entry to sent`() {
        Store.addLog(log(1, 1, ForwardStatus.PENDING))
        Store.updateLog(1, ForwardStatus.SENT, null)

        assertEquals(ForwardStatus.SENT, Store.logs.value.single().status)
        assertNull(Store.logs.value.single().error)
    }

    @Test
    fun `a later success never overwrites a failed part`() {
        Store.addLog(log(1, 1, ForwardStatus.PENDING))
        Store.updateLog(1, ForwardStatus.FAILED, "No service")
        Store.updateLog(1, ForwardStatus.SENT, null)

        assertEquals(ForwardStatus.FAILED, Store.logs.value.single().status)
        assertEquals("No service", Store.logs.value.single().error)
    }

    @Test
    fun `a null error survives the disk round trip instead of becoming the text null`() {
        Store.addLog(log(1, 1, ForwardStatus.SENT, error = null))
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertNull(Store.logs.value.single().error)
    }

    /**
     * Guards the bug that actually shipped. A round-trip assertion cannot catch it: the JVM's
     * org.json returns "" from optString() for a JSON null while Android's returns the text
     * "null", so only Android showed it. Asserting on the stored text is the same on both.
     */
    @Test
    fun `an absent error is stored as a real json null not as the text null`() {
        Store.addLog(log(1, 1, ForwardStatus.SENT, error = null))

        val stored = disk.read("logs")!!
        assertTrue(stored.contains("\"error\":null"), "expected a bare json null in: $stored")
        assertFalse(stored.contains("\"error\":\"null\""), "error was quoted in: $stored")
    }

    @Test
    fun `an absent contact name is stored as a real json null not as the text null`() {
        Store.startSession(anna, 60 * 60_000L)

        val stored = disk.read("sessions")!!
        assertTrue(stored.contains("\"name\":null"), "expected a bare json null in: $stored")
        assertFalse(stored.contains("\"name\":\"null\""), "name was quoted in: $stored")
    }

    @Test
    fun `an error message survives the disk round trip`() {
        Store.addLog(log(1, 1, ForwardStatus.FAILED, error = "Radio off"))
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertEquals("Radio off", Store.logs.value.single().error)
    }

    @Test
    fun `a missing contact name survives as null rather than the text null`() {
        Store.startSession(anna, 60 * 60_000L)
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertNull(Store.currentSession()?.target?.name)
        assertEquals(anna.number, Store.currentSession()?.target?.display)
    }

    @Test
    fun `newest entries come first`() {
        Store.addLog(log(1, 1, ForwardStatus.SENT))
        Store.addLog(log(2, 1, ForwardStatus.SENT))

        assertEquals(listOf(2L, 1L), Store.logs.value.map { it.id })
    }

    @Test
    fun `ids keep climbing after a reload so they never collide`() {
        Store.addLog(log(7, 1, ForwardStatus.SENT))
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertTrue(Store.newId() > 7L)
    }

    @Test
    fun `saving a shortcut twice keeps one copy at the front`() {
        Store.saveShortcut(amma, 15 * 60_000L)
        Store.saveShortcut(anna, 5 * 60_000L)
        Store.saveShortcut(amma, 15 * 60_000L)

        assertEquals(2, Store.shortcuts.value.size)
        assertEquals(amma.number, Store.shortcuts.value.first().target.number)
    }

    @Test
    fun `the same person with a different duration is a separate shortcut`() {
        Store.saveShortcut(amma, 15 * 60_000L)
        Store.saveShortcut(amma, 60 * 60_000L)

        assertEquals(2, Store.shortcuts.value.size)
    }

    @Test
    fun `a removed shortcut stays gone after reload`() {
        Store.saveShortcut(amma, 15 * 60_000L)
        Store.deleteShortcut(Store.shortcuts.value.single())
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertTrue(Store.shortcuts.value.isEmpty())
    }

    @Test
    fun `typing a bare number keeps the name it was saved with`() {
        Store.saveContact(amma)
        // Same digits, no name: what happens when the number is typed rather than picked.
        val session = Store.startSession(Target(amma.number, null), 60_000L, now = 0L)

        assertEquals("Amma", session.target.name)
    }

    @Test
    fun `a name is still remembered after a reload`() {
        Store.saveContact(amma)
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertEquals("Amma", Store.nameFor(amma.number))
        assertNull(Store.nameFor("+910000000000"))
    }

    @Test
    fun `only a contact chosen from the phone book may receive OTPs`() {
        assertFalse(Store.isKnownNumber(amma.number))

        Store.saveContact(amma)
        assertTrue(Store.isKnownNumber(amma.number))
    }

    @Test
    fun `starting a session never quietly authorises a number`() {
        Store.startSession(Target("+915555555555", null), 60_000L, now = 0L)

        assertFalse(Store.isKnownNumber("+915555555555"))
        assertTrue(Store.numbers.value.isEmpty())
    }

    @Test
    fun `a saved contact is recognised however the number is written`() {
        Store.saveContact(Target("+91 98765 43210", "Anna"))

        assertTrue(Store.isKnownNumber("9876543210"))
        assertTrue(Store.isKnownNumber("+919876543210"))
        assertTrue(Store.isKnownNumber("098765-43210"))
        assertFalse(Store.isKnownNumber("+919876543211"))
    }

    @Test
    fun `a removed contact is no longer allowed`() {
        Store.saveContact(amma)
        Store.deleteNumber(amma)

        assertFalse(Store.isKnownNumber(amma.number))
    }

    @Test
    fun `a shortcut can supply the name for a typed number`() {
        Store.saveShortcut(amma, 60_000L)

        assertEquals("Amma", Store.nameFor(amma.number))
    }

    @Test
    fun `saved contacts are deduplicated and newest first`() {
        Store.saveContact(amma)
        Store.saveContact(anna)
        Store.saveContact(amma)

        assertEquals(listOf(amma.number, anna.number), Store.numbers.value.map { it.number })
    }

    @Test
    fun `clearing history wipes logs but keeps a running session`() {
        val session = Store.startSession(amma, 60 * 60_000L)
        Store.addLog(log(1, session.id, ForwardStatus.SENT))
        Store.clearHistory()

        assertTrue(Store.logs.value.isEmpty())
        assertEquals(session.id, Store.currentSession()?.id)
        assertEquals(listOf(session.id), Store.sessions.value.map { it.id })
    }

    @Test
    fun `my number is remembered across a reload`() {
        Store.setMyNumber("+911111111111")
        Store.load(FakeKeyValueStore(disk.values.toMap()))

        assertEquals("+911111111111", Store.myNumber.value)
    }

    @Test
    fun `corrupt stored data is ignored rather than crashing`() {
        Store.load(FakeKeyValueStore(mapOf("logs" to "{not json", "sessions" to "]]")))

        assertTrue(Store.logs.value.isEmpty())
        assertTrue(Store.sessions.value.isEmpty())
    }
}
