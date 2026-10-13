package com.unloop.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process

data class AppUsage(val packageName: String, val timeMs: Long, val opens: Int)

data class DayUsage(
    val totalMs: Long,
    val unlocks: Int,
    val perApp: Map<String, AppUsage>,
    /** False when Android returned no events at all (e.g. older than its retention window). */
    val hasData: Boolean = true,
) {
    val top: List<AppUsage> get() = perApp.values.filter { it.timeMs >= 60_000 }.sortedByDescending { it.timeMs }
}

/** Today's screen time, built from raw usage events (midnight → now). */
class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    /** Home-screen apps (Unloop and any other launcher). Time on a home screen isn't screen time. */
    private val homeApps: Set<String> by lazy {
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet() + context.packageName
    }

    /**
     * Apps that count as screen time: ones with an app-list icon, minus home screens. Share sheets,
     * permission pop-ups and other system screens are left out, matching Digital Wellbeing.
     */
    private fun countedApps(): Set<String> =
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet() - homeApps

    /** Readable name for any package, including ones without a launcher icon. */
    fun labelOf(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    fun hasAccess(): Boolean {
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun today(): DayUsage = between(History.startOfDay(System.currentTimeMillis()), System.currentTimeMillis())

    /** Usage for [start, end), e.g. one whole past day. */
    fun between(start: Long, end: Long): DayUsage {
        val counted = countedApps()
        var eventCount = 0

        val time = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        var unlocks = 0

        // App currently in the foreground and when it got there.
        var current: String? = null
        var since = 0L
        // Last app the user entered; moving between activities of one app is not a new "open".
        var lastEntered: String? = null

        fun closeCurrent(at: Long) {
            current?.let { time[it] = (time[it] ?: 0L) + (at - since) }
            current = null
        }

        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            eventCount++
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (current != e.packageName) {
                        closeCurrent(e.timeStamp)
                        current = e.packageName
                        since = e.timeStamp
                    }
                    // System screens shown on top of an app don't make the next return a new "open";
                    // going home does.
                    if (e.packageName in counted && lastEntered != e.packageName) {
                        opens[e.packageName] = (opens[e.packageName] ?: 0) + 1
                        lastEntered = e.packageName
                    } else if (e.packageName in homeApps) {
                        lastEntered = e.packageName
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (e.packageName == current) closeCurrent(e.timeStamp)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    closeCurrent(e.timeStamp)
                    lastEntered = null
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }
        closeCurrent(end)

        val perApp = (time.keys + opens.keys)
            .filter { it in counted }
            .associateWith { AppUsage(it, time[it] ?: 0L, opens[it] ?: 0) }
        return DayUsage(
            totalMs = perApp.values.sumOf { it.timeMs },
            unlocks = unlocks,
            perApp = perApp,
            hasData = eventCount > 0,
        )
    }

    /** Apps by foreground time across [start, end), most used first. Uses Android's own aggregates. */
    fun topApps(start: Long, end: Long): List<AppUsage> {
        val counted = countedApps()
        return usm.queryAndAggregateUsageStats(start, end).values
            .filter { it.packageName in counted && it.totalTimeInForeground >= 60_000 }
            .sortedByDescending { it.totalTimeInForeground }
            .map { AppUsage(it.packageName, it.totalTimeInForeground, 0) }
    }
}
