package com.unloop.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.R
import com.unloop.launcher.UiState
import com.unloop.launcher.data.AppInfo
import com.unloop.launcher.data.MAX_FAVORITES
import com.unloop.launcher.ui.components.GhostButton
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.MAX_LEAVES
import com.unloop.launcher.ui.components.Plant
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.limit
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.UnloopColors
import com.unloop.launcher.ui.theme.tracking
import kotlinx.coroutines.delay

/** What the setup flow can ask the app to do. */
class OnboardingActions(
    val setHome: () -> Unit,
    val grantUsage: () -> Unit,
    val toggleFavorite: (String) -> Unit,
    val togglePaused: (String) -> Unit,
    val setLimit: (Int) -> Unit,
    val openAccessibility: () -> Unit,
    val finish: () -> Unit,
)

private enum class Step { Welcome, Home, Usage, Apps, Pause, Limit, Everywhere, Done }

/** First-run setup: eight short animated steps that leave Unloop ready to use. */
@Composable
fun OnboardingScreen(state: UiState, actions: OnboardingActions) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var forward by rememberSaveable { mutableIntStateOf(1) }
    val step = Step.entries[index]
    fun go(to: Int) {
        forward = if (to > index) 1 else -1
        index = to.coerceIn(0, Step.entries.lastIndex)
    }
    val next = { go(index + 1) }
    BackHandler(enabled = index > 0) { go(index - 1) }

    // Finale: a lime circle bursts out from the Start button and fills the screen, then home takes over.
    val burst = remember { Animatable(0f) }
    var leaving by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(leaving) {
        if (leaving == 1) {
            burst.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            actions.finish()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Progress(current = index, total = Step.entries.size)
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 3 * forward } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 3 * forward } + fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { s ->
            Column(Modifier.fillMaxSize()) {
                when (s) {
                    Step.Welcome -> Welcome(next)
                    Step.Home -> InfoStep(
                        art = { HomeArt() },
                        title = stringResource(R.string.ob_home_title),
                        body = stringResource(R.string.ob_home_body),
                        done = state.isDefaultLauncher,
                        primary = stringResource(R.string.ob_set_home),
                        onPrimary = actions.setHome,
                        onNext = next,
                    )
                    Step.Usage -> InfoStep(
                        art = { UsageArt() },
                        title = stringResource(R.string.ob_usage_title),
                        body = stringResource(R.string.ob_usage_body),
                        done = state.hasUsageAccess,
                        primary = stringResource(R.string.ob_allow),
                        onPrimary = actions.grantUsage,
                        onNext = next,
                    )
                    Step.Apps -> PickStep(
                        title = stringResource(R.string.ob_apps_title),
                        body = stringResource(R.string.ob_apps_body, MAX_FAVORITES),
                        apps = state.apps,
                        selected = state.favorites.map { it.packageName }.toSet(),
                        max = MAX_FAVORITES,
                        onToggle = actions.toggleFavorite,
                        onNext = next,
                    )
                    Step.Pause -> PickStep(
                        title = stringResource(R.string.ob_pause_title),
                        body = stringResource(R.string.ob_pause_body),
                        apps = state.apps,
                        selected = state.paused,
                        max = null,
                        onToggle = actions.togglePaused,
                        onNext = next,
                    )
                    Step.Limit -> LimitStep(state.limitMinutes, actions.setLimit, next)
                    Step.Everywhere -> InfoStep(
                        art = { BreathArt() },
                        title = stringResource(R.string.ob_everywhere_title),
                        body = stringResource(R.string.ob_everywhere_body),
                        done = state.pauseEverywhere,
                        primary = stringResource(R.string.ob_turn_on),
                        onPrimary = actions.openAccessibility,
                        onNext = next,
                        extra = {
                            Spacer(Modifier.height(16.dp))
                            Promise(stringResource(R.string.ob_sees), good = true)
                            Promise(stringResource(R.string.ob_never), good = false)
                            Promise(stringResource(R.string.a11y_no_share), good = false)
                        },
                    )
                    Step.Done -> DoneStep { leaving = 1 }
                }
            }
        }
    }
        if (burst.value > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val origin = Offset(size.width / 2, size.height - 90.dp.toPx())
                val reach = kotlin.math.hypot(size.width, size.height)
                drawCircle(UnloopColors.Lime, radius = reach * burst.value, center = origin)
            }
        }
    }
}

