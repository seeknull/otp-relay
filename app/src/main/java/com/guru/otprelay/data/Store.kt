package com.guru.otprelay.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Everything the app remembers, held in memory and mirrored to a few strings on disk. Clear the
 * app's data and it is all gone, by design.
 *
 * Mutations are synchronized because messages arrive on a binder thread while the UI reads on the
 * main thread.
 */
object Store {

    private const val KEY_ACTIVE = "active"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_LOGS = "logs"
    private const val KEY_SHORTCUTS = "shortcuts"
    private const val KEY_NUMBERS = "numbers"
    private const val KEY_MY_NUMBER = "my_number"

    private const val MAX_SESSIONS = 25
    private const val MAX_LOGS = 300
    private const val MAX_SHORTCUTS = 8
    private const val MAX_NUMBERS = 6

    private lateinit var store: KeyValueStore
    private val nextId = AtomicLong(1)

    private val _active = MutableStateFlow<Session?>(null)
    val active: StateFlow<Session?> = _active.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _shortcuts = MutableStateFlow<List<Preset>>(emptyList())
    val shortcuts: StateFlow<List<Preset>> = _shortcuts.asStateFlow()

    private val _numbers = MutableStateFlow<List<Target>>(emptyList())
    val numbers: StateFlow<List<Target>> = _numbers.asStateFlow()

    /** Shown to the recipient in the start and stop notices. */
    private val _myNumber = MutableStateFlow("")
    val myNumber: StateFlow<String> = _myNumber.asStateFlow()

    fun init(context: Context) {
        if (::store.isInitialized) return
        load(SharedPrefsStore(context))
    }

    @Synchronized
    fun load(source: KeyValueStore) {
        store = source
        _sessions.value = readList(KEY_SESSIONS, ::sessionFrom)
        _logs.value = readList(KEY_LOGS, ::logFrom)
        _shortcuts.value = readList(KEY_SHORTCUTS, ::presetFrom)
        _numbers.value = readList(KEY_NUMBERS, ::targetFrom)
        _myNumber.value = store.read(KEY_MY_NUMBER).orEmpty()
        _active.value = store.read(KEY_ACTIVE)
            ?.let { sessionFrom(JSONObject(it)) }
            ?.takeIf { it.isActive() }

        nextId.set(
            maxOf(
                _sessions.value.maxOfOrNull { it.id } ?: 0L,
                _logs.value.maxOfOrNull { it.id } ?: 0L,
            ) + 1
        )
    }

    /** Ids come from memory so the forwarding path never touches disk before sending. */
    fun newId(): Long = nextId.getAndIncrement()

    fun currentSession(): Session? = _active.value?.takeIf { it.isActive() }

    @Synchronized
    fun setMyNumber(value: String) {
        _myNumber.value = value
        store.write(mapOf(KEY_MY_NUMBER to value))
    }

    @Synchronized
    fun startSession(target: Target, durationMillis: Long, now: Long = System.currentTimeMillis()): Session {
        // Close out anything still open, so two sessions can never overlap in the history.
        closeActive(now)

        // Typing a number that was once picked from Contacts must not lose the name, otherwise
        // the same person shows a name one time and bare digits the next.
        val resolved = target.copy(name = target.name?.takeIf { it.isNotBlank() } ?: nameFor(target.number))

        val session = Session(newId(), resolved, now, now + durationMillis)
        _active.value = session
        _sessions.value = (listOf(session) + _sessions.value).take(MAX_SESSIONS)

        store.write(
            mapOf(
                KEY_ACTIVE to sessionTo(session).toString(),
                KEY_SESSIONS to writeList(_sessions.value, ::sessionTo),
            )
        )
        return session
    }

    @Synchronized
    fun endSession(now: Long = System.currentTimeMillis()) {
        if (!closeActive(now)) return
        store.write(
            mapOf(
                KEY_ACTIVE to null,
                KEY_SESSIONS to writeList(_sessions.value, ::sessionTo),
            )
        )
    }

    /** Clamps the recorded end time to now so history shows when it really stopped. */
    private fun closeActive(now: Long): Boolean {
        val session = _active.value ?: return false
        val ended = session.copy(expiresAt = minOf(session.expiresAt, now))
        _active.value = null
        _sessions.value = _sessions.value.map { if (it.id == session.id) ended else it }
        return true
    }

    /**
     * Only numbers picked from the phone book can receive OTPs. Being able to pick a contact
     * proves it exists on the device, which is why this never needs READ_CONTACTS.
     */
    fun isKnownNumber(number: String): Boolean =
        _numbers.value.any { sameNumber(it.number, number) }

    /** Records a contact chosen through the system picker as allowed to receive OTPs. */
    @Synchronized
    fun saveContact(target: Target) {
        _numbers.value = (listOf(target) + _numbers.value.filterNot { sameNumber(it.number, target.number) })
            .take(MAX_NUMBERS)
        store.write(mapOf(KEY_NUMBERS to writeList(_numbers.value, ::targetTo)))
    }

