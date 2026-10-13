package com.unloop.launcher

import android.app.Application
import android.app.role.RoleManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unloop.launcher.data.AppInfo
import com.unloop.launcher.data.AppRepository
import com.unloop.launcher.data.AppUsage
import com.unloop.launcher.data.CalendarRepository
import com.unloop.launcher.data.DayLog
import com.unloop.launcher.data.DayUsage
import com.unloop.launcher.data.Defaults
import com.unloop.launcher.data.FOCUS_UNTIL_STOPPED
import com.unloop.launcher.data.FocusController
import com.unloop.launcher.data.Gate
import com.unloop.launcher.data.Gatekeeper
import com.unloop.launcher.data.Schedule
import com.unloop.launcher.data.ScheduleController
import com.unloop.launcher.service.UnloopAccessibilityService
import com.unloop.launcher.data.History
import com.unloop.launcher.data.LockWallpaper
import com.unloop.launcher.data.MAX_FAVORITES
import com.unloop.launcher.data.NextEvent
import com.unloop.launcher.data.Prefs
import com.unloop.launcher.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

data class UiState(
    val apps: List<AppInfo> = emptyList(),
    val favorites: List<AppInfo> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val paused: Set<String> = emptySet(),
    val usage: DayUsage? = null,
    val hasUsageAccess: Boolean = true,
    val limitMinutes: Int = 120,
    val isDefaultLauncher: Boolean = true,
    val focusApps: Set<String> = emptySet(),
    val focusUntil: Long = 0L,
    val focusSilence: Boolean = true,
    val focusSilenced: Boolean = false,
    val hasDndAccess: Boolean = false,
    /** Sunday nudge on the home screen, until the reflection is opened that day. */
    val showReflectionNudge: Boolean = false,
    val week: WeekReport? = null,
    /** Names for packages seen in usage data that have no launcher entry. */
    val extraLabels: Map<String, String> = emptyMap(),
    /** Completed days in a row under the limit. Null until usage access is granted. */
    val streak: Int? = null,
    val strictUntil: Long = 0L,
    val showQuote: Boolean = true,
    val showNextEvent: Boolean = false,
    val hasCalendarAccess: Boolean = false,
    val nextEvent: NextEvent? = null,
    /** Index into the quotes array; changes on every unlock. */
    val quoteIndex: Int = 0,
    val appLimits: Map<String, Int> = emptyMap(),
    val pauseStepSeconds: Int = 2,
    val gestures: Boolean = true,
    val schedules: List<Schedule> = Schedule.defaults,
    /** Schedule that started the current Focus session; "" when started by hand. */
    val focusSchedule: String = "",
    /** The accessibility service is on, so pauses and limits apply to apps opened anywhere. */
    val pauseEverywhere: Boolean = false,
    val lockWallpaper: Boolean = false,
) {
    fun isFocusing(now: Long = System.currentTimeMillis()): Boolean = now < focusUntil

    fun isStrict(now: Long = System.currentTimeMillis()): Boolean = now < strictUntil

    /** True once today's screen time has passed the limit. */
    val overToday: Boolean get() = (usage?.totalMs ?: 0L) >= limitMinutes * 60_000L

    fun labelFor(packageName: String): String =
        apps.firstOrNull { it.packageName == packageName }?.label ?: extraLabels[packageName] ?: packageName
}

/** Last 7 days (oldest first, today last) compared with the 7 before. */
data class WeekReport(
    val days: List<DayLog>,
    val avgMs: Long,
    /** Null when there is no usable data for the previous week. */
    val lastWeekAvgMs: Long?,
    val avgUnlocks: Int,
    val daysUnderLimit: Int,
    val knownDays: Int,
    val turnedBack: Int,
    val openedAnyway: Int,
    val topApps: List<AppUsage>,
)

/** One-off messages for the UI to show (as a toast). Text lives in string resources. */
sealed interface Notice {
    data class StrictBlocked(val until: Long) : Notice
    data object HomeFull : Notice
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val appsRepo = AppRepository(app)
    private val usageRepo = UsageRepository(app)
    private val calendarRepo = CalendarRepository(app)
    private val prefs = Prefs(app)
    private val history = History(app)
    private val roleManager = app.getSystemService(RoleManager::class.java)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _notices = Channel<Notice>(Channel.BUFFERED)
    val notices: Flow<Notice> = _notices.receiveAsFlow()

    private val stopObserving = appsRepo.observe { reloadApps() }

    /** Streak over completed days only changes once a day; cached against the day it was computed. */
    private var streakDay = ""
    private var pastStreak = 0

