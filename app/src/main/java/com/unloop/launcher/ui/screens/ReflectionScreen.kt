package com.unloop.launcher.ui.screens

import com.unloop.launcher.ui.theme.tracking
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.limit
import com.unloop.launcher.ui.duration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.unloop.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.UiState
import com.unloop.launcher.WeekReport
import com.unloop.launcher.data.DayLog
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.PopCard
import com.unloop.launcher.ui.formatDuration
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.Mono
import com.unloop.launcher.ui.theme.UnloopColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReflectionScreen(state: UiState, onBack: () -> Unit, onShare: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val week = state.week

    Column(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Label(stringResource(R.string.last_7_days), color = UnloopColors.Lime)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.your_week), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, letterSpacing = tracking(stringResource(R.string.your_week), (-1.5).sp))
        Spacer(Modifier.height(24.dp))

        when {
            !state.hasUsageAccess -> Message(stringResource(R.string.week_needs_access))
            week == null -> Message(stringResource(R.string.week_loading))
            week.knownDays == 0 -> Message(stringResource(R.string.week_no_data))
            else -> WeekBody(week, state, onShare)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun WeekBody(week: WeekReport, state: UiState, onShare: (String) -> Unit) {
    val limitMs = state.limitMinutes * 60_000L
    var selected by remember(week) { mutableIntStateOf(week.days.lastIndex) }
    val daysUnder = pluralStringResource(R.plurals.days_of, week.knownDays, week.daysUnderLimit, week.knownDays)
    val last = week.lastWeekAvgMs
    val shareText = stringResource(
        R.string.share_week_text,
        duration(week.avgMs),
        if (last != null && last > 0) stringResource(R.string.share_week_vs, duration(last)) else stringResource(R.string.share_week_first),
        daysUnder,
        week.turnedBack,
    )
    val day = week.days[selected]

    // Headline: daily average, compared with last week in words as well as colour.
    PopCard(edge = UnloopColors.SurfaceEdge) {
        Label(stringResource(R.string.daily_average))
        Spacer(Modifier.height(8.dp))
        Text(duration(week.avgMs), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, letterSpacing = tracking(duration(week.avgMs), (-2).sp))
        if (last != null && last > 0) {
            val change = ((week.avgMs - last) * 100f / last).roundToInt()
            val (text, color) = when {
                change < 0 -> stringResource(R.string.less_than_last_week, abs(change)) to UnloopColors.Lime
                change > 0 -> stringResource(R.string.more_than_last_week, change) to UnloopColors.Orange
                else -> stringResource(R.string.same_as_last_week) to UnloopColors.TextSoft
            }
            Text(text, color = color, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        } else {
            Text(stringResource(R.string.no_last_week), color = UnloopColors.Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }

    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Label(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(day.dayStart)), modifier = Modifier.weight(1f))
        Text(
            if (day.known) duration(day.totalMs) else stringResource(R.string.no_data),
            fontFamily = Display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
        )
    }
    Spacer(Modifier.height(12.dp))
    WeekChart(days = week.days, limitMs = limitMs, limitText = stringResource(R.string.limit_label, limit(state.limitMinutes)).uppercase(), selected = selected, onSelect = { selected = it })
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.tap_day_hint, limit(state.limitMinutes)),
        color = UnloopColors.Muted,
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    )

    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatTile(stringResource(R.string.under_limit), daysUnder, Modifier.weight(1f))
        StatTile(stringResource(R.string.unlocks_a_day), "${week.avgUnlocks}", Modifier.weight(1f))
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatTile(stringResource(R.string.turned_back), pluralStringResource(R.plurals.times, week.turnedBack, week.turnedBack), Modifier.weight(1f))
        StatTile(stringResource(R.string.opened_anyway_stat), pluralStringResource(R.plurals.times, week.openedAnyway, week.openedAnyway), Modifier.weight(1f))
    }

    if (week.topApps.isNotEmpty()) {
        Spacer(Modifier.height(32.dp))
        Label(stringResource(R.string.where_time_went))
        Spacer(Modifier.height(4.dp))
        week.topApps.forEach { app ->
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.labelFor(app.packageName),
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(duration(app.timeMs), color = UnloopColors.TextSoft, fontFamily = Mono, fontSize = 14.sp)
            }
        }
    }

    Spacer(Modifier.height(32.dp))
    Text(
        when {
            week.turnedBack > week.openedAnyway -> stringResource(R.string.closing_turned_back)
            week.daysUnderLimit * 2 >= week.knownDays -> stringResource(R.string.closing_under_limit)
            else -> stringResource(R.string.closing_heavy)
        },
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )
    Spacer(Modifier.height(28.dp))
    PopButton(stringResource(R.string.share_week), onClick = { onShare(shareText) }, modifier = Modifier.fillMaxWidth())
}

