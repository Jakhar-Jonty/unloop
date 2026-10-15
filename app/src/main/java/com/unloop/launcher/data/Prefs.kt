package com.unloop.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.content.edit

const val MAX_FAVORITES = 6

/** User choices, keyed by package name. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("unloop", Context.MODE_PRIVATE)

    /** First-run setup has been completed (or skipped to the end). */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) = sp.edit { putBoolean("onboarded", value) }

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(value) = sp.edit { putBoolean("seeded", value) }

    var favorites: List<String>
        get() = sp.getString("favorites", "").orEmpty().split(",").filter { it.isNotBlank() }
        set(value) = sp.edit { putString("favorites", value.joinToString(",")) }

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()).orEmpty().toSet()
        set(value) = sp.edit { putStringSet("hidden", value) }

    var paused: Set<String>
        get() = sp.getStringSet("paused", emptySet()).orEmpty().toSet()
        set(value) = sp.edit { putStringSet("paused", value) }

    var dailyLimitMinutes: Int
        get() = sp.getInt("daily_limit", 120)
        set(value) = sp.edit { putInt("daily_limit", value) }

    /** Apps still reachable during Focus mode. Null until first chosen. */
    var focusApps: Set<String>?
        get() = if (sp.contains("focus_apps")) sp.getStringSet("focus_apps", emptySet()).orEmpty().toSet() else null
        set(value) = sp.edit { putStringSet("focus_apps", value) }

    /** Epoch millis when Focus mode ends; 0 = off, [FOCUS_UNTIL_STOPPED] = no end time. */
    var focusUntil: Long
        get() = sp.getLong("focus_until", 0L)
        set(value) = sp.edit { putLong("focus_until", value) }

    /** User wants Do Not Disturb while focusing. */
    var focusSilence: Boolean
        get() = sp.getBoolean("focus_silence", true)
        set(value) = sp.edit { putBoolean("focus_silence", value) }

    /** True while Unloop itself has Do Not Disturb switched on. */
    var focusSilenced: Boolean
        get() = sp.getBoolean("focus_silenced", false)
        set(value) = sp.edit { putBoolean("focus_silenced", value) }

    /** Interruption filter to restore when Focus mode ends. */
    var savedInterruptionFilter: Int
        get() = sp.getInt("saved_filter", 0)
        set(value) = sp.edit { putInt("saved_filter", value) }

    /** Epoch millis until which settings can only be made stricter; 0 = off. */
    var strictUntil: Long
        get() = sp.getLong("strict_until", 0L)
        set(value) = sp.edit { putLong("strict_until", value) }

    var showQuote: Boolean
        get() = sp.getBoolean("show_quote", true)
        set(value) = sp.edit { putBoolean("show_quote", value) }

    var showNextEvent: Boolean
        get() = sp.getBoolean("show_next_event", false)
        set(value) = sp.edit { putBoolean("show_next_event", value) }

    /** Daily limit in minutes per package. Stored as "package=minutes". */
    var appLimits: Map<String, Int>
        get() = sp.getStringSet("app_limits", emptySet()).orEmpty()
            .mapNotNull { e -> e.split("=").takeIf { it.size == 2 }?.let { (p, m) -> m.toIntOrNull()?.let { p to it } } }
            .toMap()
        set(value) = sp.edit { putStringSet("app_limits", value.map { (p, m) -> "$p=$m" }.toSet()) }

    /** Extra seconds added to the pause for each earlier open of that app today; 0 = no escalation. */
    var pauseStepSeconds: Int
        get() = sp.getInt("pause_step", 2)
        set(value) = sp.edit { putInt("pause_step", value) }

    var showTasks: Boolean
        get() = sp.getBoolean("show_tasks", true)
        set(value) = sp.edit { putBoolean("show_tasks", value) }

    var gestures: Boolean
        get() = sp.getBoolean("gestures", true)
        set(value) = sp.edit { putBoolean("gestures", value) }

    var schedules: List<Schedule>
        get() = sp.getString("schedules", null)?.let(Schedule::decodeList) ?: Schedule.defaults
        set(value) = sp.edit { putString("schedules", Schedule.encodeList(value)) }

    /** Schedule that started the current Focus session, or "" when started by hand. */
    var focusSchedule: String
        get() = sp.getString("focus_schedule", "").orEmpty()
        set(value) = sp.edit { putString("focus_schedule", value) }

    /** A scheduled window the user ended early is not restarted before this time. */
    var scheduleSkipUntil: Long
        get() = sp.getLong("schedule_skip_until", 0L)
        set(value) = sp.edit { putLong("schedule_skip_until", value) }

    /** Unloop draws today's screen time, streak and a quote as the lock-screen wallpaper. */
    var lockWallpaper: Boolean
        get() = sp.getBoolean("lock_wallpaper", false)
        set(value) = sp.edit { putBoolean("lock_wallpaper", value) }

    /** "compact" (card only) or "full" (card, plant and quote). */
    var lockStyle: String
        get() = sp.getString("lock_style", LOCK_COMPACT).orEmpty()
        set(value) = sp.edit { putString("lock_style", value) }

    /** Where the card sits, so it can dodge each phone's own lock clock: "top", "middle" or "bottom". */
    var lockPosition: String
        get() = sp.getString("lock_position", LOCK_BOTTOM).orEmpty()
        set(value) = sp.edit { putString("lock_position", value) }

    var lastWallpaperAt: Long
        get() = sp.getLong("last_wallpaper_at", 0L)
        set(value) = sp.edit { putLong("last_wallpaper_at", value) }

    /** Last computed streak, so the wallpaper can be drawn without the launcher running. */
    var lastStreak: Int
        get() = sp.getInt("last_streak", 0)
        set(value) = sp.edit { putInt("last_streak", value) }

    /** Day (yyyyMMdd) the weekly reflection was last opened, to stop nagging. */
    var reflectionSeenDay: String
        get() = sp.getString("reflection_seen", "").orEmpty()
        set(value) = sp.edit { putString("reflection_seen", value) }
}

const val FOCUS_UNTIL_STOPPED = Long.MAX_VALUE

const val LOCK_COMPACT = "compact"
const val LOCK_FULL = "full"
const val LOCK_TOP = "top"
const val LOCK_MIDDLE = "middle"
const val LOCK_BOTTOM = "bottom"

/** First-run picks: core apps on the home screen, known time sinks behind the pause. */
object Defaults {
    private val distracting = setOf(
        "com.instagram.android",
        "com.instagram.barcelona",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.twitter.android",
        "com.snapchat.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.reddit.frontpage",
        "com.pinterest",
        "in.mohalla.sharechat",
        "in.mohalla.video",
    )

    fun paused(installed: Set<String>): Set<String> = distracting intersect installed

    /** Calls, messages, maps and clock: what you still need while focusing. */
    fun focusApps(context: Context, installed: Set<String>): Set<String> {
        val candidates = listOfNotNull(
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            Telephony.Sms.getDefaultSmsPackage(context),
            "com.google.android.apps.maps",
            "com.google.android.deskclock",
        )
        return candidates.filter { it in installed }.toSet()
    }

    fun favorites(context: Context, installed: Set<String>): List<String> {
        val pm = context.packageManager
        val camera = pm.resolveActivity(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val candidates = listOfNotNull(
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            Telephony.Sms.getDefaultSmsPackage(context),
            camera,
            "com.google.android.apps.maps",
            "com.google.android.keep",
        )
        return candidates.filter { it in installed }.distinct().take(5)
    }
}
