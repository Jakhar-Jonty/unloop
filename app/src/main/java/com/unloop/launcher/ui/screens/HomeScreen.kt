package com.unloop.launcher.ui.screens

import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import com.unloop.launcher.data.Task
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateColorAsState
import com.unloop.launcher.data.Note
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import com.unloop.launcher.ui.components.nextLeafInMs
import com.unloop.launcher.ui.components.MAX_LEAVES
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
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
    onLock: () -> Unit,
    taskActions: TaskActions,
    onOpenNote: (Long) -> Unit,
    onGrantUsage: () -> Unit,
    onSetDefault: () -> Unit,
    onMinuteTick: () -> Unit,
    homePresses: Int,
    /** Page to open on (1 = Today), so returning from a note lands back on Today. */
    startPage: Int = 0,
    onPageChange: (Int) -> Unit = {},
) {
    // Home is the bottom of the stack: back does nothing (no "leave app" slide).
    BackHandler {}
    val now by rememberMinuteClock()
    LaunchedEffect(now) { onMinuteTick() }
    val pager = rememberPagerState(initialPage = startPage) { 2 }
    LaunchedEffect(pager.currentPage) { onPageChange(pager.currentPage) }
    val scope = rememberCoroutineScope()
    // The caller owns which page is showing (Home press resets it to 0); follow it.
    LaunchedEffect(startPage) { if (pager.currentPage != startPage) pager.scrollToPage(startPage) }

    Box(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                MainPage(
                    state = state,
                    now = now,
                    onOpenApp = onOpenApp,
                    onAppActions = onAppActions,
                    onAllApps = onAllApps,
                    onFocusMode = onFocusMode,
                    onEndFocus = onEndFocus,
                    onSettings = onSettings,
                    onNotifications = onNotifications,
                    onLock = onLock,
                    onSetDefault = onSetDefault,
                    onPlant = onPlant,
                    onShowToday = { scope.launch { pager.animateScrollToPage(1) } },
                    onGrantUsage = onGrantUsage,
                )
            } else {
                TodayPage(
                    state = state,
                    onWeek = onWeek,
                    onPlant = onPlant,
                    actions = taskActions,
                    onOpenNote = onOpenNote,
                    onGrantUsage = onGrantUsage,
                )
            }
        }
        // Soft fade so scrolling content never collides with the page dots.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, UnloopColors.Bg))),
        )
        PageDots(
            current = pager.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 6.dp),
        )
    }
}

/** Page 1: clock, home apps and the two main buttons. Nothing else. */
@Composable
private fun MainPage(
    state: UiState,
    now: Long,
    onOpenApp: (AppInfo) -> Unit,
    onAppActions: (AppInfo) -> Unit,
    onAllApps: () -> Unit,
    onFocusMode: () -> Unit,
    onEndFocus: () -> Unit,
    onSettings: () -> Unit,
    onNotifications: () -> Unit,
    onLock: () -> Unit,
    onSetDefault: () -> Unit,
    onPlant: () -> Unit,
    onShowToday: () -> Unit,
    onGrantUsage: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val timeFormat = remember(is24h) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()) }
    val focusing = state.isFocusing(now)
    val homeApps = if (focusing) state.apps.filter { it.packageName in state.focusApps } else state.favorites

    Column(
        Modifier
            .fillMaxSize()
            // Vertical swipes only (horizontal ones turn the page): up = all apps (not while
            // focusing), down = notifications. Double-tap on empty space locks the screen.
            .pointerInput(focusing, state.gestures) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = {
                        when {
                            dy < -150f && !focusing -> onAllApps()
                            dy > 150f && state.gestures -> onNotifications()
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    dy += amount
                }
            }
            .pointerInput(state.gestures) {
                if (state.gestures) detectTapGestures(onDoubleTap = { onLock() })
            }
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now)).uppercase()
        Text(
            date,
            color = UnloopColors.TextSoft,
            fontFamily = Mono,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = tracking(date, 3.sp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Long-press the clock for settings.
            Text(
                timeFormat.format(Date(now)),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                // Sized in dp: the clock is already huge, so it doesn't grow with system text size.
                fontSize = with(LocalDensity.current) { 92.dp.toSp() },
                lineHeight = with(LocalDensity.current) { 96.dp.toSp() },
                letterSpacing = tracking(timeFormat.format(Date(now)), (-4).sp),
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onSettings()
                        },
                    ),
            )
            if (state.hasUsageAccess && state.usage != null) {
                Plant(
                    leaves = state.leaves,
                    flowering = state.flowering,
                    modifier = Modifier
                        .size(width = 64.dp, height = 76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onPlant),
                )
            }
        }
        TimeStrip(state, onClick = onShowToday, onGrant = onGrantUsage)
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
        if (focusing) {
            Spacer(Modifier.height(24.dp))
            FocusCard(focusUntil = state.focusUntil, silenced = state.focusSilenced, schedule = state.focusSchedule, timeFormat = timeFormat)
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
        // Room for the page dots.
        Spacer(Modifier.height(32.dp))
    }
}