/** Segmented progress: filled lime up to the current step. */
@Composable
private fun Progress(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(total) { i ->
            val color by animateColorAsState(if (i <= current) UnloopColors.Lime else UnloopColors.Border, tween(300), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 44.sp, letterSpacing = tracking(text, (-1.5).sp))
}

@Composable
private fun Body(text: String) {
    Text(text, color = UnloopColors.TextSoft, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp)
}

/** Welcome: the lime loop draws itself, then the wordmark and tagline fade in. */
@Composable
private fun Welcome(onNext: () -> Unit) {
    val draw = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        reveal.animateTo(1f, tween(500))
    }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("unl", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, letterSpacing = (-2).sp, modifier = Modifier.graphicsLayer { alpha = reveal.value })
            // Sized to the letters' x-height, so the loop reads as the "oo".
            LoopMark(
                progress = draw.value,
                modifier = Modifier
                    .padding(start = 2.dp, end = 2.dp, top = 8.dp)
                    .size(width = 76.dp, height = 36.dp),
            )
            Text("p", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, modifier = Modifier.graphicsLayer { alpha = reveal.value })
        }
        Spacer(Modifier.height(36.dp))
        Column(Modifier.graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * 40f }) {
            Title(stringResource(R.string.ob_welcome_title))
            Spacer(Modifier.height(14.dp))
            Body(stringResource(R.string.ob_welcome_body))
        }
        Spacer(Modifier.weight(1.2f))
        PopButton(stringResource(R.string.ob_start), onClick = onNext, modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = reveal.value })
        Spacer(Modifier.height(24.dp))
    }
}

/** The broken infinity loop from the logo, drawn up to [progress]. */
@Composable
private fun LoopMark(progress: Float, modifier: Modifier = Modifier) {
    val path = remember {
        // Same curve as the app icon, in its own 94 x 40 box (x 119..213, y 5..45).
        Path().apply {
            moveTo(84f, 1.5f)
            cubicTo(90.5f, 4f, 94f, 11f, 94f, 20f)
            cubicTo(94f, 31f, 87f, 40f, 76f, 40f)
            cubicTo(67f, 40f, 61f, 35f, 47f, 20f)
            cubicTo(33f, 5f, 27f, 0f, 18f, 0f)
            cubicTo(7f, 0f, 0f, 9f, 0f, 20f)
            cubicTo(0f, 31f, 7f, 40f, 18f, 40f)
            cubicTo(27f, 40f, 33f, 35f, 47f, 20f)
            cubicTo(57f, 9f, 62f, 3.5f, 69f, 1f)
        }
    }
    val measure = remember { PathMeasure().apply { setPath(path, false) } }
    Canvas(modifier) {
        val s = (size.width / 104f).coerceAtMost(size.height / 50f)
        val segment = Path()
        measure.getSegment(0f, measure.length * progress, segment, true)
        translate(left = (size.width - 94f * s) / 2, top = (size.height - 40f * s) / 2) {
            scale(s, pivot = Offset.Zero) {
                drawPath(segment, UnloopColors.Lime, style = Stroke(width = 10f, cap = StrokeCap.Butt))
            }
        }
    }
}

/** A step with an illustration, a title, a reason and one thing to do. */
@Composable
private fun InfoStep(
    art: @Composable () -> Unit,
    title: String,
    body: String,
    done: Boolean,
    primary: String,
    onPrimary: () -> Unit,
    onNext: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.CenterStart) { art() }
        Spacer(Modifier.height(28.dp))
        Title(title)
        Spacer(Modifier.height(14.dp))
        Body(body)
        extra()
        Spacer(Modifier.weight(1f))
        AnimatedContent(targetState = done, label = "done") { isDone ->
            Column {
                if (isDone) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                        TaskCheck(done = true, onClick = {}, size = 24)
                        Spacer(Modifier.width(10.dp))
                        Label(stringResource(R.string.ob_done_check), color = UnloopColors.Lime)
                    }
                    PopButton(stringResource(R.string.ob_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
                } else {
                    PopButton(primary, onClick = onPrimary, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    GhostButton(stringResource(R.string.ob_later), onClick = onNext)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Promise(text: String, good: Boolean) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (good) UnloopColors.Lime else UnloopColors.Orange),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (good) "✓" else "✕", color = UnloopColors.Ink, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
    }
}

/** App names appear one by one, like the home screen. */
@Composable
private fun HomeArt() {
    val names = listOf("Phone", "Messages", "Camera", "Maps")
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(names.size) {
            delay(220)
            shown++
        }
    }
    Column {
        names.forEachIndexed { i, name ->
            AnimatedVisibility(visible = i < shown, enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 4 }) {
                Text(name, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-1).sp, color = if (i == 0) UnloopColors.Text else UnloopColors.TextSoft)
            }
        }
    }
}

/** Screen time counting up with its bar. */
@Composable
private fun UsageArt() {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    val minutes = (72 * p.value).toInt()
    Column(Modifier.fillMaxWidth()) {
        Label(stringResource(R.string.screen_time_today))
        Spacer(Modifier.height(8.dp))
        Text("${minutes / 60}h ${minutes % 60}m", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, letterSpacing = (-2).sp)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(UnloopColors.Border)) {
            Box(Modifier.fillMaxWidth(0.6f * p.value).height(8.dp).clip(CircleShape).background(UnloopColors.Lime))
        }
    }
}

