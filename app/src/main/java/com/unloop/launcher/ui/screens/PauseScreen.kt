package com.unloop.launcher.ui.screens

import com.unloop.launcher.ui.theme.tracking
import com.unloop.launcher.ui.duration
import com.unloop.launcher.ui.limit
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import com.unloop.launcher.R
import androidx.compose.ui.res.stringResource
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.data.AppUsage
import com.unloop.launcher.ui.components.Chip
import com.unloop.launcher.ui.components.GhostButton
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.components.PopCard
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.UnloopColors
import kotlin.math.ceil

/** Wait before "5 more minutes" on the daily-limit screen. */
const val LIMIT_WAIT_SECONDS = 10

/** Shown instead of the intention prompt when an app's daily limit is used up. */
data class LimitReached(val usedMs: Long, val limitMinutes: Int, val canExtend: Boolean)

/**
 * The pause before a distracting app: a [seconds]-long breathing countdown, then "why are you
 * opening this?". With [limit] set it becomes the daily-limit block screen instead.
 */
@Composable
fun PauseScreen(
    appLabel: String,
    appKey: String,
    usage: AppUsage?,
    hasUsageAccess: Boolean,
    seconds: Int,
    onGoHome: () -> Unit,
    onOpenAnyway: () -> Unit,
    limit: LimitReached? = null,
) {
    BackHandler(onBack = onGoHome)
    val view = LocalView.current

    val progress = remember(appKey) { Animatable(0f) }
    LaunchedEffect(appKey) {
        progress.animateTo(1f, tween(seconds * 1000, easing = LinearEasing))
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }
    val done = progress.value >= 1f
    var reason by remember(appKey) { mutableStateOf<Reason?>(null) }
    val suggestions = stringArrayResource(R.array.suggestions)
    val suggestion = remember(appKey, suggestions) { suggestions.random() }
    val remaining = ceil(seconds * (1f - progress.value)).toInt()

    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Label(
            stringResource(if (limit != null) R.string.limit_reached_label else R.string.about_to_open),
            color = UnloopColors.Orange,
            size = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            appLabel,
            fontFamily = Display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 52.sp,
            lineHeight = 54.sp,
            letterSpacing = tracking(appLabel, (-2).sp),
            maxLines = 2,
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (limit != null) {
                // The countdown only gates "5 more minutes"; the message shows straight away.
                LimitMessage(appLabel, limit)
            } else if (done) {
                IntentionPrompt(
                    appLabel = appLabel,
                    reason = reason,
                    suggestion = suggestion,
                    onReason = { reason = it },
                )
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(230.dp)
                        .graphicsLayer {
                            scaleX = breath
                            scaleY = breath
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        drawCircle(UnloopColors.Surface)
                        val inset = stroke / 2 + 14.dp.toPx()
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(
                            color = UnloopColors.Border,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke),
                        )
                        drawArc(
                            color = UnloopColors.Lime,
                            startAngle = -90f,
                            sweepAngle = 360f * (1f - progress.value),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    Text(
                        remaining.toString(),
                        color = UnloopColors.Lime,
                        fontFamily = Display,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 96.sp,
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.take_breath),
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }

        PopCard {
            Row {
                val opens = usage?.opens ?: 0
                Stat(stringResource(R.string.opened_today), pluralStringResource(R.plurals.times, opens, opens), hasUsageAccess, Modifier.weight(1f))
                Stat(stringResource(R.string.time_spent), duration(usage?.timeMs ?: 0L), hasUsageAccess, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        PopButton(stringResource(R.string.go_back_home), onClick = onGoHome, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        when {
            limit == null -> GhostButton(
                when {
                    !done -> stringResource(R.string.open_anyway_in, remaining)
                    reason == null -> stringResource(R.string.pick_reason)
                    else -> stringResource(R.string.open_anyway)
                },
                onClick = onOpenAnyway,
                enabled = done && reason != null,
            )
            limit.canExtend -> GhostButton(
                if (done) stringResource(R.string.five_more_minutes) else stringResource(R.string.five_more_minutes_in, remaining),
                onClick = onOpenAnyway,
                enabled = done,
            )
            // Strict mode: no way past the limit today.
            else -> Spacer(Modifier.height(52.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LimitMessage(appLabel: String, info: LimitReached) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.limit_reached_title),
            fontFamily = Display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            lineHeight = 32.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.limit_reached_body, appLabel, duration(info.usedMs), limit(info.limitMinutes)),
            color = UnloopColors.TextSoft,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )
    }
}

private enum class Reason(@StringRes val label: Int, val mindless: Boolean) {
    Reply(R.string.reason_reply, false),
    LookUp(R.string.reason_look_up, false),
    Bored(R.string.reason_bored, true),
    Habit(R.string.reason_habit, true),
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntentionPrompt(
    appLabel: String,
    reason: Reason?,
    suggestion: String,
    onReason: (Reason) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.why_open, appLabel),
            fontFamily = Display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            letterSpacing = tracking(stringResource(R.string.why_open, appLabel), (-0.5).sp),
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Reason.entries.forEach { r ->
                Chip(stringResource(r.label), selected = reason == r, onClick = { onReason(r) })
            }
        }
        AnimatedVisibility(visible = reason?.mindless == true) {
            Column {
                Spacer(Modifier.height(22.dp))
                Label(stringResource(R.string.try_instead), color = UnloopColors.Lime)
                Spacer(Modifier.height(6.dp))
                Text(suggestion, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, hasAccess: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Label(label)
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasAccess) value else "—",
            fontFamily = Display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            letterSpacing = tracking(if (hasAccess) value else "—", (-0.5).sp),
        )
    }
}
