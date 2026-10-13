package com.unloop.launcher.data

/** What should happen when an app is about to open. */
sealed interface Gate {
    data object Open : Gate
    data class Pause(val seconds: Int, val usage: AppUsage?) : Gate
    data class Limit(val usedMs: Long, val limitMinutes: Int, val canExtend: Boolean, val usage: AppUsage?) : Gate
}

const val BASE_PAUSE_SECONDS = 5
private const val MAX_PAUSE_SECONDS = 60
const val EXTRA_MINUTES = 5

/**
 * Shared decisions for the launcher and the accessibility service, plus the short-lived state
 * of what the user has already chosen to open. Lives in the app process; resets if it restarts.
 */
object Gatekeeper {
    /** App the user chose to open after the pause; not paused again until they leave it. */
    @Volatile
    private var passed: String? = null
    private val extraUntil = HashMap<String, Long>()

    fun pass(packageName: String) {
        passed = packageName
    }

    /** Leaving the app (or locking the phone) means the next open gets a pause again. */
    fun clearPass(except: String? = null) {
        if (passed != except) passed = null
    }

    @Synchronized
    fun grantExtra(packageName: String) {
        extraUntil[packageName] = System.currentTimeMillis() + EXTRA_MINUTES * 60_000L
    }

    @Synchronized
    fun extraUntil(packageName: String): Long = extraUntil[packageName] ?: 0L

    fun pauseSeconds(opensToday: Int, stepSeconds: Int): Int =
        (BASE_PAUSE_SECONDS + stepSeconds * opensToday).coerceAtMost(MAX_PAUSE_SECONDS)

    /** Decides how [packageName] opens. Reads today's usage, so call it off the main thread. */
    fun check(prefs: Prefs, usageRepo: UsageRepository, packageName: String): Gate {
        val usage = if (usageRepo.hasAccess()) usageRepo.today().perApp[packageName] else null
        val limit = prefs.appLimits[packageName]
        val now = System.currentTimeMillis()
        // A used-up limit wins over everything, including an earlier "open anyway".
        if (limit != null && usage != null && usage.timeMs >= limit * 60_000L && now >= extraUntil(packageName)) {
            return Gate.Limit(usage.timeMs, limit, canExtend = now >= prefs.strictUntil, usage = usage)
        }
        if (passed == packageName || packageName !in prefs.paused) return Gate.Open
        return Gate.Pause(pauseSeconds(usage?.opens ?: 0, prefs.pauseStepSeconds), usage)
    }

    /** Milliseconds of allowed use left today for a limited app, or null if it has no limit. */
    fun timeLeft(prefs: Prefs, usageRepo: UsageRepository, packageName: String): Long? {
        val limit = prefs.appLimits[packageName] ?: return null
        val used = usageRepo.today().perApp[packageName]?.timeMs ?: 0L
        val now = System.currentTimeMillis()
        val extra = extraUntil(packageName) - now
        return maxOf(limit * 60_000L - used, extra, 0L)
    }
}
