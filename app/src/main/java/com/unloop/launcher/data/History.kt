package com.unloop.launcher.data

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One day of history. [known] is false when Android had no usage data for that day. */
data class DayLog(
    val dayStart: Long,
    val totalMs: Long = 0L,
    val unlocks: Int = 0,
    val turnedBack: Int = 0,
    val openedAnyway: Int = 0,
    val known: Boolean = false,
)

/**
 * Per-day totals, kept by Unloop because Android only keeps raw usage events for about a week.
 * Stored as "known,totalMs,unlocks,turnedBack,openedAnyway" under "d_yyyyMMdd".
 */
class History(context: Context) {
    private val sp = context.getSharedPreferences("unloop_history", Context.MODE_PRIVATE)

    fun get(dayStart: Long): DayLog? {
        val parts = sp.getString(key(dayStart), null)?.split(",") ?: return null
        if (parts.size < 5) return null
        return DayLog(
            dayStart = dayStart,
            known = parts[0] == "1",
            totalMs = parts[1].toLongOrNull() ?: 0L,
            unlocks = parts[2].toIntOrNull() ?: 0,
            turnedBack = parts[3].toIntOrNull() ?: 0,
            openedAnyway = parts[4].toIntOrNull() ?: 0,
        )
    }

    /** Stores usage totals, keeping the day's pause counts. */
    fun saveUsage(dayStart: Long, totalMs: Long, unlocks: Int, known: Boolean) {
        val old = get(dayStart) ?: DayLog(dayStart)
        put(old.copy(totalMs = totalMs, unlocks = unlocks, known = known))
    }

    /** Counts one pause-screen outcome for today. */
    fun recordPause(turnedBack: Boolean) {
        val today = startOfDay(System.currentTimeMillis())
        val old = get(today) ?: DayLog(today)
        put(
            if (turnedBack) old.copy(turnedBack = old.turnedBack + 1)
            else old.copy(openedAnyway = old.openedAnyway + 1),
        )
    }

    private fun put(log: DayLog) {
        sp.edit {
            putString(
                key(log.dayStart),
                listOf(if (log.known) 1 else 0, log.totalMs, log.unlocks, log.turnedBack, log.openedAnyway).joinToString(","),
            )
        }
    }

    companion object {
        fun dayKey(dayStart: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(dayStart))
        private fun key(dayStart: Long) = "d_" + dayKey(dayStart)

        fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /** Midnight [days] days before (negative) or after the day containing [dayStart]. DST-safe. */
        fun shiftDays(dayStart: Long, days: Int): Long = Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
    }
}
