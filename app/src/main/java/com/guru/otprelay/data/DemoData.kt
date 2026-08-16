package com.guru.otprelay.data

/**
 * Made-up contacts and history for screenshots, loaded into memory only. Nothing is written to
 * disk, so the real data is untouched and comes back on the next launch.
 *
 * Debug builds only:
 *   adb shell am start -n com.guru.otprelay/.MainActivity --ez demo true
 */
object DemoData {

    private const val MINUTE = 60_000L

    private class Memory : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(values: Map<String, String?>) {
            values.forEach { (key, value) ->
                if (value == null) this.values.remove(key) else this.values[key] = value
            }
        }
    }

    fun load(now: Long = System.currentTimeMillis()) {
        Store.load(Memory())

        val priya = Target("+919000000011", "Priya")
        val ravi = Target("+919000000022", "Ravi")
        val meera = Target("+919000000033", "Meera")

        listOf(priya, ravi, meera).reversed().forEach { Store.saveContact(it) }
        Store.setMyNumber("+919000000099")
        Store.saveShortcut(meera, 30 * MINUTE)
        Store.saveShortcut(ravi, 5 * MINUTE)
        Store.saveShortcut(priya, 15 * MINUTE)

        val earlier = Store.startSession(ravi, 5 * MINUTE, now = now - 95 * MINUTE)
        Store.addLog(entry(Store.newId(), earlier, now - 95 * MINUTE, "Session started",
            "OTP forwarding to you from +919000000099 is ON until 8:35 am. (fwd by OTP Relay)", 31))
        Store.addLog(entry(Store.newId(), earlier, now - 93 * MINUTE, "VK-AMAZON",
            "123456 is your OTP for login. Never share it.", 14))
        Store.addLog(entry(Store.newId(), earlier, now - 90 * MINUTE, "Session ended",
            "OTP forwarding to you from +919000000099 has been turned OFF. (fwd by OTP Relay)", 11))
        Store.endSession(now = now - 90 * MINUTE)

        val recent = Store.startSession(priya, 15 * MINUTE, now = now - 9 * MINUTE)
        Store.addLog(entry(Store.newId(), recent, now - 9 * MINUTE, "Session started",
            "OTP forwarding to you from +919000000099 is ON until 10:20 am. (fwd by OTP Relay)", 28))
        Store.addLog(entry(Store.newId(), recent, now - 7 * MINUTE, "VM-HDFCBK",
            "Your OTP is 730214, valid for 10 minutes.", 9))
        Store.addLog(entry(Store.newId(), recent, now - 3 * MINUTE, "AX-ICICIB",
            "OTP 481932 for your card ending 4417. Do not share.", 12))
        Store.endSession(now = now - 3 * MINUTE)
    }

    private fun entry(id: Long, session: Session, at: Long, from: String, body: String, latency: Long) =
        LogEntry(
            id = id,
            sessionId = session.id,
            arrivedAt = at,
            from = from,
            body = body,
            to = session.target.number,
            latencyMs = latency,
            status = ForwardStatus.SENT,
            error = null,
        )
}
