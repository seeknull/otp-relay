package com.guru.otprelay.forwarding

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.guru.otprelay.data.ForwardStatus
import com.guru.otprelay.data.LogEntry
import com.guru.otprelay.data.Session
import com.guru.otprelay.data.Store
import java.util.concurrent.atomic.AtomicInteger

object Forwarder {

    const val EXTRA_LOG_ID = "log_id"

    const val SENDER_STARTED = "Session started"
    const val SENDER_ENDED = "Session ended"

    /** ASCII only: a non-GSM-7 character would halve every segment's capacity. */
    private const val FINGERPRINT = "(fwd by OTP Relay)"

    private val requestCode = AtomicInteger(1)

    @Volatile
    private var cached: SmsManager? = null

    private fun smsManager(context: Context): SmsManager = cached ?: synchronized(this) {
        cached ?: run {
            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            cached = manager
            manager
        }
    }

    /**
     * OTPs expire fast, so the send happens before anything is written to disk — the log entry is
     * only persisted once the message is already handed to the modem.
     */
    fun forward(context: Context, session: Session, arrivedAt: Long, from: String, body: String) {
        val logId = Store.newId()
        val error = send(context, session.target.number, "$body\n$FINGERPRINT", logId)
        log(logId, session, arrivedAt, from, body, error)
    }

    fun notifyStarted(context: Context, session: Session, until: String) {
        val from = Store.myNumber.value.trim().takeIf { it.isNotEmpty() }?.let { " from $it" }.orEmpty()
        notice(context, session, SENDER_STARTED, "OTP forwarding to you$from is ON until $until.")
    }

    fun notifyEnded(context: Context, session: Session) {
        val from = Store.myNumber.value.trim().takeIf { it.isNotEmpty() }?.let { " from $it" }.orEmpty()
        notice(context, session, SENDER_ENDED, "OTP forwarding to you$from has been turned OFF.")
    }

    private fun notice(context: Context, session: Session, sender: String, text: String) {
        val logId = Store.newId()
        val at = System.currentTimeMillis()
        val body = "$text $FINGERPRINT"
        val error = send(context, session.target.number, body, logId)
        log(logId, session, at, sender, body, error)
    }

    /** Returns null when the message reached the modem, or a message describing why it did not. */
    private fun send(context: Context, to: String, text: String, logId: Long): String? = try {
        val manager = smsManager(context)
        val parts = manager.divideMessage(text)
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        repeat(parts.size) {
            sentIntents.add(
                PendingIntent.getBroadcast(
                    context,
                    // Distinct per part: PendingIntent identity ignores extras, so a shared
                    // request code would collide across concurrent sends.
                    requestCode.getAndIncrement(),
                    Intent(context, SmsSentReceiver::class.java).putExtra(EXTRA_LOG_ID, logId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        }
        manager.sendMultipartTextMessage(to, null, parts, sentIntents, null)
        null
    } catch (e: Exception) {
        e.message ?: e::class.java.simpleName
    }

    private fun log(
        logId: Long,
        session: Session,
        at: Long,
        from: String,
        body: String,
        error: String?,
    ) {
        Store.addLog(
            LogEntry(
                id = logId,
                sessionId = session.id,
                arrivedAt = at,
                from = from,
                body = body,
                to = session.target.number,
                latencyMs = System.currentTimeMillis() - at,
                status = if (error == null) ForwardStatus.PENDING else ForwardStatus.FAILED,
                error = error,
            )
        )
    }

    fun errorFor(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
        SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
        else -> "Send error $resultCode"
    }
}
