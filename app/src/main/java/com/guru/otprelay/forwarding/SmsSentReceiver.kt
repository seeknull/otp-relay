package com.guru.otprelay.forwarding

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guru.otprelay.data.ForwardStatus
import com.guru.otprelay.data.Store

/**
 * Declared in the manifest rather than registered by the service: the last message of a session is
 * the "stopped" notice, and its result arrives after the service has already torn itself down.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Forwarder.EXTRA_LOG_ID, -1L)
        if (id < 0) return
        Store.init(context)
        if (resultCode == Activity.RESULT_OK) {
            Store.updateLog(id, ForwardStatus.SENT, null)
        } else {
            Store.updateLog(id, ForwardStatus.FAILED, Forwarder.errorFor(resultCode))
        }
    }
}
