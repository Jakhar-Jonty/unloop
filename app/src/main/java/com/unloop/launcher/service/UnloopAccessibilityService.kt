package com.unloop.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.unloop.launcher.MainActivity
import com.unloop.launcher.PauseActivity
import com.unloop.launcher.data.Gate
import com.unloop.launcher.data.Gatekeeper
import com.unloop.launcher.data.LockWallpaper
import com.unloop.launcher.data.Prefs
import com.unloop.launcher.data.UsageRepository
import java.util.concurrent.Executors

/**
 * "Pause everywhere": watches which app comes to the front, however it was opened (notification,
 * Recents, a link), and shows the pause or daily-limit screen on top when needed. It only reads
 * window changes; it never reads screen content. Also locks the screen for the double-tap gesture.
 */
class UnloopAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private lateinit var usageRepo: UsageRepository

    /** App currently in front, as far as we know. */
    private var foreground: String? = null
    private var homeApps: Set<String> = emptySet()
    private var launchable: Set<String> = emptySet()
    private var keyboards: Set<String> = emptySet()

    private val limitCheck = Runnable { foreground?.let(::evaluate) }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Locking the phone ends the "already passed the pause" allowance.
            Gatekeeper.clearPass()
            foreground = null
            main.removeCallbacks(limitCheck)
            // Fresh numbers on the lock screen the user is about to see.
            worker.execute { LockWallpaper.update(this@UnloopAccessibilityService) }
        }
    }

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshPackages()
    }

    override fun onServiceConnected() {
        instance = this
        prefs = Prefs(this)
        usageRepo = UsageRepository(this)
        refreshPackages()
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(
            this,
            packagesChanged,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        runCatching { unregisterReceiver(screenOff) }
        runCatching { unregisterReceiver(packagesChanged) }
        main.removeCallbacks(limitCheck)
        worker.shutdown()
        super.onDestroy()
    }

    private fun refreshPackages() {
        val pm = packageManager
        homeApps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet()
        launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet()
        keyboards = getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Of Unloop's own windows only the home screen counts; the pause screen (and its inner
        // windows) sits on top of the app and must not look like "went home".
        if (pkg == packageName && event.className?.toString() != MainActivity::class.java.name) return
        // The keyboard and system overlays don't change which app is in front.
        if (pkg in keyboards || pkg == "com.android.systemui") return
        if (pkg in homeApps || pkg == packageName) {
            foreground = pkg
            Gatekeeper.clearPass()
            main.removeCallbacks(limitCheck)
            return
        }
        // Only real apps count; permission dialogs, share sheets etc. are ignored.
        if (pkg !in launchable || pkg == foreground) return
        foreground = pkg
        Gatekeeper.clearPass(except = pkg)
        main.removeCallbacks(limitCheck)
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        worker.execute {
            val gate = Gatekeeper.check(prefs, usageRepo, pkg)
            val left = if (gate == Gate.Open) Gatekeeper.timeLeft(prefs, usageRepo, pkg) else null
            main.post {
                if (foreground != pkg) return@post
                when (gate) {
                    Gate.Open -> left?.let { main.postDelayed(limitCheck, it + 1_000) }
                    is Gate.Pause, is Gate.Limit -> startActivity(PauseActivity.intent(this, pkg))
                }
            }
        }
    }

    /** Re-checks the app in front, e.g. after "5 more minutes" was granted. */
    fun recheckLater(pkg: String) {
        main.removeCallbacks(limitCheck)
        if (foreground == pkg) evaluate(pkg)
    }

    override fun onInterrupt() = Unit

    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    companion object {
        /** Set while the service is running; null when the user hasn't turned it on. */
        @Volatile
        var instance: UnloopAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, UnloopAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