    /** Language the app labels were loaded in; system app names are translated. */
    private var labelsLocale: Locale? = null

    private val quoteCount = app.resources.getStringArray(R.array.quotes).size

    init {
        _state.update { it.copy(quoteIndex = Random.nextInt(quoteCount)) }
        reloadApps()
    }

    /** Unlock count last seen; a higher count means the phone was unlocked since. */
    private var seenUnlocks = -1
    private var lockedFromHome = false

    fun onLockedFromHome() {
        lockedFromHome = true
    }

    /** Picks a different random quote: a fresh one after every unlock. */
    private fun nextQuote() {
        if (quoteCount < 2) return
        _state.update { it.copy(quoteIndex = (it.quoteIndex + 1 + Random.nextInt(quoteCount - 1)) % quoteCount) }
    }

    fun onResume() {
        if (lockedFromHome) {
            lockedFromHome = false
            // This unlock is handled; the next usage refresh should just record the new count.
            seenUnlocks = -1
            nextQuote()
        }
        if (labelsLocale != null && labelsLocale != Locale.getDefault()) {
            _state.update { it.copy(extraLabels = emptyMap()) }
            reloadApps()
        }
        refreshUsage()
        refreshSystemState()
    }

    fun refreshUsage() {
        if (FocusController.endIfExpired(getApplication(), prefs)) publish()
        if (ScheduleController.apply(getApplication(), prefs)) publish()
        if (prefs.strictUntil in 1..System.currentTimeMillis()) {
            prefs.strictUntil = 0L
            publish()
        }
        viewModelScope.launch {
            val hasAccess = usageRepo.hasAccess()
            val usage = if (hasAccess) withContext(Dispatchers.Default) { usageRepo.today() } else null
            if (usage != null) {
                // Unlocked while away in another app: new quote for when home shows again.
                if (seenUnlocks in 0 until usage.unlocks) nextQuote()
                seenUnlocks = usage.unlocks
                history.saveUsage(History.startOfDay(System.currentTimeMillis()), usage.totalMs, usage.unlocks, known = true)
            }
            val streak = if (hasAccess) withContext(Dispatchers.Default) { completedStreak() } else null
            if (streak != null) prefs.lastStreak = streak
            // Keep the lock-screen wallpaper reasonably fresh while the launcher is in use.
            if (prefs.lockWallpaper && System.currentTimeMillis() - prefs.lastWallpaperAt > 15 * 60_000L) {
                withContext(Dispatchers.IO) { LockWallpaper.update(getApplication()) }
            }
            val event = if (prefs.showNextEvent) withContext(Dispatchers.IO) { calendarRepo.next() } else null
            _state.update { it.copy(hasUsageAccess = hasAccess, usage = usage, streak = streak, nextEvent = event) }
            usage?.let { labelUnknown(it.perApp.keys) }
        }
    }

    /** Re-reads system state the user may have changed in Settings. */
    fun refreshSystemState() {
        _state.update {
            it.copy(
                isDefaultLauncher = roleManager.isRoleHeld(RoleManager.ROLE_HOME),
                hasDndAccess = FocusController.hasDndAccess(getApplication()),
                hasCalendarAccess = calendarRepo.hasPermission(),
                pauseEverywhere = UnloopAccessibilityService.isEnabled(getApplication()),
            )
        }
    }

    fun recordPause(turnedBack: Boolean) = history.recordPause(turnedBack)

    /** How [app] should open: directly, after a pause, or not at all (limit reached). */
    suspend fun gateFor(app: AppInfo): Gate =
        withContext(Dispatchers.Default) { Gatekeeper.check(prefs, usageRepo, app.packageName) }

    /** User got past the pause (or took 5 more minutes) and is opening the app. */
    fun openAfterGate(app: AppInfo, gate: Gate) {
        if (gate is Gate.Limit) Gatekeeper.grantExtra(app.packageName) else if (gate is Gate.Pause) recordPause(turnedBack = false)
        Gatekeeper.pass(app.packageName)
        launch(app)
    }

    fun loadWeek() {
        prefs.reflectionSeenDay = History.dayKey(History.startOfDay(System.currentTimeMillis()))
        publish()
        viewModelScope.launch {
            val report = withContext(Dispatchers.Default) { buildWeek() }
            _state.update { it.copy(week = report) }
            report?.let { labelUnknown(it.topApps.map { app -> app.packageName }) }
        }
    }

    fun launch(app: AppInfo) = appsRepo.launch(app)

    fun openAppInfo(app: AppInfo) = appsRepo.openAppInfo(app)

