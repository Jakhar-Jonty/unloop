package com.unloop.launcher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import com.unloop.launcher.ui.components.MAX_LEAVES
import com.unloop.launcher.ui.components.LEAF_MINUTES
import com.unloop.launcher.ui.components.FLOWER_DAYS
import kotlinx.coroutines.launch
import com.unloop.launcher.ui.screens.LimitReached
import com.unloop.launcher.ui.screens.LIMIT_WAIT_SECONDS
import com.unloop.launcher.ui.screens.DailyLimitSheet
import com.unloop.launcher.service.UnloopAccessibilityService
import com.unloop.launcher.data.Gate
import androidx.compose.runtime.rememberCoroutineScope
import android.annotation.SuppressLint
import android.Manifest
import android.app.LocaleManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unloop.launcher.data.AppInfo
import com.unloop.launcher.data.FOCUS_UNTIL_STOPPED
import com.unloop.launcher.data.MAX_FAVORITES
import com.unloop.launcher.ui.components.Plant
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.duration
import com.unloop.launcher.ui.limit
import com.unloop.launcher.ui.screens.AllAppsScreen
import com.unloop.launcher.ui.screens.AppActionsSheet
import com.unloop.launcher.ui.screens.EndFocusSheet
import com.unloop.launcher.ui.screens.HomeScreen
import com.unloop.launcher.ui.screens.PauseScreen
import com.unloop.launcher.ui.screens.ReflectionScreen
import com.unloop.launcher.ui.screens.SettingsScreen
import com.unloop.launcher.ui.screens.TaskActions
import com.unloop.launcher.ui.screens.NoteEditor
import com.unloop.launcher.ui.screens.OnboardingActions
import com.unloop.launcher.ui.screens.OnboardingScreen
import com.unloop.launcher.ui.screens.StartFocusSheet
import com.unloop.launcher.ui.screens.UnloopSheet
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.UnloopColors
import com.unloop.launcher.ui.theme.UnloopTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FocusSheet { Start, End }