/** Page 2: today at a glance. Two small stat tiles, then to-do and notes, all in place. */
@Composable
private fun TodayPage(
    state: UiState,
    onWeek: () -> Unit,
    onPlant: () -> Unit,
    actions: TaskActions,
    onOpenNote: (Long) -> Unit,
    onGrantUsage: () -> Unit,
) {
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showAllNotes by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        val title = stringResource(R.string.today)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = tracking(title, (-1).sp))
            Spacer(Modifier.width(12.dp))
            Text(
                SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()),
                color = UnloopColors.Muted,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        Spacer(Modifier.height(18.dp))

        val usage = state.usage
        if (state.hasUsageAccess && usage != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                val limitMs = state.limitMinutes * 60_000L
                val over = usage.totalMs >= limitMs
                StatTile(edge = if (over) UnloopColors.Orange else UnloopColors.LimeEdge, onClick = onWeek, modifier = Modifier.weight(1f)) {
                    Label(stringResource(R.string.cat_screen_time), size = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(duration(usage.totalMs), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, maxLines = 1)
                    Text(
                        stringResource(if (over) R.string.over_short else R.string.of_short, limit(state.limitMinutes)),
                        color = UnloopColors.TextSoft,
                        fontFamily = Display,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(UnloopColors.Border)) {
                        Box(
                            Modifier
                                .fillMaxWidth((usage.totalMs.toFloat() / limitMs).coerceIn(0.02f, 1f))
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (over) UnloopColors.Orange else UnloopColors.Lime),
                        )
                    }
                }
                val leaves = state.leaves
                StatTile(edge = if (leaves == MAX_LEAVES) UnloopColors.LimeEdge else UnloopColors.Orange, onClick = onPlant, modifier = Modifier.weight(1f)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Label(stringResource(R.string.plant), size = 10.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "$leaves/$MAX_LEAVES",
                                color = if (leaves == MAX_LEAVES) UnloopColors.Text else UnloopColors.Orange,
                                fontFamily = Display,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 26.sp,
                            )
                            val streak = state.streak ?: 0
                            Text(
                                pluralStringResource(R.plurals.days_short, streak, streak),
                                color = UnloopColors.TextSoft,
                                fontFamily = Display,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Plant(leaves = leaves, flowering = state.flowering, modifier = Modifier.size(width = 48.dp, height = 64.dp))
                    }
                }
            }
        } else {
            Label(
                stringResource(R.string.allow_usage_access) + "  →",
                color = UnloopColors.Orange,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onGrantUsage)
                    .padding(vertical = 8.dp),
            )
        }
        if (state.showReflectionNudge && state.hasUsageAccess) {
            Spacer(Modifier.height(12.dp))
            Label(
                stringResource(R.string.week_ready),
                color = UnloopColors.Lime,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onWeek)
                    .padding(vertical = 6.dp),
            )
        }

        if (state.showTasks) {
            // To-do: add in place, check off with a small animation, tap a task to edit it.
            Spacer(Modifier.height(28.dp))
            val open = state.openTasks
            SectionHeader(stringResource(R.string.todo), count = open.size.takeIf { it > 0 })
            Spacer(Modifier.height(12.dp))
            PopCard(edge = UnloopColors.LimeEdge) {
                var draft by rememberSaveable { mutableStateOf("") }
                val add = {
                    actions.add(draft)
                    draft = ""
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnloopTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        hint = stringResource(R.string.add_task_hint),
                        onDone = add,
                        container = UnloopColors.Bg,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    PopButton("+", onClick = add, height = 48.dp, textSize = 26.sp, modifier = Modifier.width(52.dp))
                }
                if (open.isEmpty()) {
                    Text(
                        stringResource(R.string.nothing_to_do),
                        color = UnloopColors.Muted,
                        fontFamily = Display,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                }
                open.take(TASKS_SHOWN).forEach { task ->
                    key(task.id) {
                        TaskLine(task, onToggle = { actions.toggle(task.id) }, onOpen = { editingTask = task })
                    }
                }
                if (open.size > TASKS_SHOWN) {
                    AnimatedVisibility(visible = showAll, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            open.drop(TASKS_SHOWN).forEach { task ->
                                key(task.id) {
                                    TaskLine(task, onToggle = { actions.toggle(task.id) }, onOpen = { editingTask = task })
                                }
                            }
                        }
                    }
                    Label(
                        if (showAll) stringResource(R.string.show_less) + "  ▲" else stringResource(R.string.show_all_tasks, open.size) + "  ▼",
                        color = UnloopColors.Lime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAll = !showAll }
                            .padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                val done = state.doneTasks
                if (done.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(
                            "${stringResource(R.string.done_section)} · ${done.size}  " + if (showDone) "▲" else "▼",
                            color = UnloopColors.Muted,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDone = !showDone }
                                .padding(vertical = 6.dp),
                        )
                        Label(
                            stringResource(R.string.clear_done),
                            color = UnloopColors.Orange,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = actions.clearDone)
                                .padding(6.dp),
                        )
                    }
                    AnimatedVisibility(visible = showDone, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            done.forEach { task ->
                                key(task.id) {
                                    TaskLine(task, onToggle = { actions.toggle(task.id) }, onOpen = { editingTask = task })
                                }
                            }
                        }
                    }
                }
            }

            // Notes: a quiet grid; tapping one opens it straight away.
            Spacer(Modifier.height(28.dp))
            val notes = state.notes.filter { it.text.isNotBlank() }.sortedByDescending { it.updatedAt }
            SectionHeader(
                stringResource(R.string.notes_tab),
                count = notes.size.takeIf { it > 0 },
                action = stringResource(R.string.new_short),
                onAction = { onOpenNote(actions.newNote()) },
            )
            Spacer(Modifier.height(12.dp))
            if (notes.isEmpty()) {
                Text(stringResource(R.string.no_notes), color = UnloopColors.Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            @Composable
            fun NoteRows(list: List<Note>) = list.chunked(2).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .height(IntrinsicSize.Max),
                ) {
                    pair.forEach { note -> key(note.id) { NoteTile(note, Modifier.weight(1f)) { onOpenNote(note.id) } } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            NoteRows(notes.take(NOTES_SHOWN))
            if (notes.size > NOTES_SHOWN) {
                AnimatedVisibility(visible = showAllNotes, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column { NoteRows(notes.drop(NOTES_SHOWN)) }
                }
                Label(
                    if (showAllNotes) stringResource(R.string.show_less) + "  ▲" else stringResource(R.string.show_all_tasks, notes.size) + "  ▼",
                    color = UnloopColors.Lime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAllNotes = !showAllNotes }
                        .padding(vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(56.dp))
    }

    editingTask?.let { task ->
        TaskSheet(
            task = task,
            onDismiss = { editingTask = null },
            onSave = { title, note ->
                actions.update(task.id, title, note)
                editingTask = null
            },
            onDelete = {
                actions.delete(task.id)
                editingTask = null
            },
        )
    }
}

/** Open tasks shown before "Show all". */
private const val TASKS_SHOWN = 5

/** Notes shown before "Show all". */
private const val NOTES_SHOWN = 4

/**
 * One task. Checking an open task fills the circle, strikes the title, then the row folds away
 * before the task moves to Done. New tasks slide in.
 */
@Composable
private fun TaskLine(task: Task, onToggle: () -> Unit, onOpen: () -> Unit) {
    var checking by remember(task.id) { mutableStateOf(false) }
    val isNew = remember(task.id) { System.currentTimeMillis() - task.createdAt < 2_000 }
    val visible = remember(task.id) { MutableTransitionState(!isNew).apply { targetState = true } }
    LaunchedEffect(checking) {
        if (checking) {
            delay(420)
            visible.targetState = false
            delay(260)
            onToggle()
        }
    }
    val done = task.done || checking
    val titleColor by animateColorAsState(if (done) UnloopColors.Muted else UnloopColors.Text, tween(250), label = "title")
    AnimatedVisibility(
        visibleState = visible,
        enter = expandVertically(tween(260)) + fadeIn(tween(260)),
        exit = shrinkVertically(tween(260)) + fadeOut(tween(200)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCheck(done = done, onClick = { if (task.done) onToggle() else checking = true }, size = 24)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = titleColor,
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.note.isNotBlank()) {
                    Text(task.note, color = UnloopColors.Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StatTile(edge: Color, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    PopCard(
        edge = edge,
        fillHeight = true,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

/** Quiet section title with a grey count and an optional small lime action. */
@Composable
private fun SectionHeader(title: String, count: Int?, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = tracking(title, (-0.5).sp))
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", color = UnloopColors.Muted, fontFamily = Mono, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Label(
                action,
                color = UnloopColors.Lime,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

/** Note preview card. Presses down slightly when tapped. */
@Composable
private fun NoteTile(note: Note, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val lines = note.text.trim().lines()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    PopCard(
        fillHeight = true,
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Text(lines.first(), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val rest = lines.drop(1).joinToString(" ").trim()
        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(rest, color = UnloopColors.TextSoft, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlantCard(state: UiState, onClick: () -> Unit) {
    val leaves = state.leaves
    PopCard(
        edge = if (leaves == MAX_LEAVES) UnloopColors.LimeEdge else UnloopColors.Orange,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Plant(leaves = leaves, flowering = state.flowering, modifier = Modifier.size(width = 72.dp, height = 84.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Label(stringResource(R.string.plant_title))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (leaves == MAX_LEAVES) stringResource(R.string.leaves_all_safe) else stringResource(R.string.leaves_left, leaves, MAX_LEAVES),
                    color = if (leaves == MAX_LEAVES) UnloopColors.Text else UnloopColors.Orange,
                    fontFamily = Display,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                )
                val used = state.usage?.totalMs ?: 0L
                nextLeafInMs(used, state.limitMinutes)?.let { ms ->
                    Text(
                        stringResource(R.string.leaf_falls_soon, duration(ms + 59_999)),
                        color = UnloopColors.TextSoft,
                        fontFamily = Display,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                val streak = state.streak ?: 0
                Text(
                    pluralStringResource(R.plurals.days_protected, streak, streak),
                    color = UnloopColors.Lime,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Slim "time used of limit" line under the clock, with a thin bar. Tap for the Today page. */
@Composable
private fun TimeStrip(state: UiState, onClick: () -> Unit, onGrant: () -> Unit) {
    val usage = state.usage
    if (!state.hasUsageAccess || usage == null) {
        Label(
            stringResource(R.string.allow_usage_access) + "  →",
            color = UnloopColors.Orange,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onGrant)
                .padding(vertical = 8.dp),
        )
        return
    }
    val limitMs = state.limitMinutes * 60_000L
    val over = usage.totalMs >= limitMs
    val accent = if (over) UnloopColors.Orange else UnloopColors.Lime
    Column(
        Modifier
            .padding(top = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row {
            Text(
                duration(usage.totalMs),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (over) R.string.over_limit else R.string.of_limit, limit(state.limitMinutes)),
                color = UnloopColors.TextSoft,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(UnloopColors.Border),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((usage.totalMs.toFloat() / limitMs).coerceIn(0.02f, 1f))
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

/** Two small dots showing which home page is in view. */
@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) { i ->
            Box(
                Modifier
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i == current) UnloopColors.Lime else UnloopColors.Faint),
            )
        }
    }
}

/** How the home app names are laid out to fit the space left on screen. */
private data class HomeLayout(val nameSize: TextUnit, val showQuote: Boolean, val columns: Int, val scrolls: Boolean = false)

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

@Composable
private fun TasksCard(state: UiState, onOpen: () -> Unit, onToggle: (Long) -> Unit, onAdd: () -> Unit) {
    val open = state.openTasks
    PopCard(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.todo), modifier = Modifier.weight(1f))
            if (open.isNotEmpty()) {
                Text(pluralStringResource(R.plurals.tasks_left, open.size, open.size), color = UnloopColors.TextSoft, fontFamily = Mono, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (open.isEmpty()) {
            Text(
                stringResource(R.string.nothing_to_do),
                color = UnloopColors.Muted,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        open.take(5).forEach { task ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskCheck(done = false, onClick = { onToggle(task.id) }, size = 22)
                Spacer(Modifier.width(12.dp))
                Text(
                    task.title,
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Label(
            stringResource(R.string.add_short),
            color = UnloopColors.Lime,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAdd)
                .padding(top = 8.dp, bottom = 2.dp),
        )
    }
}