    fun toggleFavorite(packageName: String) {
        val current = prefs.favorites
        prefs.favorites = when {
            packageName in current -> current - packageName
            current.size >= MAX_FAVORITES -> return notify(Notice.HomeFull)
            else -> current + packageName
        }
        publish()
    }

    // In strict mode, anything that loosens a setting is refused. Tightening is always allowed.

    fun toggleHidden(packageName: String) {
        if (packageName in prefs.hidden && blockedByStrict()) return
        prefs.hidden = prefs.hidden.toggle(packageName)
        publish()
    }

    fun togglePaused(packageName: String) {
        if (packageName in prefs.paused && blockedByStrict()) return
        prefs.paused = prefs.paused.toggle(packageName)
        publish()
    }

    fun setDailyLimit(minutes: Int) {
        val next = minutes.coerceIn(15, 8 * 60)
        if (next > prefs.dailyLimitMinutes && blockedByStrict()) return
        prefs.dailyLimitMinutes = next
        streakDay = ""
        publish()
        refreshUsage()
    }

    fun toggleFocusApp(packageName: String) {
        val current = prefs.focusApps.orEmpty()
        if (packageName !in current && blockedByStrict()) return
        prefs.focusApps = current.toggle(packageName)
        publish()
    }

    /** [minutes] null = until stopped by hand. */
    fun startFocus(minutes: Int?) {
        val until = minutes?.let { System.currentTimeMillis() + it * 60_000L } ?: FOCUS_UNTIL_STOPPED
        FocusController.start(getApplication(), prefs, until)
        publish()
    }

    fun endFocus() {
        if (blockedByStrict()) return
        // Ending a scheduled session early: don't restart it until its window is over.
        if (prefs.focusSchedule.isNotEmpty()) prefs.scheduleSkipUntil = prefs.focusUntil
        FocusController.end(getApplication(), prefs)
        publish()
    }

    fun setFocusSilence(on: Boolean) {
        prefs.focusSilence = on
        publish()
    }

    /** [minutes] null removes the limit. Removing or raising a limit counts as loosening. */
    fun setAppLimit(packageName: String, minutes: Int?) {
        val current = prefs.appLimits[packageName]
        val loosens = current != null && (minutes == null || minutes > current)
        if (loosens && blockedByStrict()) return
        prefs.appLimits = if (minutes == null) prefs.appLimits - packageName else prefs.appLimits + (packageName to minutes)
        publish()
    }

    fun setPauseStep(seconds: Int) {
        if (seconds < prefs.pauseStepSeconds && blockedByStrict()) return
        prefs.pauseStepSeconds = seconds
        publish()
    }

    fun setLockWallpaper(on: Boolean) {
        prefs.lockWallpaper = on
        publish()
        viewModelScope.launch(Dispatchers.IO) {
            if (on) LockWallpaper.update(getApplication(), force = true) else LockWallpaper.clear(getApplication())
        }
    }

    fun setGestures(on: Boolean) {
        prefs.gestures = on
        publish()
    }

    /** Turning a schedule on is always allowed; any other change counts as loosening in strict mode. */
    fun updateSchedule(schedule: Schedule) {
        val old = prefs.schedules.firstOrNull { it.id == schedule.id }
        val onlyEnabling = old != null && old.copy(enabled = true) == schedule
        if (!onlyEnabling && blockedByStrict()) return
        prefs.schedules = prefs.schedules.map { if (it.id == schedule.id) schedule else it }
        ScheduleController.apply(getApplication(), prefs)
        publish()
    }

    fun startStrict(days: Int) {
        // Extending is fine; shortening an active lock is not.
        val until = System.currentTimeMillis() + days * 24 * 60 * 60_000L
        if (until > prefs.strictUntil) prefs.strictUntil = until
        publish()
    }

    fun setShowQuote(on: Boolean) {
        prefs.showQuote = on
        publish()
    }

    fun setShowNextEvent(on: Boolean) {
        prefs.showNextEvent = on
        publish()
        refreshSystemState()
        refreshUsage()
    }

    private fun blockedByStrict(): Boolean {
        val until = prefs.strictUntil
        if (System.currentTimeMillis() >= until) return false
        notify(Notice.StrictBlocked(until))
        return true
    }

    private fun notify(notice: Notice) {
        _notices.trySend(notice)
    }

    private fun labelUnknown(packages: Collection<String>) {
        val known = _state.value.apps.map { it.packageName }.toSet() + _state.value.extraLabels.keys
        val missing = packages.filter { it !in known }
        if (missing.isEmpty()) return
        val labels = missing.associateWith(usageRepo::labelOf)
        _state.update { it.copy(extraLabels = it.extraLabels + labels) }
    }