private sealed interface Screen {
    data object Home : Screen
    data object AllApps : Screen
    data object Settings : Screen
    data object Reflection : Screen
    data class Note(val id: Long) : Screen
    data class Pause(val app: AppInfo, val gate: Gate) : Screen
}

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    /** Bumped on every Home button press so the UI can jump back to the home screen. */
    private var homePresses by mutableIntStateOf(0)

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshSystemState()
        // The system stops showing the role dialog after repeated "no"; fall back to Settings.
        if (!vm.state.value.isDefaultLauncher) openSettings(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private val calendarRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setShowNextEvent(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            UnloopTheme {
                UnloopApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Screen went off (or the phone was locked) with home showing: new quote when it comes back.
        if (!getSystemService(PowerManager::class.java).isInteractive) vm.onLockedFromHome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    @Composable
    private fun UnloopApp() {
        val state by vm.state.collectAsStateWithLifecycle()
        when (state.onboarded) {
            // Preferences not read yet: plain background rather than a flash of the wrong screen.
            null -> {
                Box(Modifier.fillMaxSize().background(UnloopColors.Bg))
                return
            }
            false -> {
                OnboardingScreen(
                    state = state,
                    actions = remember {
                        OnboardingActions(
                            setHome = ::requestDefaultLauncher,
                            grantUsage = ::openUsageAccess,
                            toggleFavorite = vm::toggleFavorite,
                            togglePaused = vm::togglePaused,
                            setLimit = vm::setDailyLimit,
                            openAccessibility = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            finish = vm::finishOnboarding,
                        )
                    },
                )
                return
            }
            true -> Unit
        }
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
        var focusSheet by remember { mutableStateOf<FocusSheet?>(null) }
        var plantSheet by remember { mutableStateOf(false) }
        // Which home page was showing, so coming back from a note lands on Today again.
        var homePage by rememberSaveable { mutableIntStateOf(0) }
        var limitApp by remember { mutableStateOf<AppInfo?>(null) }
        val taskActions = remember {
            TaskActions(
                add = vm::addTask,
                toggle = vm::toggleTask,
                update = vm::updateTask,
                delete = vm::deleteTask,
                clearDone = vm::clearDoneTasks,
                newNote = vm::newNote,
                updateNote = vm::updateNote,
                deleteNote = vm::deleteNote,
                dropEmptyNotes = vm::dropEmptyNotes,
            )
        }
        val scope = rememberCoroutineScope()

        LaunchedEffect(homePresses) {
            screen = Screen.Home
            homePage = 0
            sheetApp = null
            focusSheet = null
            plantSheet = false
            limitApp = null
        }

        LaunchedEffect(Unit) {
            vm.notices.collect { notice ->
                toast(
                    when (notice) {
                        is Notice.StrictBlocked -> getString(R.string.strict_blocked, formatDateTime(notice.until))
                        Notice.HomeFull -> getString(R.string.home_full, MAX_FAVORITES)
                    },
                )
            }
        }

        fun open(app: AppInfo) {
            scope.launch {
                when (val gate = vm.gateFor(app)) {
                    Gate.Open -> {
                        vm.launch(app)
                        screen = Screen.Home
                    }
                    else -> screen = Screen.Pause(app, gate)
                }
            }
        }

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState is Screen.Note) {
                    (slideInVertically(tween(280)) { it / 8 } + fadeIn(tween(220))) togetherWith fadeOut(tween(150))
                } else {
                    (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith fadeOut(tween(150))
                }
            },
            contentKey = { it::class },
            label = "screen",
        ) { current ->
            when (current) {
                Screen.Home -> HomeScreen(
                    state = state,
                    onOpenApp = ::open,
                    onAppActions = { sheetApp = it },
                    onAllApps = { screen = Screen.AllApps },
                    onFocusMode = { focusSheet = FocusSheet.Start },
                    onEndFocus = { focusSheet = FocusSheet.End },
                    onSettings = { screen = Screen.Settings },
                    onWeek = {
                        vm.loadWeek()
                        screen = Screen.Reflection
                    },
                    onPlant = { plantSheet = true },
                    onNotifications = ::expandNotifications,
                    taskActions = taskActions,
                    onOpenNote = { screen = Screen.Note(it) },
                    startPage = homePage,
                    onPageChange = { homePage = it },
                    onLock = {
                        if (UnloopAccessibilityService.instance?.lockScreen() != true) {
                            toast(getString(R.string.lock_needs_accessibility))
                        }
                    },
                    onGrantUsage = ::openUsageAccess,
                    onSetDefault = ::requestDefaultLauncher,
                    onMinuteTick = vm::refreshUsage,
                    homePresses = homePresses,
                )
                Screen.AllApps -> AllAppsScreen(
                    state = state,
                    onOpenApp = ::open,
                    onAppActions = { sheetApp = it },
                    onBack = { screen = Screen.Home },
                    onSettings = { screen = Screen.Settings },
                )
                is Screen.Note -> {
                    val note = state.notes.firstOrNull { it.id == current.id }
                    if (note == null) {
                        LaunchedEffect(Unit) { screen = Screen.Home }
                    } else {
                        NoteEditor(
                            note = note,
                            onChange = { vm.updateNote(note.id, it) },
                            onDelete = {
                                vm.deleteNote(note.id)
                                screen = Screen.Home
                            },
                            onClose = {
                                vm.dropEmptyNotes()
                                screen = Screen.Home
                            },
                        )
                    }
                }
                Screen.Reflection -> ReflectionScreen(
                    state = state,
                    onBack = { screen = Screen.Home },
                    onShare = ::shareText,
                )
                Screen.Settings -> SettingsScreen(
                    state = state,
                    language = appLanguage(),
                    canPickLanguage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    onBack = { screen = Screen.Home },
                    onSetLimit = vm::setDailyLimit,
                    onToggleFocusApp = vm::toggleFocusApp,
                    onTogglePaused = vm::togglePaused,
                    onToggleHidden = vm::toggleHidden,
                    onStartStrict = vm::startStrict,
                    onShowQuote = vm::setShowQuote,
                    onShowNextEvent = { on ->
                        if (on && !state.hasCalendarAccess) {
                            calendarRequest.launch(Manifest.permission.READ_CALENDAR)
                        } else {
                            vm.setShowNextEvent(on)
                        }
                    },
                    onLanguage = ::setLanguage,
                    onPauseEverywhere = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onPauseStep = vm::setPauseStep,
                    onRemoveLimit = { vm.setAppLimit(it, null) },
                    onUpdateSchedule = vm::updateSchedule,
                    onGestures = vm::setGestures,
                    onGrantDnd = { openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    onSetAppLimit = vm::setAppLimit,
                    onLockWallpaper = vm::setLockWallpaper,
                    onLockStyle = vm::setLockStyle,
                    onLockPosition = vm::setLockPosition,
                    onSetupGuide = vm::restartOnboarding,
                    onShowTasks = vm::setShowTasks,
                    onGrantUsage = ::openUsageAccess,
                    onSetDefault = ::requestDefaultLauncher,
                )
                is Screen.Pause -> {
                    val gate = current.gate
                    PauseScreen(
                        appLabel = current.app.label,
                        appKey = current.app.key,
                        usage = (gate as? Gate.Pause)?.usage ?: (gate as? Gate.Limit)?.usage,
                        hasUsageAccess = state.hasUsageAccess,
                        seconds = (gate as? Gate.Pause)?.seconds ?: LIMIT_WAIT_SECONDS,
                        limit = (gate as? Gate.Limit)?.let { LimitReached(it.usedMs, it.limitMinutes, it.canExtend) },
                        onGoHome = {
                            if (gate is Gate.Pause) vm.recordPause(turnedBack = true)
                            screen = Screen.Home
                        },
                        onOpenAnyway = {
                            vm.openAfterGate(current.app, gate)
                            screen = Screen.Home
                        },
                    )
                }
            }
        }

        sheetApp?.let { app ->
            AppActionsSheet(
                app = app,
                isFavorite = state.favorites.any { it.packageName == app.packageName },
                isPaused = app.packageName in state.paused,
                isHidden = app.packageName in state.hidden,
                isFocusApp = app.packageName in state.focusApps,
                limitMinutes = state.appLimits[app.packageName],
                onDismiss = { sheetApp = null },
                onToggleFavorite = {
                    vm.toggleFavorite(app.packageName)
                    sheetApp = null
                },
                onTogglePaused = { vm.togglePaused(app.packageName) },
                onToggleHidden = {
                    vm.toggleHidden(app.packageName)
                    sheetApp = null
                },
                onToggleFocusApp = { vm.toggleFocusApp(app.packageName) },
                onDailyLimit = {
                    limitApp = app
                    sheetApp = null
                },
                onAppInfo = {
                    vm.openAppInfo(app)
                    sheetApp = null
                },
                onUninstall = {
                    openSettings(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)))
                    sheetApp = null
                },
            )
        }

        when (focusSheet) {
            FocusSheet.Start -> StartFocusSheet(
                allowedLabels = state.apps.filter { it.packageName in state.focusApps }.map { it.label },
                silence = state.focusSilence,
                hasDndAccess = state.hasDndAccess,
                onSilenceChange = vm::setFocusSilence,
                onGrantDnd = {
                    vm.setFocusSilence(true)
                    openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                },
                onDismiss = { focusSheet = null },
                onStart = { minutes ->
                    vm.startFocus(minutes)
                    focusSheet = null
                },
            )
            FocusSheet.End -> EndFocusSheet(
                remainingText = if (state.focusUntil == FOCUS_UNTIL_STOPPED) {
                    stringResource(R.string.end_focus_no_end)
                } else {
                    stringResource(R.string.end_focus_left, duration(state.focusUntil - System.currentTimeMillis() + 59_999))
                },
                strictUntilText = if (state.isStrict()) formatDateTime(state.strictUntil) else null,
                onDismiss = { focusSheet = null },
                onEnd = {
                    vm.endFocus()
                    focusSheet = null
                },
            )
            null -> Unit
        }

        limitApp?.let { app ->
            DailyLimitSheet(
                appLabel = app.label,
                current = state.appLimits[app.packageName],
                onDismiss = { limitApp = null },
                onChoose = { minutes ->
                    vm.setAppLimit(app.packageName, minutes)
                    limitApp = null
                },
            )
        }

        if (plantSheet) {
            val streak = state.streak ?: 0
            val leaves = state.leaves
            UnloopSheet(onDismiss = { plantSheet = false }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Plant(leaves = leaves, flowering = state.flowering, modifier = Modifier.size(width = 120.dp, height = 140.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.plant_title), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.plant_body, limit(state.limitMinutes), LEAF_MINUTES, FLOWER_DAYS),
                    color = UnloopColors.TextSoft,
                    fontFamily = Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (leaves == MAX_LEAVES) stringResource(R.string.leaves_all_safe) else stringResource(R.string.leaves_left, leaves, MAX_LEAVES),
                    color = if (leaves == MAX_LEAVES) UnloopColors.Lime else UnloopColors.Orange,
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    pluralStringResource(R.plurals.days_protected, streak, streak),
                    color = UnloopColors.TextSoft,
                    fontFamily = Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(24.dp))
                PopButton(stringResource(R.string.plant_ok), onClick = { plantSheet = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    private fun requestDefaultLauncher() {
        if (vm.state.value.isDefaultLauncher) return
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            openSettings(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun openUsageAccess() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (_: ActivityNotFoundException) {
            openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    /** Per-app language (Android 13+). Null follows the phone. */
    private fun appLanguage(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].language
    }

    private fun setLanguage(tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            openSettings(Intent(Settings.ACTION_LOCALE_SETTINGS))
        }
    }

    /** Pulls down the notification shade. No public API; StatusBarManager has done this for years. */
    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        runCatching {
            val statusBar = getSystemService("statusbar")
            statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
        }
    }

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
    }

    private fun formatDateTime(time: Long): String =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMMjmm"), Locale.getDefault()).format(Date(time))

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.settings_open_failed))
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
