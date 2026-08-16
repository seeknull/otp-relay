package com.guru.otprelay.data

import android.content.Context

/** The whole persistence layer. A handful of strings on disk, nothing more. */
interface KeyValueStore {
    fun read(key: String): String?

    /** A null value removes the key. Writes are applied together. */
    fun write(values: Map<String, String?>)
}

class SharedPrefsStore(context: Context) : KeyValueStore {

    private val prefs =
        context.applicationContext.getSharedPreferences("otprelay", Context.MODE_PRIVATE)

    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(values: Map<String, String?>) {
        prefs.edit().apply {
            values.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
        }.apply()
    }
}