    /**
     * The last [count] days, oldest first, today last. Today and the past week are read from
     * Android's events (the stored copy may be a partial day); older days come from our history.
     */
    private fun recentDays(count: Int): List<DayLog> {
        val today = History.startOfDay(System.currentTimeMillis())
        return (count - 1 downTo 0).map { back ->
            val start = History.shiftDays(today, -back)
            val stored = history.get(start)
            when {
                back == 0 -> {
                    val usage = usageRepo.today()
                    history.saveUsage(start, usage.totalMs, usage.unlocks, known = true)
                    history.get(start)!!
                }
                back > 6 -> stored ?: DayLog(start)
                else -> {
                    val usage = usageRepo.between(start, History.shiftDays(start, 1))
                    if (usage.hasData || stored?.known != true) {
                        history.saveUsage(start, usage.totalMs, usage.unlocks, known = usage.hasData)
                    }
                    history.get(start)!!
                }
            }
        }
    }

    /** Consecutive completed days (yesterday backwards) under the limit. An unknown day ends it. */
    private fun completedStreak(): Int {
        val dayKey = History.dayKey(History.startOfDay(System.currentTimeMillis()))
        if (dayKey == streakDay) return pastStreak
        val limitMs = prefs.dailyLimitMinutes * 60_000L
        val past = recentDays(61).dropLast(1).asReversed()
        pastStreak = past.takeWhile { it.known && it.totalMs < limitMs }.size
        streakDay = dayKey
        return pastStreak
    }

    private fun buildWeek(): WeekReport? {
        if (!usageRepo.hasAccess()) return null
        val days = recentDays(14)
        val lastWeek = days.take(7).filter { it.known }
        val thisWeek = days.drop(7)
        val known = thisWeek.filter { it.known }
        val limitMs = prefs.dailyLimitMinutes * 60_000L
        return WeekReport(
            days = thisWeek,
            avgMs = if (known.isEmpty()) 0L else known.sumOf { it.totalMs } / known.size,
            lastWeekAvgMs = if (lastWeek.isEmpty()) null else lastWeek.sumOf { it.totalMs } / lastWeek.size,
            avgUnlocks = if (known.isEmpty()) 0 else known.sumOf { it.unlocks } / known.size,
            daysUnderLimit = known.count { it.totalMs < limitMs },
            knownDays = known.size,
            turnedBack = thisWeek.sumOf { it.turnedBack },
            openedAnyway = thisWeek.sumOf { it.openedAnyway },
            topApps = usageRepo.topApps(thisWeek.first().dayStart, System.currentTimeMillis()).take(3),
        )
    }

    private fun reloadApps() {
        viewModelScope.launch {
            labelsLocale = Locale.getDefault()
            val apps = withContext(Dispatchers.IO) { appsRepo.loadApps() }
            if (!prefs.seeded && apps.isNotEmpty()) {
                val installed = apps.map { it.packageName }.toSet()
                prefs.favorites = Defaults.favorites(getApplication(), installed)
                prefs.paused = Defaults.paused(installed)
                prefs.seeded = true
            }
            if (prefs.focusApps == null && apps.isNotEmpty()) {
                prefs.focusApps = Defaults.focusApps(getApplication(), apps.map { it.packageName }.toSet())
            }
            publish(apps)
        }
    }

    private fun publish(apps: List<AppInfo> = _state.value.apps) {
        val favorites = prefs.favorites.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        _state.update {
            it.copy(
                apps = apps,
                favorites = favorites,
                hidden = prefs.hidden,
                paused = prefs.paused,
                limitMinutes = prefs.dailyLimitMinutes,
                focusApps = prefs.focusApps.orEmpty(),
                focusUntil = prefs.focusUntil,
                focusSilence = prefs.focusSilence,
                focusSilenced = prefs.focusSilenced,
                showReflectionNudge = isSunday() &&
                    prefs.reflectionSeenDay != History.dayKey(History.startOfDay(System.currentTimeMillis())),
                strictUntil = prefs.strictUntil,
                showQuote = prefs.showQuote,
                showNextEvent = prefs.showNextEvent,
                nextEvent = if (prefs.showNextEvent) it.nextEvent else null,
                appLimits = prefs.appLimits,
                pauseStepSeconds = prefs.pauseStepSeconds,
                gestures = prefs.gestures,
                lockWallpaper = prefs.lockWallpaper,
                schedules = prefs.schedules,
                focusSchedule = prefs.focusSchedule,
            )
        }
    }

    override fun onCleared() {
        stopObserving()
    }
}

private fun isSunday() = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

private fun Set<String>.toggle(item: String) = if (item in this) this - item else this + item
