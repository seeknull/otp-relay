package com.guru.otprelay.forwarding

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * A short double beep when an OTP is relayed, so you know it happened without looking. Uses the
 * built-in tone generator rather than a bundled sound file, and rides the notification volume, so
 * silent mode stays silent. Played after the message is already on its way, never before.
 */
object Chime {

    private const val TONE_MS = 350
    private const val RELEASE_DELAY_MS = 600L

    fun play() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, TONE_MS)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, RELEASE_DELAY_MS)
        } catch (e: RuntimeException) {
            // Audio resources can be unavailable; a missing beep must never break forwarding.
        }
    }
}