    /** The name last seen for a number, so typing it back in still shows who it is. */
    fun nameFor(number: String): String? =
        _numbers.value.firstOrNull { sameNumber(it.number, number) }?.name
            ?: _shortcuts.value.firstOrNull { sameNumber(it.target.number, number) }?.target?.name

    @Synchronized
    fun deleteNumber(target: Target) {
        _numbers.value = _numbers.value.filterNot { sameNumber(it.number, target.number) }
        store.write(mapOf(KEY_NUMBERS to writeList(_numbers.value, ::targetTo)))
    }

    @Synchronized
    fun saveShortcut(target: Target, durationMillis: Long) {
        val shortcut = Preset(target, durationMillis)
        _shortcuts.value = (listOf(shortcut) + _shortcuts.value.filterNot {
            it.target.number == target.number && it.durationMillis == durationMillis
        }).take(MAX_SHORTCUTS)
        store.write(mapOf(KEY_SHORTCUTS to writeList(_shortcuts.value, ::presetTo)))
    }

    @Synchronized
    fun deleteShortcut(shortcut: Preset) {
        _shortcuts.value = _shortcuts.value.filterNot {
            it.target.number == shortcut.target.number && it.durationMillis == shortcut.durationMillis
        }
        store.write(mapOf(KEY_SHORTCUTS to writeList(_shortcuts.value, ::presetTo)))
    }

    @Synchronized
    fun addLog(entry: LogEntry) {
        _logs.value = (listOf(entry) + _logs.value).take(MAX_LOGS)
        store.write(mapOf(KEY_LOGS to writeList(_logs.value, ::logTo)))
    }

    @Synchronized
    fun updateLog(id: Long, status: ForwardStatus, error: String?) {
        _logs.value = _logs.value.map {
            when {
                it.id != id -> it
                // One failed part fails the message; a later success must not paper over it.
                it.status == ForwardStatus.FAILED -> it
                else -> it.copy(status = status, error = error)
            }
        }
        store.write(mapOf(KEY_LOGS to writeList(_logs.value, ::logTo)))
    }

    @Synchronized
    fun clearHistory() {
        _logs.value = emptyList()
        _sessions.value = _active.value?.let { listOf(it) } ?: emptyList()
        store.write(
            mapOf(
                KEY_LOGS to "[]",
                KEY_SESSIONS to writeList(_sessions.value, ::sessionTo),
            )
        )
    }

    private fun <T> readList(key: String, from: (JSONObject) -> T): List<T> {
        val raw = store.read(key) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { from(array.getJSONObject(it)) }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }

    private fun <T> writeList(items: List<T>, to: (T) -> JSONObject): String =
        JSONArray().apply { items.forEach { put(to(it)) } }.toString()

    // Android's org.json renders JSONObject.NULL as the literal text "null" from optString(),
    // while the JVM one returns "". isNull() is the only form that behaves the same on both, so
    // absence is always checked first. Do not "simplify" this to optString().
    //
    // The "null" text is also treated as absent: earlier builds wrote it into stored rows, and
    // those rows would otherwise keep showing the word null in the log for ever.
    private fun optional(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null
        else o.getString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun targetTo(t: Target) = JSONObject()
        .put("number", t.number)
        .put("name", t.name ?: JSONObject.NULL)

    private fun targetFrom(o: JSONObject) = Target(o.getString("number"), optional(o, "name"))

    private fun sessionTo(s: Session) = JSONObject()
        .put("id", s.id)
        .put("target", targetTo(s.target))
        .put("startedAt", s.startedAt)
        .put("expiresAt", s.expiresAt)

    private fun sessionFrom(o: JSONObject) = Session(
        id = o.getLong("id"),
        target = targetFrom(o.getJSONObject("target")),
        startedAt = o.getLong("startedAt"),
        expiresAt = o.getLong("expiresAt"),
    )

    private fun presetTo(p: Preset) = JSONObject()
        .put("target", targetTo(p.target))
        .put("durationMillis", p.durationMillis)

    private fun presetFrom(o: JSONObject) =
        Preset(targetFrom(o.getJSONObject("target")), o.getLong("durationMillis"))

    private fun logTo(e: LogEntry) = JSONObject()
        .put("id", e.id)
        .put("sessionId", e.sessionId)
        .put("arrivedAt", e.arrivedAt)
        .put("from", e.from)
        .put("body", e.body)
        .put("to", e.to)
        .put("latencyMs", e.latencyMs)
        .put("status", e.status.name)
        .put("error", e.error ?: JSONObject.NULL)

    private fun logFrom(o: JSONObject) = LogEntry(
        id = o.getLong("id"),
        sessionId = o.getLong("sessionId"),
        arrivedAt = o.getLong("arrivedAt"),
        from = o.getString("from"),
        body = o.getString("body"),
        to = o.getString("to"),
        latencyMs = o.getLong("latencyMs"),
        status = ForwardStatus.valueOf(o.getString("status")),
        error = optional(o, "error"),
    )
}
