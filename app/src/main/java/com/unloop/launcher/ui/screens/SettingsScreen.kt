package com.unloop.launcher.ui.screens

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.unloop.launcher.data.LockWallpaper
import com.unloop.launcher.data.LOCK_TOP
import com.unloop.launcher.data.LOCK_MIDDLE
import com.unloop.launcher.data.LOCK_FULL
import com.unloop.launcher.data.LOCK_COMPACT
import com.unloop.launcher.data.LOCK_BOTTOM
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.BuildConfig
import com.unloop.launcher.R
import com.unloop.launcher.UiState
import com.unloop.launcher.data.Schedule
import com.unloop.launcher.ui.components.Chip
import com.unloop.launcher.ui.components.GhostButton
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.components.PopCard
import com.unloop.launcher.ui.components.UnloopSwitch
import com.unloop.launcher.ui.formatLimit
import com.unloop.launcher.ui.limit
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.Mono
import com.unloop.launcher.ui.theme.UnloopColors
import com.unloop.launcher.ui.theme.tracking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val LIMIT_STEP = 15
private val strictDurations = listOf(1, 3, 7)
private val pauseSteps = listOf(0, 2, 5)

/** Language choices: null = follow the phone. Labels are each language's own name. */
private val languages: List<Pair<String?, String?>> = listOf(null to null, "en" to "English", "hi" to "हिन्दी")

/** Which list the app picker adds to. */
private enum class Picker(@StringRes val title: Int) {
    Paused(R.string.paused_apps),
    Focus(R.string.allowed_in_focus),
    Hidden(R.string.hidden_apps),
    Limits(R.string.app_limits),
}

private enum class Page(@StringRes val title: Int) {
    ScreenTime(R.string.cat_screen_time),
    Pause(R.string.cat_pause),
    Focus(R.string.cat_focus),
    Strict(R.string.strict_mode),
    Home(R.string.cat_home),
    Language(R.string.language),
    Permissions(R.string.permissions),
}

/**
 * Settings: an overview of categories, each with a one-line summary of its current state,
 * opening into a page of its own. Missing permissions are surfaced on the overview.
 */