/** The breathing ring from the pause screen. */
@Composable
private fun BreathArt() {
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Box(
        Modifier
            .size(150.dp)
            .graphicsLayer {
                scaleX = breath
                scaleY = breath
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(UnloopColors.Surface)
            drawCircle(UnloopColors.Lime, radius = size.minDimension / 2 - 14.dp.toPx(), style = Stroke(8.dp.toPx()))
        }
        Text("5", color = UnloopColors.Lime, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp)
    }
}

/** Tick apps in a list; ticked ones float to the top. [max] caps the selection. */
@Composable
private fun PickStep(
    title: String,
    body: String,
    apps: List<AppInfo>,
    selected: Set<String>,
    max: Int?,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
) {
    // Order fixed when the step opens, so rows don't jump while ticking.
    val ordered = remember(apps) {
        apps.distinctBy { it.packageName }.sortedWith(compareByDescending<AppInfo> { it.packageName in selected }.thenBy { it.label.lowercase() })
    }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(32.dp))
        Title(title)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Body(body) }
            if (max != null) Label(stringResource(R.string.ob_selected, selected.size, max), color = UnloopColors.Lime)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(ordered, key = { it.packageName }) { app ->
                val on = app.packageName in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(app.packageName) }
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        app.label,
                        color = if (on) UnloopColors.Text else UnloopColors.TextSoft,
                        fontFamily = Display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TaskCheck(done = on, onClick = { onToggle(app.packageName) }, size = 24)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PopButton(stringResource(R.string.ob_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

/** Daily limit with the full plant beside it. */
@Composable
private fun LimitStep(minutes: Int, onSet: (Int) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.CenterStart) {
            Plant(leaves = MAX_LEAVES, flowering = false, modifier = Modifier.size(width = 130.dp, height = 160.dp))
        }
        Spacer(Modifier.height(28.dp))
        Title(stringResource(R.string.ob_limit_title))
        Spacer(Modifier.height(14.dp))
        Body(stringResource(R.string.ob_limit_body))
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(targetState = minutes, label = "limit", modifier = Modifier.weight(1f)) { m ->
                Text(limit(m), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, letterSpacing = (-1.5).sp)
            }
            PopButton("−", onClick = { onSet(minutes - 15) }, face = UnloopColors.Paper, edge = UnloopColors.PaperEdge, height = 52.dp, textSize = 24.sp, modifier = Modifier.width(64.dp))
            Spacer(Modifier.width(12.dp))
            PopButton("+", onClick = { onSet(minutes + 15) }, height = 52.dp, textSize = 24.sp, modifier = Modifier.width(64.dp))
        }
        Spacer(Modifier.weight(1f))
        PopButton(stringResource(R.string.ob_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

/** The plant grows its leaves one by one, then a flower, then "Start". */
@Composable
private fun DoneStep(onFinish: () -> Unit) {
    var leaves by remember { mutableIntStateOf(0) }
    var bloom by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(250)
        repeat(MAX_LEAVES) {
            delay(170)
            leaves++
        }
        delay(250)
        bloom = 1
    }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Plant(leaves = leaves, flowering = bloom == 1, growing = true, modifier = Modifier.size(width = 150.dp, height = 190.dp))
        Spacer(Modifier.height(32.dp))
        AnimatedVisibility(visible = bloom == 1, enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.96f)) {
            Column {
                Title(stringResource(R.string.ob_done_title))
                Spacer(Modifier.height(14.dp))
                Body(stringResource(R.string.ob_done_body))
            }
        }
        Spacer(Modifier.weight(1.2f))
        PopButton(stringResource(R.string.ob_go), onClick = onFinish, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Prominent disclosure for the accessibility service, shown before every trip to Accessibility
 * settings outside of setup (Google Play's Accessibility API policy).
 */
@Composable
fun AccessibilityConsentSheet(onAgree: () -> Unit, onDismiss: () -> Unit) {
    UnloopSheet(onDismiss) {
        Text(stringResource(R.string.pause_everywhere), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
        Spacer(Modifier.height(10.dp))
        Body(stringResource(R.string.ob_everywhere_body))
        Spacer(Modifier.height(14.dp))
        Promise(stringResource(R.string.ob_sees), good = true)
        Promise(stringResource(R.string.ob_never), good = false)
        Promise(stringResource(R.string.a11y_no_share), good = false)
        Spacer(Modifier.height(22.dp))
        PopButton(stringResource(R.string.ob_turn_on), onClick = onAgree, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.ob_later), onClick = onDismiss)
    }
}
