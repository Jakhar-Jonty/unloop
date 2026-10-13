package com.unloop.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.unloop.launcher.data.Gate
import com.unloop.launcher.data.Gatekeeper
import com.unloop.launcher.data.History
import com.unloop.launcher.data.Prefs
import com.unloop.launcher.data.UsageRepository
import com.unloop.launcher.service.UnloopAccessibilityService
import com.unloop.launcher.ui.screens.LIMIT_WAIT_SECONDS
import com.unloop.launcher.ui.screens.LimitReached
import com.unloop.launcher.ui.screens.PauseScreen
import com.unloop.launcher.ui.theme.UnloopColors
import com.unloop.launcher.ui.theme.UnloopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pause / daily-limit screen shown on top of an app that was opened outside Unloop
 * (from a notification, Recents or a link). Started by [UnloopAccessibilityService].
 */
class PauseActivity : ComponentActivity() {
    private var target by mutableStateOf<Target?>(null)

    private class Target(val packageName: String, val label: String, val gate: Gate, val hasUsageAccess: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        load(intent)
        setContent {
            UnloopTheme {
                // Plain background while today's numbers load, so the app underneath never shows.
                Box(Modifier.fillMaxSize().background(UnloopColors.Bg)) {
                    target?.let { t -> Content(t) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        load(intent)
    }

    @Composable
    private fun Content(t: Target) {
        val gate = t.gate
        PauseScreen(
            appLabel = t.label,
            appKey = t.packageName + gate.hashCode(),
            usage = when (gate) {
                is Gate.Pause -> gate.usage
                is Gate.Limit -> gate.usage
                Gate.Open -> null
            },
            hasUsageAccess = t.hasUsageAccess,
            seconds = (gate as? Gate.Pause)?.seconds ?: LIMIT_WAIT_SECONDS,
            limit = (gate as? Gate.Limit)?.let { LimitReached(it.usedMs, it.limitMinutes, it.canExtend) },
            onGoHome = {
                if (gate is Gate.Pause) History(this).recordPause(turnedBack = true)
                goHome()
            },
            onOpenAnyway = {
                if (gate is Gate.Limit) {
                    Gatekeeper.grantExtra(t.packageName)
                } else {
                    History(this).recordPause(turnedBack = false)
                }
                Gatekeeper.pass(t.packageName)
                finish()
                UnloopAccessibilityService.instance?.recheckLater(t.packageName)
            },
        )
    }

    private fun load(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        lifecycleScope.launch {
            val prefs = Prefs(this@PauseActivity)
            val usageRepo = UsageRepository(this@PauseActivity)
            val (gate, label, access) = withContext(Dispatchers.Default) {
                Triple(Gatekeeper.check(prefs, usageRepo, pkg), usageRepo.labelOf(pkg), usageRepo.hasAccess())
            }
            if (gate == Gate.Open) finish() else target = Target(pkg, label, gate, access)
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, PauseActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
