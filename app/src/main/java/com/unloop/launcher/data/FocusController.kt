package com.unloop.launcher.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts and ends Focus mode, including the optional Do Not Disturb and the end-of-session alarm. */
object FocusController {

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    /** [forceSilence] silences even if the user's Focus default lets notifications through (Sleep). */
    fun start(context: Context, prefs: Prefs, until: Long, forceSilence: Boolean = false) {
        prefs.focusUntil = until
        prefs.focusSchedule = ""
        val nm = context.getSystemService(NotificationManager::class.java)
        if ((prefs.focusSilence || forceSilence) && nm.isNotificationPolicyAccessGranted && !prefs.focusSilenced) {
            prefs.savedInterruptionFilter = nm.currentInterruptionFilter
            // Priority-only follows the user's own DND rules (starred contacts, repeat callers, alarms).
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            prefs.focusSilenced = true
        }
        if (until != FOCUS_UNTIL_STOPPED) {
            // Inexact is fine: the launcher also ends expired sessions whenever it is shown.
            context.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, endIntent(context))
        }
    }

    fun end(context: Context, prefs: Prefs) {
        prefs.focusUntil = 0L
        prefs.focusSchedule = ""
        if (prefs.focusSilenced) {
            val nm = context.getSystemService(NotificationManager::class.java)
            // Only undo our own change; if the user switched DND themselves since, leave it.
            if (nm.isNotificationPolicyAccessGranted &&
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
            ) {
                val saved = prefs.savedInterruptionFilter
                nm.setInterruptionFilter(
                    if (saved == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL else saved,
                )
            }
            prefs.focusSilenced = false
        }
        context.getSystemService(AlarmManager::class.java).cancel(endIntent(context))
    }

    /** Ends the session if its time has passed. Returns true if it did. */
    fun endIfExpired(context: Context, prefs: Prefs): Boolean {
        val until = prefs.focusUntil
        if (until == 0L || System.currentTimeMillis() < until) return false
        end(context, prefs)
        return true
    }

    private fun endIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, FocusEndReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Fires when a timed Focus session should end, even if the launcher is not on screen. */
class FocusEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FocusController.endIfExpired(context, Prefs(context))
    }
}
