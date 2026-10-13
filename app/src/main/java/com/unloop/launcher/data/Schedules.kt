package com.unloop.launcher.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * A repeating Focus window. [days] is a bitmask with Monday = bit 0 … Sunday = bit 6, and refers
 * to the day the window starts. [start] and [end] are minutes after midnight; end < start means
 * the window runs past midnight (e.g. Sleep 23:00–07:00).
 */
data class Schedule(
    val id: String,
    val enabled: Boolean,
    val days: Int,
    val start: Int,
    val end: Int,
    /** Always silence notifications in this window, whatever the Focus default is. */
    val silence: Boolean,
) {
    fun runsOn(dayIndex: Int): Boolean = days and (1 shl dayIndex) != 0

    companion object {
        const val WORK = "work"
        const val WIND_DOWN = "wind_down"
        const val SLEEP = "sleep"
        const val WEEKDAYS = 0b0011111
        const val EVERY_DAY = 0b1111111

        val defaults = listOf(
            Schedule(WORK, enabled = false, days = WEEKDAYS, start = 10 * 60, end = 18 * 60, silence = false),
            Schedule(WIND_DOWN, enabled = false, days = EVERY_DAY, start = 22 * 60, end = 23 * 60, silence = false),
            Schedule(SLEEP, enabled = false, days = EVERY_DAY, start = 23 * 60, end = 7 * 60, silence = true),
        )

        fun encodeList(list: List<Schedule>): String =
            list.joinToString(";") { "${it.id},${if (it.enabled) 1 else 0},${it.days},${it.start},${it.end},${if (it.silence) 1 else 0}" }

        fun decodeList(text: String): List<Schedule> {
            val parsed = text.split(";").mapNotNull { row ->
                val f = row.split(",")
                if (f.size != 6) return@mapNotNull null
                Schedule(f[0], f[1] == "1", f[2].toIntOrNull() ?: return@mapNotNull null, f[3].toIntOrNull() ?: 0, f[4].toIntOrNull() ?: 0, f[5] == "1")
            }
            // Keep the preset order; fill any missing preset with its default.
            return defaults.map { d -> parsed.firstOrNull { it.id == d.id } ?: d }
        }
    }
}

/** A concrete occurrence of a schedule. */
data class ScheduleWindow(val schedule: Schedule, val startMs: Long, val endMs: Long)

/** Turns schedules into Focus sessions, by alarm in the background and on every launcher refresh. */
object ScheduleController {

    /** Monday = 0 … Sunday = 6. */
    private fun dayIndex(cal: Calendar) = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun windowStarting(schedule: Schedule, dayStart: Long): ScheduleWindow? {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        if (!schedule.enabled || !schedule.runsOn(dayIndex(cal))) return null
        val start = dayStart + schedule.start * 60_000L
        val endDay = if (schedule.end <= schedule.start) History.shiftDays(dayStart, 1) else dayStart
        val end = endDay + schedule.end * 60_000L
        return ScheduleWindow(schedule, start, end)
    }

    /** The window covering [now], if any. Checks windows that started today or yesterday. */
    fun activeWindow(schedules: List<Schedule>, now: Long = System.currentTimeMillis()): ScheduleWindow? {
        val today = History.startOfDay(now)
        return schedules.flatMap { s -> listOfNotNull(windowStarting(s, today), windowStarting(s, History.shiftDays(today, -1))) }
            .filter { now in it.startMs until it.endMs }
            .maxByOrNull { it.endMs }
    }

    private fun nextStart(schedules: List<Schedule>, now: Long): Long? {
        val today = History.startOfDay(now)
        return (0..7).flatMap { d -> schedules.mapNotNull { windowStarting(it, History.shiftDays(today, d)) } }
            .map { it.startMs }
            .filter { it > now }
            .minOrNull()
    }

    /** Starts Focus if a window is active, then sets an alarm for the next window. Returns true if Focus changed. */
    fun apply(context: Context, prefs: Prefs): Boolean {
        val now = System.currentTimeMillis()
        val window = activeWindow(prefs.schedules, now)
        var changed = false
        if (window != null && prefs.focusUntil < window.endMs && prefs.scheduleSkipUntil < window.endMs) {
            FocusController.start(context, prefs, window.endMs, forceSilence = window.schedule.silence)
            prefs.focusSchedule = window.schedule.id
            changed = true
        }
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 1, Intent(context, ScheduleReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = nextStart(prefs.schedules, now)
        if (next == null) alarm.cancel(intent) else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent)
        return changed
    }
}

/** Starts scheduled Focus at the window's start time, and re-arms alarms after a reboot. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        FocusController.endIfExpired(context, prefs)
        ScheduleController.apply(context, prefs)
    }
}