/**
 * Seven daily bars with a dashed limit line. Bars over the limit are orange and carry their
 * value, so the over/under reading never depends on colour alone.
 */
@Composable
private fun WeekChart(days: List<DayLog>, limitMs: Long, limitText: String, selected: Int, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val dayFormat = remember { SimpleDateFormat("EEEEE", Locale.getDefault()) }
    val res = LocalContext.current.resources
    val labelStyle = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = UnloopColors.Muted)
    val selectedStyle = labelStyle.copy(color = UnloopColors.Text, fontWeight = FontWeight.Bold)
    val valueStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = UnloopColors.TextSoft)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(days.size) {
                detectTapGestures { offset ->
                    val slot = size.width / days.size
                    onSelect((offset.x / slot).toInt().coerceIn(0, days.lastIndex))
                }
            },
    ) {
        val labelSpace = 24.dp.toPx()
        val valueSpace = 18.dp.toPx()
        val plotTop = valueSpace
        val plotBottom = size.height - labelSpace
        val plotHeight = plotBottom - plotTop
        val maxMs = maxOf(days.maxOf { it.totalMs }, limitMs, 1L) * 1.08f
        val slot = size.width / days.size
        val barWidth = slot * 0.46f
        val radius = CornerRadius(4.dp.toPx())

        // Recessive baseline.
        drawLine(UnloopColors.Border, Offset(0f, plotBottom), Offset(size.width, plotBottom), strokeWidth = 1.dp.toPx())

        // Limit reference line, drawn under the bars, labelled just above it at the left.
        val limitY = plotBottom - limitMs / maxMs * plotHeight
        drawLine(
            UnloopColors.TextSoft,
            Offset(0f, limitY),
            Offset(size.width, limitY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
        val limitLabel = measurer.measure(limitText, labelStyle.copy(color = UnloopColors.TextSoft))
        drawText(limitLabel, topLeft = Offset(0f, limitY - limitLabel.size.height - 2.dp.toPx()))

        days.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2
            val left = cx - barWidth / 2
            if (d.known && d.totalMs > 0) {
                val h = (d.totalMs / maxMs * plotHeight).coerceAtLeast(4.dp.toPx())
                val over = d.totalMs >= limitMs
                val color = if (over) UnloopColors.Orange else UnloopColors.Lime
                // Rounded top, square base sitting on the baseline.
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = plotBottom - h, right = left + barWidth, bottom = plotBottom,
                            topLeftCornerRadius = radius, topRightCornerRadius = radius,
                        ),
                    )
                }
                drawPath(path, color.copy(alpha = if (i == selected) 1f else 0.72f))
                if (over) {
                    val text = measurer.measure(formatDuration(res, d.totalMs).replace(" ", ""), valueStyle)
                    drawText(text, topLeft = Offset(cx - text.size.width / 2f, plotBottom - h - text.size.height - 2.dp.toPx()))
                }
            } else if (!d.known) {
                // Unknown day: faint outline instead of a misleading zero.
                drawRoundRect(
                    UnloopColors.Faint,
                    topLeft = Offset(left, plotBottom - 12.dp.toPx()),
                    size = Size(barWidth, 12.dp.toPx()),
                    cornerRadius = radius,
                    style = Stroke(1.dp.toPx()),
                )
            }
            val label = measurer.measure(dayFormat.format(Date(d.dayStart)), if (i == selected) selectedStyle else labelStyle)
            drawText(label, topLeft = Offset(cx - label.size.width / 2f, plotBottom + 6.dp.toPx()))
        }

    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    PopCard(modifier = modifier) {
        Label(label)
        Spacer(Modifier.height(6.dp))
        Text(value, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, color = UnloopColors.TextSoft, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
}

