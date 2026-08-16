package com.guru.otprelay

import com.guru.otprelay.data.KeyValueStore

/** Stands in for SharedPreferences so the store can be exercised on the JVM. */
class FakeKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {

    val values = initial.toMutableMap()
    var writeCount = 0
        private set

    override fun read(key: String): String? = values[key]

    override fun write(values: Map<String, String?>) {
        writeCount++
        values.forEach { (key, value) ->
            if (value == null) this.values.remove(key) else this.values[key] = value
        }
    }
}
