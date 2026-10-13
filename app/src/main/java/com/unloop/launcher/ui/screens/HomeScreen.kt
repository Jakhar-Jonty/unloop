package com.unloop.launcher.ui.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import com.unloop.launcher.ui.theme.tracking
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.unloop.launcher.R
import com.unloop.launcher.UiState
import com.unloop.launcher.data.AppInfo
import com.unloop.launcher.data.DayUsage
import com.unloop.launcher.data.FOCUS_UNTIL_STOPPED
import com.unloop.launcher.data.NextEvent
import com.unloop.launcher.data.Schedule
import com.unloop.launcher.ui.components.GhostButton
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.Plant
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.components.PopCard
import com.unloop.launcher.ui.duration
import com.unloop.launcher.ui.limit
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.Mono
import com.unloop.launcher.ui.theme.UnloopColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: UiState,
    onOpenApp: (AppInfo) -> Unit,
    onAppActions: (AppInfo) -> Unit,
    onAllApps: () -> Unit,
    onFocusMode: () -> Unit,
    onEndFocus: () -> Unit,
    onSettings: () -> Unit,
    onWeek: () -> Unit,
    onPlant: () -> Unit,
    onNotifications: () -> Unit,
    onCamera: () -> Unit,
    onLock: () -> Unit,
    onGrantUsage: () -> Unit,
    onSetDefault: () -> Unit,
    onMinuteTick: () -> Unit,
) {
    // Home is the bottom of the stack: back does nothing (no "leave app" slide).
    BackHandler {}
    val now by rememberMinuteClock()
    LaunchedEffect(now) { onMinuteTick() }
    val context = LocalContext.current
    val view = LocalView.current
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val timeFormat = remember(is24h) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()) }
    var showTop by rememberSaveable { mutableStateOf(false) }
    val focusing = state.isFocusing(now)
    val homeApps = if (focusing) state.apps.filter { it.packageName in state.focusApps } else state.favorites

    Column(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            // Swipes: up = all apps (locked while focusing); with gestures on, down =
            // notifications and right = camera. Double-tap on empty space locks the screen.
            .pointerInput(focusing, state.gestures) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = {
                        dx = 0f
                        dy = 0f
                    },
                    onDragEnd = {
                        val threshold = 150f
                        when {
                            abs(dy) > abs(dx) && dy < -threshold && !focusing -> onAllApps()
                            abs(dy) > abs(dx) && dy > threshold && state.gestures -> onNotifications()
                            abs(dx) > abs(dy) && dx > threshold && state.gestures -> onCamera()
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    dx += amount.x
                    dy += amount.y
                }
            }
            .pointerInput(state.gestures) {
                if (state.gestures) detectTapGestures(onDoubleTap = { onLock() })
            }
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now)).uppercase(),
                    color = UnloopColors.TextSoft,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = tracking(SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now)).uppercase(), 3.sp),
                )
                // Long-press the clock for settings.
                Text(
                    timeFormat.format(Date(now)),
                    fontFamily = Display,
                    fontWeight = FontWeight.ExtraBold,
                    // Sized in dp: the clock is already huge, so it doesn't grow with system text size.
                    fontSize = with(LocalDensity.current) { 92.dp.toSp() },
                    lineHeight = with(LocalDensity.current) { 96.dp.toSp() },
                    letterSpacing = tracking(timeFormat.format(Date(now)), (-4).sp),
                    modifier = Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onSettings()
                        },
                    ),
                )
            }
            state.streak?.let { streak ->
                StreakPlant(streak = streak, overToday = state.overToday, onClick = onPlant)
            }
        }
        state.nextEvent?.let { NextEventLine(it, timeFormat) }
        if (!state.isDefaultLauncher) {
            Label(
                stringResource(R.string.set_default_home),
                color = UnloopColors.Orange,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSetDefault)
                    .padding(vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        if (focusing) {
            FocusCard(focusUntil = state.focusUntil, silenced = state.focusSilenced, schedule = state.focusSchedule, timeFormat = timeFormat)
        } else {
            ScreenTimeCard(
                state = state,
                expanded = showTop,
                onToggle = { showTop = !showTop },
                onGrant = onGrantUsage,
                onWeek = onWeek,
            )
            if (state.showReflectionNudge && state.hasUsageAccess) {
                Spacer(Modifier.height(14.dp))
                Label(
                    stringResource(R.string.week_ready),
                    color = UnloopColors.Lime,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onWeek)
                        .padding(vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        // Fit every home app on screen: on small screens or with large system text, first drop
        // the quote, then shrink the names. Apps must never be cut off.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val quotes = stringArrayResource(R.array.quotes)
            val wantQuote = state.showQuote && !focusing
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            val rowPad = with(density) { 10.dp.roundToPx() }
            fun rowsHeight(size: TextUnit, rows: Int = homeApps.size) =
                rows * (measurer.measure("Ag", TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = size)).size.height + rowPad)
            val quoteHeight = if (wantQuote) {
                measurer.measure(
                    "“${quotes[state.quoteIndex % quotes.size]}”",
                    TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
                    constraints = Constraints(maxWidth = widthPx),
                ).size.height + with(density) { 16.dp.roundToPx() }
            } else 0
            // Tightest layouts last: one column big, one column without the quote, one column
            // smaller, then two columns, and only then a scrolling list.
            val twoColumnRows = (homeApps.size + 1) / 2
            val layout = when {
                rowsHeight(38.sp) + quoteHeight <= heightPx -> HomeLayout(38.sp, wantQuote, columns = 1)
                rowsHeight(38.sp) <= heightPx -> HomeLayout(38.sp, false, columns = 1)
                rowsHeight(30.sp) <= heightPx -> HomeLayout(30.sp, false, columns = 1)
                else -> listOf(30, 26, 22)
                    .firstOrNull { rowsHeight(it.sp, twoColumnRows) <= heightPx }
                    ?.let { HomeLayout(it.sp, false, columns = 2) }
                    ?: HomeLayout(22.sp, false, columns = 2, scrolls = true)
            }

            @Composable
            fun AppName(app: AppInfo, modifier: Modifier = Modifier) {
                Text(
                    app.label,
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = layout.nameSize,
                    letterSpacing = tracking(app.label, (-1).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = modifier
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenApp(app) },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onAppActions(app)
                            },
                        )
                        .padding(vertical = 5.dp),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (layout.scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            ) {
                if (layout.columns == 1) {
                    homeApps.forEach { AppName(it) }
                } else {
                    homeApps.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { AppName(it, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            if (homeApps.isEmpty() && state.apps.isNotEmpty()) {
                Text(
                    stringResource(if (focusing) R.string.focus_empty else R.string.home_empty),
                    color = UnloopColors.Muted,
                    fontFamily = Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
            }
            if (layout.showQuote) {
                Spacer(Modifier.weight(1f))
                Quote(state.quoteIndex)
            }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (focusing) {
            GhostButton(stringResource(R.string.end_focus), onClick = onEndFocus)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PopButton(
                    stringResource(R.string.all_apps),
                    onClick = onAllApps,
                    face = UnloopColors.Paper,
                    edge = UnloopColors.PaperEdge,
                    modifier = Modifier.weight(1f),
                )
                PopButton(
                    stringResource(R.string.focus_mode),
                    onClick = onFocusMode,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** How the home app names are laid out to fit the space left on screen. */
private data class HomeLayout(val nameSize: TextUnit, val showQuote: Boolean, val columns: Int, val scrolls: Boolean = false)

@Composable
private fun StreakPlant(streak: Int, overToday: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Plant(streak = streak, wilting = overToday, modifier = Modifier.size(width = 56.dp, height = 64.dp))
        Spacer(Modifier.height(4.dp))
        Label(
            if (overToday) stringResource(R.string.streak_over_today) else pluralStringResource(R.plurals.streak_days, streak, streak),
            color = if (overToday) UnloopColors.Muted else UnloopColors.Lime,
            size = 10.sp,
        )
    }
}

@Composable
private fun NextEventLine(event: NextEvent, timeFormat: SimpleDateFormat) {
    Row(Modifier.padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(stringResource(R.string.next_event), color = UnloopColors.Lime)
        Spacer(Modifier.width(10.dp))
        Text(
            "${timeFormat.format(Date(event.begin))}  ${event.title.ifBlank { stringResource(R.string.event_untitled) }}",
            color = UnloopColors.TextSoft,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Quote(index: Int) {
    val quotes = stringArrayResource(R.array.quotes)
    Text(
        "“${quotes[index % quotes.size]}”",
        color = UnloopColors.Muted,
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun FocusCard(focusUntil: Long, silenced: Boolean, schedule: String, timeFormat: SimpleDateFormat) {
    PopCard(edge = UnloopColors.Lime) {
        Label(scheduleName(schedule) ?: stringResource(R.string.focus_mode), color = UnloopColors.Lime)
        Spacer(Modifier.height(10.dp))
        if (focusUntil == FOCUS_UNTIL_STOPPED) {
            Text(stringResource(R.string.focus_on), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = tracking(stringResource(R.string.focus_on), (-1.5).sp))
            Text(stringResource(R.string.focus_until_you_end), color = UnloopColors.TextSoft, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        } else {
            // Round up so "0m left" never shows while still focusing.
            val left = focusUntil - System.currentTimeMillis() + 59_999
            Text(
                stringResource(R.string.focus_left, duration(left)),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp,
                letterSpacing = tracking(stringResource(R.string.focus_left, duration(left)), (-1.5).sp),
            )
            Text(
                stringResource(R.string.focus_ends_at, timeFormat.format(Date(focusUntil))),
                color = UnloopColors.TextSoft,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
        if (silenced) {
            Spacer(Modifier.height(10.dp))
            Label(stringResource(R.string.notifications_silenced), color = UnloopColors.Muted)
        }
    }
}

@Composable
private fun ScreenTimeCard(
    state: UiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onGrant: () -> Unit,
    onWeek: () -> Unit,
) {
    val usage: DayUsage? = state.usage
    if (!state.hasUsageAccess || usage == null) {
        PopCard(edge = UnloopColors.LimeEdge) {
            Label(stringResource(R.string.screen_time_today))
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.see_where_time_goes),
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(14.dp))
            PopButton(stringResource(R.string.allow_usage_access), onClick = onGrant, height = 46.dp, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    val limitMs = state.limitMinutes * 60_000L
    val fraction = (usage.totalMs.toFloat() / limitMs).coerceIn(0f, 1f)
    val over = usage.totalMs >= limitMs
    val accent = if (over) UnloopColors.Orange else UnloopColors.Lime

    PopCard(
        edge = accent,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onToggle,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.screen_time_today), modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.unlocks, usage.unlocks, usage.unlocks),
                color = UnloopColors.TextSoft,
                fontFamily = Mono,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Text(
                duration(usage.totalMs),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp,
                maxLines = 1,
                softWrap = false,
                letterSpacing = tracking(duration(usage.totalMs), (-1.5).sp),
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (over) R.string.over_limit else R.string.of_limit, limit(state.limitMinutes)),
                color = UnloopColors.TextSoft,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(UnloopColors.Border),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(20.dp))
                Label(stringResource(R.string.top_apps))
                Spacer(Modifier.height(6.dp))
                val top = usage.top.take(3)
                if (top.isEmpty()) {
                    Text(stringResource(R.string.nothing_over_minute), color = UnloopColors.Muted, fontFamily = Display, fontSize = 15.sp)
                }
                top.forEach { app ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.labelFor(app.packageName),
                            fontFamily = Display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            duration(app.timeMs),
                            color = UnloopColors.TextSoft,
                            fontFamily = Mono,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Label(
                    stringResource(R.string.see_your_week),
                    color = UnloopColors.Lime,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onWeek)
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Current time, updated by the system's minute tick. Timer-based delays use uptime, which
 * stops while the phone sleeps, so they leave the clock stale after waking.
 */
@Composable
private fun rememberMinuteClock(): State<Long> {
    val context = LocalContext.current
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                now.longValue = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LifecycleResumeEffect(Unit) {
        now.longValue = System.currentTimeMillis()
        onPauseOrDispose {}
    }
    return now
}

/** Display name for a schedule id, or null for a Focus session started by hand. */
@Composable
fun scheduleName(id: String): String? = when (id) {
    Schedule.WORK -> stringResource(R.string.schedule_work)
    Schedule.WIND_DOWN -> stringResource(R.string.schedule_wind_down)
    Schedule.SLEEP -> stringResource(R.string.schedule_sleep)
    else -> null
}
