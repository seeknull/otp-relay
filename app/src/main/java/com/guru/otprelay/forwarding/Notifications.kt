package com.guru.otprelay.forwarding

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.guru.otprelay.MainActivity
import com.guru.otprelay.R
import com.guru.otprelay.data.Session
import com.guru.otprelay.ui.formatClock

object Notifications {

    const val ID = 1
    private const val CHANNEL = "forwarding"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Active forwarding", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Shown while OTP forwarding is switched on" }
        )
    }

    /**
     * The notification is the only outward sign that forwarding is on, so it is treated as a
     * precondition rather than a nicety: if it cannot be shown, or is not actually on screen,
     * the session is stopped. Silently forwarding is worse than not forwarding at all.
     */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Asks the system whether our notification is genuinely posted right now. */
    fun isLive(context: Context): Boolean {
        if (!canPost(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications.any { it.id == ID && it.packageName == context.packageName }
    }

    /** A null session means we are only satisfying startForeground before shutting down. */
    fun build(context: Context, session: Session?): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            context, 0,
            Intent(context, ForwardingService::class.java).setAction(ForwardingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Forwarding OTPs")
            .setContentText(
                session?.let { "To ${it.target.display} until ${formatClock(it.expiresAt)}" }
                    ?: "Stopping"
            )
            .setColor(0xFFE63946.toInt())
            .setColorized(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }
}