@Composable
fun SettingsScreen(
    state: UiState,
    language: String?,
    canPickLanguage: Boolean,
    onBack: () -> Unit,
    onSetLimit: (Int) -> Unit,
    onToggleFocusApp: (String) -> Unit,
    onTogglePaused: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onStartStrict: (days: Int) -> Unit,
    onShowQuote: (Boolean) -> Unit,
    onShowNextEvent: (Boolean) -> Unit,
    onLanguage: (String?) -> Unit,
    onGrantUsage: () -> Unit,
    onSetDefault: () -> Unit,
    onPauseEverywhere: () -> Unit,
    onPauseStep: (Int) -> Unit,
    onRemoveLimit: (String) -> Unit,
    onUpdateSchedule: (Schedule) -> Unit,
    onGestures: (Boolean) -> Unit,
    onGrantDnd: () -> Unit,
    onSetAppLimit: (String, Int?) -> Unit,
    onLockWallpaper: (Boolean) -> Unit,
    onLockStyle: (String) -> Unit,
    onLockPosition: (String) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var limitFor by remember { mutableStateOf<String?>(null) }
    BackHandler { if (page != null) page = null else onBack() }

    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "settings-page",
    ) { current ->
        val byLabel = compareBy<String> { state.labelFor(it).lowercase() }
        when (current) {
            null -> Overview(state, language, onOpen = { page = it })
            Page.ScreenTime -> PageScaffold(Page.ScreenTime, onBack = { page = null }) {
                item { LimitCard(state, onSetLimit) }
                appList(
                    title = R.string.app_limits,
                    hint = R.string.app_limits_hint,
                    packages = state.appLimits.keys.sortedWith(byLabel),
                    labelFor = state::labelFor,
                    trailing = { pkg -> state.appLimits[pkg]?.let { formatLimit(LocalContext.current.resources, it) } },
                    onTap = onRemoveLimit,
                    onAdd = { picker = Picker.Limits },
                )
            }
            Page.Pause -> PageScaffold(Page.Pause, onBack = { page = null }) {
                item {
                    Group {
                        SwitchRow(
                            stringResource(R.string.pause_everywhere),
                            hint = stringResource(R.string.pause_everywhere_hint),
                            on = state.pauseEverywhere,
                            onClick = onPauseEverywhere,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Label(stringResource(R.string.pause_growth))
                    Spacer(Modifier.height(4.dp))
                    Hint(stringResource(R.string.pause_growth_hint))
                    Spacer(Modifier.height(12.dp))
                    ChipRow {
                        pauseSteps.forEach { step ->
                            Chip(
                                if (step == 0) stringResource(R.string.state_off) else stringResource(R.string.growth_step, step),
                                selected = state.pauseStepSeconds == step,
                                onClick = { onPauseStep(step) },
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
                appList(
                    title = R.string.paused_apps,
                    hint = R.string.paused_apps_hint,
                    packages = state.paused.sortedWith(byLabel),
                    labelFor = state::labelFor,
                    onTap = onTogglePaused,
                    onAdd = { picker = Picker.Paused },
                )
            }
            Page.Focus -> FocusPage(state, byLabel, onToggleFocusApp, onUpdateSchedule, onAdd = { picker = Picker.Focus }, onBack = { page = null })
            Page.Strict -> StrictPage(state, onStartStrict, onBack = { page = null })
            Page.Home -> PageScaffold(Page.Home, onBack = { page = null }) {
                item {
                    Group {
                        SwitchRow(stringResource(R.string.daily_quote), on = state.showQuote, onClick = { onShowQuote(!state.showQuote) })
                        Divider()
                        SwitchRow(
                            stringResource(R.string.next_event_setting),
                            on = state.showNextEvent && state.hasCalendarAccess,
                            onClick = { onShowNextEvent(!(state.showNextEvent && state.hasCalendarAccess)) },
                        )
                        Divider()
                        SwitchRow(
                            stringResource(R.string.lock_wallpaper),
                            hint = stringResource(R.string.lock_wallpaper_hint),
                            on = state.lockWallpaper,
                            onClick = { onLockWallpaper(!state.lockWallpaper) },
                        )
                        if (state.lockWallpaper) LockWallpaperOptions(state, onLockStyle, onLockPosition)
                        Divider()
                        SwitchRow(
                            stringResource(R.string.gestures),
                            hint = stringResource(R.string.gestures_hint),
                            on = state.gestures,
                            onClick = { onGestures(!state.gestures) },
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
                appList(
                    title = R.string.hidden_apps,
                    hint = R.string.hidden_hint,
                    packages = state.hidden.sortedWith(byLabel),
                    labelFor = state::labelFor,
                    onTap = onToggleHidden,
                    onAdd = { picker = Picker.Hidden },
                )
            }
            Page.Language -> PageScaffold(Page.Language, onBack = { page = null }) {
                item {
                    if (canPickLanguage) {
                        Group {
                            languages.forEachIndexed { i, (tag, name) ->
                                if (i > 0) Divider()
                                ChoiceRow(name ?: stringResource(R.string.language_system), selected = language == tag) { onLanguage(tag) }
                            }
                        }
                    } else {
                        Group { NavRow(stringResource(R.string.language_system), value = null, onClick = { onLanguage(null) }) }
                    }
                }
            }
            Page.Permissions -> PageScaffold(Page.Permissions, onBack = { page = null }) {
                item {
                    Group {
                        PermissionRow(stringResource(R.string.default_home), ok = state.isDefaultLauncher, onFix = onSetDefault)
                        Divider()
                        PermissionRow(stringResource(R.string.usage_access), ok = state.hasUsageAccess, onFix = onGrantUsage)
                        Divider()
                        PermissionRow(stringResource(R.string.pause_everywhere), ok = state.pauseEverywhere, onFix = onPauseEverywhere)
                        Divider()
                        PermissionRow(stringResource(R.string.dnd_access), ok = state.hasDndAccess, onFix = onGrantDnd, optional = true)
                        Divider()
                        PermissionRow(
                            stringResource(R.string.calendar_access),
                            ok = state.hasCalendarAccess,
                            onFix = { onShowNextEvent(true) },
                            optional = true,
                        )
                    }
                }
            }
        }
    }

    picker?.let { target ->
        val res = LocalContext.current.resources
        AppPickerSheet(
            title = stringResource(target.title),
            apps = state.apps,
            selected = when (target) {
                Picker.Paused -> state.paused
                Picker.Focus -> state.focusApps
                Picker.Hidden -> state.hidden
                Picker.Limits -> state.appLimits.keys
            },
            trailing = { pkg -> state.appLimits[pkg]?.takeIf { target == Picker.Limits }?.let { formatLimit(res, it) } },
            onToggle = { app ->
                when (target) {
                    Picker.Paused -> onTogglePaused(app.packageName)
                    Picker.Focus -> onToggleFocusApp(app.packageName)
                    Picker.Hidden -> onToggleHidden(app.packageName)
                    Picker.Limits -> limitFor = app.packageName
                }
            },
            onDismiss = { picker = null },
        )
    }
    limitFor?.let { pkg ->
        DailyLimitSheet(
            appLabel = state.labelFor(pkg),
            current = state.appLimits[pkg],
            onDismiss = { limitFor = null },
            onChoose = { minutes ->
                onSetAppLimit(pkg, minutes)
                limitFor = null
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Overview

/** Required permissions that are missing. Optional ones (DND, calendar) don't count. */
private fun missingPermissions(state: UiState): Int =
    listOf(state.isDefaultLauncher, state.hasUsageAccess, state.pauseEverywhere || state.paused.isEmpty()).count { !it }

@Composable
private fun Overview(state: UiState, language: String?, onOpen: (Page) -> Unit) {
    val dateTime = rememberDateTime()
    val attention = missingPermissions(state)
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 36.dp, bottom = 40.dp),
    ) {
        item {
            Text(
                stringResource(R.string.settings),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 44.sp,
                letterSpacing = tracking(stringResource(R.string.settings), (-1.5).sp),
            )
            Spacer(Modifier.height(24.dp))

            // At a glance: today's limit, strict mode, and anything that needs fixing.
            PopCard(edge = if (attention > 0) UnloopColors.Orange else UnloopColors.LimeEdge) {
                Label(stringResource(R.string.today_limit_status, limit(state.limitMinutes)))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (attention > 0) pluralStringResource(R.plurals.perm_attention, attention, attention)
                    else stringResource(R.string.perm_all_set),
                    color = if (attention > 0) UnloopColors.Orange else UnloopColors.Text,
                    fontFamily = Display,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                )
                if (state.isStrict()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${stringResource(R.string.strict_short_on)} · ${dateTime.format(Date(state.strictUntil))}",
                        color = UnloopColors.TextSoft,
                        fontFamily = Display,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            Group {
                NavRow(
                    stringResource(R.string.cat_screen_time),
                    value = listOf(
                        stringResource(R.string.sum_per_day, limit(state.limitMinutes)),
                        if (state.appLimits.isEmpty()) stringResource(R.string.sum_no_app_limits)
                        else pluralStringResource(R.plurals.sum_app_limits, state.appLimits.size, state.appLimits.size),
                    ).joinToString(" · "),
                    onClick = { onOpen(Page.ScreenTime) },
                )
                Divider()
                NavRow(
                    stringResource(R.string.cat_pause),
                    value = listOf(
                        pluralStringResource(R.plurals.sum_paused, state.paused.size, state.paused.size),
                        stringResource(if (state.pauseEverywhere) R.string.sum_everywhere else R.string.sum_from_unloop),
                    ).joinToString(" · "),
                    warn = !state.pauseEverywhere && state.paused.isNotEmpty(),
                    onClick = { onOpen(Page.Pause) },
                )
                Divider()
                val activeSchedules = state.schedules.filter { it.enabled }.map { scheduleName(it.id).orEmpty() }
                NavRow(
                    stringResource(R.string.cat_focus),
                    value = listOf(
                        pluralStringResource(R.plurals.sum_focus_apps, state.focusApps.size, state.focusApps.size),
                        activeSchedules.joinToString(", ").ifEmpty { stringResource(R.string.sum_no_schedules) },
                    ).joinToString(" · "),
                    onClick = { onOpen(Page.Focus) },
                )
                Divider()
                NavRow(
                    stringResource(R.string.strict_mode),
                    value = if (state.isStrict()) stringResource(R.string.strict_on_until, dateTime.format(Date(state.strictUntil)))
                    else stringResource(R.string.sum_off),
                    accent = state.isStrict(),
                    onClick = { onOpen(Page.Strict) },
                )
            }
            Spacer(Modifier.height(16.dp))
            Group {
                NavRow(
                    stringResource(R.string.cat_home),
                    value = "${stringResource(R.string.sum_quote)} ${onOff(state.showQuote)} · ${stringResource(R.string.sum_gestures)} ${onOff(state.gestures)}",
                    onClick = { onOpen(Page.Home) },
                )
                Divider()
                NavRow(
                    stringResource(R.string.language),
                    value = languages.firstOrNull { it.first == language }?.second ?: stringResource(R.string.language_system),
                    onClick = { onOpen(Page.Language) },
                )
                Divider()
                NavRow(
                    stringResource(R.string.permissions),
                    value = if (attention > 0) pluralStringResource(R.plurals.perm_attention, attention, attention)
                    else stringResource(R.string.perm_all_set),
                    warn = attention > 0,
                    onClick = { onOpen(Page.Permissions) },
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.version, BuildConfig.VERSION_NAME),
                color = UnloopColors.Faint,
                fontFamily = Mono,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun onOff(on: Boolean): String = stringResource(if (on) R.string.state_on else R.string.state_off).lowercase()

// ---------------------------------------------------------------------------------------------
// Pages

@Composable
private fun PageScaffold(page: Page, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 40.dp),
    ) {
        item {
            Label(
                "←  " + stringResource(R.string.settings),
                color = UnloopColors.Lime,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(vertical = 10.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(page.title),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                letterSpacing = tracking(stringResource(page.title), (-1.5).sp),
            )
            Spacer(Modifier.height(24.dp))
        }
        content()
    }
}

@Composable
private fun LimitCard(state: UiState, onSetLimit: (Int) -> Unit) {
    PopCard(edge = UnloopColors.LimeEdge) {
        Label(stringResource(R.string.daily_limit))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                limit(state.limitMinutes),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp,
                modifier = Modifier.weight(1f),
            )
            PopButton(
                "−",
                onClick = { onSetLimit(state.limitMinutes - LIMIT_STEP) },
                face = UnloopColors.Paper,
                edge = UnloopColors.PaperEdge,
                height = 48.dp,
                modifier = Modifier.width(64.dp),
            )
            Spacer(Modifier.width(12.dp))
            PopButton(
                "+",
                onClick = { onSetLimit(state.limitMinutes + LIMIT_STEP) },
                height = 48.dp,
                modifier = Modifier.width(64.dp),
            )
        }
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun FocusPage(
    state: UiState,
    byLabel: Comparator<String>,
    onToggleFocusApp: (String) -> Unit,
    onUpdateSchedule: (Schedule) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<Schedule?>(null) }
    PageScaffold(Page.Focus, onBack) {
        item {
            Label(stringResource(R.string.schedules))
            Spacer(Modifier.height(4.dp))
            Hint(stringResource(R.string.schedules_hint))
            Spacer(Modifier.height(12.dp))
            Group {
                state.schedules.forEachIndexed { i, schedule ->
                    if (i > 0) Divider()
                    ScheduleRow(
                        schedule = schedule,
                        onToggle = { onUpdateSchedule(schedule.copy(enabled = !schedule.enabled)) },
                        onEdit = { editing = schedule },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        appList(
            title = R.string.allowed_in_focus,
            hint = R.string.focus_apps_hint,
            packages = state.focusApps.sortedWith(byLabel),
            labelFor = state::labelFor,
            onTap = onToggleFocusApp,
            onAdd = onAdd,
        )
    }
    editing?.let { schedule ->
        ScheduleSheet(
            schedule = schedule,
            onDismiss = { editing = null },
            onSave = {
                onUpdateSchedule(it)
                editing = null
            },
        )
    }
}

@Composable
private fun StrictPage(state: UiState, onStartStrict: (Int) -> Unit, onBack: () -> Unit) {
    var strictDays by rememberSaveable { mutableIntStateOf(1) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val dateTime = rememberDateTime()
    PageScaffold(Page.Strict, onBack) {
        item {
            PopCard(edge = if (state.isStrict()) UnloopColors.Orange else UnloopColors.SurfaceEdge) {
                if (state.isStrict()) {
                    Label(stringResource(R.string.strict_short_on), color = UnloopColors.Orange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.strict_on_until, dateTime.format(Date(state.strictUntil))),
                        fontFamily = Display,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Hint(stringResource(R.string.strict_body), color = UnloopColors.TextSoft, size = 15)
                if (!state.isStrict()) {
                    Spacer(Modifier.height(16.dp))
                    ChipRow {
                        strictDurations.forEach { d ->
                            Chip(pluralStringResource(R.plurals.streak_days, d, d), selected = strictDays == d, onClick = { strictDays = d })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    PopButton(
                        stringResource(R.string.strict_lock),
                        onClick = { confirm = true },
                        face = UnloopColors.Orange,
                        edge = UnloopColors.PaperEdge,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (confirm) {
        val days = strictDays
        val until = remember(days) { System.currentTimeMillis() + days * 24 * 60 * 60_000L }
        UnloopSheet(onDismiss = { confirm = false }) {
            Text(
                stringResource(R.string.strict_confirm_title, pluralStringResource(R.plurals.streak_days, days, days)),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                lineHeight = 34.sp,
            )
            Spacer(Modifier.height(10.dp))
            Hint(stringResource(R.string.strict_confirm_body, dateTime.format(Date(until))), color = UnloopColors.TextSoft, size = 16)
            Spacer(Modifier.height(24.dp))
            PopButton(
                stringResource(R.string.strict_confirm),
                onClick = {
                    onStartStrict(days)
                    confirm = false
                },
                face = UnloopColors.Orange,
                edge = UnloopColors.PaperEdge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            GhostButton(stringResource(R.string.cancel), onClick = { confirm = false })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks

/** Rounded surface that groups related rows. */
@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(UnloopColors.Surface)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(UnloopColors.Divider))
}

@Composable
private fun Hint(text: String, color: Color = UnloopColors.Muted, size: Int = 14) {
    Text(text, color = color, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = (size + 6).sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

/** Category row: title, current value underneath, chevron. */
@Composable
private fun NavRow(title: String, value: String?, onClick: () -> Unit, warn: Boolean = false, accent: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (value != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    color = when {
                        warn -> UnloopColors.Orange
                        accent -> UnloopColors.Lime
                        else -> UnloopColors.Muted
                    },
                    fontFamily = Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text("›", color = UnloopColors.Muted, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp)
    }
}

@Composable
private fun SwitchRow(title: String, on: Boolean, onClick: () -> Unit, hint: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Hint(hint)
            }
        }
        Spacer(Modifier.width(16.dp))
        UnloopSwitch(on)
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) UnloopColors.Lime else UnloopColors.Border),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(UnloopColors.Ink))
        }
    }
}

@Composable
private fun PermissionRow(title: String, ok: Boolean, onFix: () -> Unit, optional: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !ok, onClick = onFix)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (optional) {
                Text(stringResource(R.string.optional), color = UnloopColors.Faint, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        Label(
            if (ok) stringResource(R.string.state_on) else stringResource(R.string.turn_on),
            color = when {
                ok -> UnloopColors.Lime
                optional -> UnloopColors.TextSoft
                else -> UnloopColors.Orange
            },
            size = 12.sp,
        )
    }
}

/** A titled list of apps inside a group; tapping a row removes it from the list. */
private fun LazyListScope.appList(
    @StringRes title: Int,
    @StringRes hint: Int,
    packages: List<String>,
    labelFor: (String) -> String,
    onTap: (String) -> Unit,
    onAdd: () -> Unit,
    trailing: @Composable (String) -> String? = { null },
) {
    item(key = "title_$title") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(title), modifier = Modifier.weight(1f))
            Text("${packages.size}", color = UnloopColors.Muted, fontFamily = Mono, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Hint(stringResource(hint))
        Spacer(Modifier.height(12.dp))
        Group {
            packages.forEachIndexed { i, pkg ->
                if (i > 0) Divider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTap(pkg) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        labelFor(pkg),
                        fontFamily = Display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    trailing(pkg)?.let {
                        Text(it, color = UnloopColors.Orange, fontFamily = Mono, fontSize = 13.sp)
                        Spacer(Modifier.width(14.dp))
                    }
                    Text("✕", color = UnloopColors.Muted, fontFamily = Mono, fontSize = 14.sp)
                }
            }
            if (packages.isNotEmpty()) Divider()
            Text(
                stringResource(R.string.add_app),
                color = UnloopColors.Lime,
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(vertical = 15.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ScheduleRow(schedule: Schedule, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(scheduleName(schedule.id).orEmpty(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(scheduleSummary(schedule), color = UnloopColors.Muted, fontFamily = Mono, fontSize = 12.sp)
        }
        Spacer(Modifier.width(16.dp))
        // The switch has its own tap target so the row itself can open the editor.
        Box(Modifier.clip(CircleShape).clickable(onClick = onToggle).padding(4.dp)) {
            UnloopSwitch(schedule.enabled)
        }
    }
}

/** "M T W T F · 10:00–18:00", or "Every day · 23:00–07:00". */
@Composable
private fun scheduleSummary(schedule: Schedule): String {
    val names = stringArrayResource(R.array.weekday_short)
    val days = if (schedule.days == Schedule.EVERY_DAY) stringResource(R.string.every_day)
    else (0..6).filter { schedule.runsOn(it) }.joinToString(" ") { names[it] }
    return "$days  ·  ${clock(schedule.start)}–${clock(schedule.end)}"
}

@Composable
private fun clock(minuteOfDay: Int): String {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(cal.time)
}

@Composable
private fun rememberDateTime(): SimpleDateFormat = remember {
    SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMMjmm"), Locale.getDefault())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSheet(schedule: Schedule, onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val from = rememberTimePickerState(schedule.start / 60, schedule.start % 60, is24)
    val to = rememberTimePickerState(schedule.end / 60, schedule.end % 60, is24)
    var days by remember { mutableIntStateOf(schedule.days) }
    val names = stringArrayResource(R.array.weekday_short)
    val colors = TimePickerDefaults.colors(
        timeSelectorSelectedContainerColor = UnloopColors.Lime,
        timeSelectorSelectedContentColor = UnloopColors.Ink,
        timeSelectorUnselectedContainerColor = UnloopColors.Bg,
        timeSelectorUnselectedContentColor = UnloopColors.Text,
        periodSelectorSelectedContainerColor = UnloopColors.Lime,
        periodSelectorSelectedContentColor = UnloopColors.Ink,
        periodSelectorUnselectedContentColor = UnloopColors.TextSoft,
        periodSelectorBorderColor = UnloopColors.Border,
    )

    UnloopSheet(onDismiss) {
        Text(scheduleName(schedule.id).orEmpty(), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
        Spacer(Modifier.height(16.dp))
        Label(stringResource(R.string.schedule_from))
        Spacer(Modifier.height(8.dp))
        TimeInput(state = from, colors = colors)
        Label(stringResource(R.string.schedule_to))
        Spacer(Modifier.height(8.dp))
        TimeInput(state = to, colors = colors)
        Label(stringResource(R.string.schedule_days))
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..6).forEach { d ->
                Chip(names[d], selected = days and (1 shl d) != 0, onClick = { days = days xor (1 shl d) })
            }
        }
        Spacer(Modifier.height(24.dp))
        PopButton(
            stringResource(R.string.save),
            onClick = {
                onSave(
                    schedule.copy(
                        enabled = true,
                        days = if (days == 0) schedule.days else days,
                        start = from.hour * 60 + from.minute,
                        end = to.hour * 60 + to.minute,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Layout and position for the lock-screen wallpaper, with a live preview of the result. */
@Composable
private fun LockWallpaperOptions(state: UiState, onStyle: (String) -> Unit, onPosition: (String) -> Unit) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, state.lockStyle, state.lockPosition, state.limitMinutes) {
        value = withContext(Dispatchers.Default) {
            LockWallpaper.preview(context, state.lockStyle, state.lockPosition, width = 360).asImageBitmap()
        }
    }
    Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Label(stringResource(R.string.lock_layout))
            Spacer(Modifier.height(8.dp))
            ChipRow {
                Chip(stringResource(R.string.lock_compact), selected = state.lockStyle == LOCK_COMPACT, onClick = { onStyle(LOCK_COMPACT) })
                Chip(stringResource(R.string.lock_full), selected = state.lockStyle == LOCK_FULL, onClick = { onStyle(LOCK_FULL) })
            }
            Spacer(Modifier.height(16.dp))
            Label(stringResource(R.string.lock_position))
            Spacer(Modifier.height(8.dp))
            ChipRow {
                listOf(LOCK_TOP to R.string.lock_top, LOCK_MIDDLE to R.string.lock_middle, LOCK_BOTTOM to R.string.lock_bottom).forEach { (pos, name) ->
                    Chip(stringResource(name), selected = state.lockPosition == pos, onClick = { onPosition(pos) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Hint(stringResource(R.string.lock_position_hint))
        }
        Spacer(Modifier.width(14.dp))
        // Phone-shaped preview of exactly what will be set.
        Box(
            Modifier
                .width(96.dp)
                .aspectRatio(9f / 20f)
                .clip(RoundedCornerShape(14.dp))
                .background(UnloopColors.Bg)
                .border(1.dp, UnloopColors.Border, RoundedCornerShape(14.dp)),
        ) {
            preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
    }
}
