package com.guru.otprelay.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.guru.otprelay.forwarding.Notifications

/** What to do about a problem. The screen owns the actual intents and launchers. */
enum class Fix { REQUEST_PERMISSIONS, APP_INFO, NOTIFICATION_SETTINGS, BATTERY }

/**
 * Something standing between the user and a working session. Blocking problems stop the start;
 * the rest are warnings the user can go ahead past.
 */
data class Problem(
    val title: String,
    val detail: String,
    val fixLabel: String,
    val fix: Fix,
    val blocking: Boolean = true,
)

/**
 * Everything checked before a session starts, so the app explains what is wrong in one place
 * rather than failing later with a message the user cannot act on.
 */
fun preflight(context: Context, asked: Boolean): List<Problem> = buildList {

    val missing = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    if (missing.isNotEmpty()) {
        // Android reports "no rationale needed" both before the first ask and after a permanent
        // denial, so the remembered flag is what separates a fresh install from a dead end.
        val willPrompt = !asked || missing.any { canPrompt(context, it) }
        if (willPrompt) {
            add(
                Problem(
                    title = "SMS access is off",
                    detail = "The app needs to read incoming texts during a session and send the " +
                        "code on. Nothing is read outside a session.",
                    fixLabel = "Grant access",
                    fix = Fix.REQUEST_PERMISSIONS,
                )
            )
        } else {
            add(
                Problem(
                    title = "Android is blocking SMS access",
                    detail = "Apps installed from outside an app store cannot be given SMS " +
                        "access until the restriction is lifted, so no prompt appears and the " +
                        "switch stays greyed out.\n\n" +
                        "Open app info, tap ⋮ in the top corner, then " +
                        "“Allow restricted settings”.",
                    fixLabel = "Open app info",
                    fix = Fix.APP_INFO,
                )
            )
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        add(
            Problem(
                title = "Notifications are off",
                detail = "A session always shows a notification while it runs, so forwarding is " +
                    "never happening invisibly. Without it the session refuses to start.",
                fixLabel = "Grant access",
                fix = Fix.REQUEST_PERMISSIONS,
            )
        )
    } else if (!Notifications.canPost(context)) {
        // The permission can be granted while the channel itself is switched off.
        add(
            Problem(
                title = "The session notification is blocked",
                detail = "Its notification channel is switched off. A session will not start " +
                    "without a visible notification, so turn it back on.",
                fixLabel = "Open notification settings",
                fix = Fix.NOTIFICATION_SETTINGS,
            )
        )
    }

    if (!hasSim(context)) {
        add(
            Problem(
                title = "No SIM ready",
                detail = "Codes are sent from your own number over your own SIM, so a working " +
                    "SIM has to be in the phone.",
                fixLabel = "Open app info",
                fix = Fix.APP_INFO,
            )
        )
    }

    if (!ignoresBatteryLimits(context)) {
        add(
            Problem(
                title = "Battery saving may cut a session short",
                detail = "Android can stop the app before the time is up. This only matters for " +
                    "longer sessions, and you can start anyway.",
                fixLabel = "Open battery settings",
                fix = Fix.BATTERY,
                blocking = false,
            )
        )
    }
}

private fun canPrompt(context: Context, permission: String): Boolean {
    val activity = context as? Activity ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

/** getSimState needs no permission, unlike anything that would reveal the number itself. */
private fun hasSim(context: Context): Boolean = try {
    context.getSystemService(TelephonyManager::class.java)?.simState ==
        TelephonyManager.SIM_STATE_READY
} catch (e: SecurityException) {
    true // Never block a start over a check that could not be made.
}

private fun ignoresBatteryLimits(context: Context): Boolean = try {
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
} catch (e: RuntimeException) {
    true
}
