package com.guru.otprelay.forwarding

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.guru.otprelay.data.OtpMatch
import com.guru.otprelay.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ForwardingService : Service() {

    companion object {
        const val ACTION_STOP = "com.guru.otprelay.STOP"
        private const val WATCHDOG_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ForwardingService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ForwardingService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timer: Job? = null
    private var watchdog: Job? = null
    private var smsReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent means the system restarted us after a kill; the session is re-read from
        // its stored absolute expiry, so a restart can never extend the window.
        val session = Store.currentSession()

        // startForeground must happen before any early exit: the service was launched with
        // startForegroundService, and bailing out without it is a hard framework violation.
        Notifications.ensureChannel(this)
        startForeground(Notifications.ID, Notifications.build(this, session))

        // No visible notification means no forwarding. Bail out before a single message is read.
        if (intent?.action == ACTION_STOP || session == null || !Notifications.canPost(this)) {
            finish()
            return START_NOT_STICKY
        }

        registerReceivers()

        timer?.cancel()
        timer = scope.launch {
            delay((session.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
            finish()
        }

        watchdog?.cancel()
        watchdog = scope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!Notifications.isLive(this@ForwardingService)) {
                    finish()
                    return@launch
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        timer?.cancel()
        watchdog?.cancel()
        unregisterReceivers()
        scope.cancel()
        super.onDestroy()
    }

    private fun registerReceivers() {
        if (smsReceiver != null) return

        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onSmsReceived(intent, System.currentTimeMillis())
            }
        }
        // SMS_RECEIVED comes from the telephony process, not from this app, so the receiver has to
        // be exported or it silently never fires. The action is a protected broadcast, so nothing
        // else can spoof it. High priority just gets us earlier in the ordered-broadcast chain.
        ContextCompat.registerReceiver(
            this,
            smsReceiver,
            IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply { priority = 999 },
            ContextCompat.RECEIVER_EXPORTED,
        )

    }

    private fun unregisterReceivers() {
        smsReceiver?.let { unregisterReceiver(it) }
        smsReceiver = null
    }

    private fun onSmsReceived(intent: Intent, arrivedAt: Long) {
        val session = Store.currentSession() ?: return finish()
        // Cheaper than the watchdog's full check, and closes the gap between its ticks.
        if (!Notifications.canPost(this)) return finish()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val body = OtpMatch.join(messages.map { it.messageBody })
        if (!OtpMatch.matches(body)) return

        val from = messages.first().originatingAddress ?: "Unknown"
        Forwarder.forward(this, session, arrivedAt, from, body)
    }

    private fun finish() {
        timer?.cancel()
        watchdog?.cancel()
        // Stop listening first, so nothing can slip through while the session is being torn down.
        unregisterReceivers()
        // Read before ending: on natural expiry currentSession() is already null. endSession()
        // clears it, so a second finish() call sends nothing.
        val ending = Store.active.value
        Store.endSession()
        ending?.let { Forwarder.notifyEnded(this, it) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
