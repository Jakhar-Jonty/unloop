package com.unloop.launcher.ui.screens

import com.unloop.launcher.ui.limit
import com.unloop.launcher.ui.components.Chip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import com.unloop.launcher.ui.theme.tracking
import com.unloop.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.data.AppInfo
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.UnloopColors

@Composable
fun AppActionsSheet(
    app: AppInfo,
    isFavorite: Boolean,
    isPaused: Boolean,
    isHidden: Boolean,
    isFocusApp: Boolean,
    limitMinutes: Int?,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePaused: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleFocusApp: () -> Unit,
    onDailyLimit: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    UnloopSheet(onDismiss) {
        Text(
            app.label,
            fontFamily = Display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp,
            letterSpacing = tracking(app.label, (-1).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        ActionRow(if (isFavorite) stringResource(R.string.remove_from_home) else stringResource(R.string.add_to_home), onClick = onToggleFavorite)
        ActionRow(
            stringResource(R.string.pause_5s),
            state = if (isPaused) stringResource(R.string.toggle_on) else stringResource(R.string.toggle_off),
            stateColor = if (isPaused) UnloopColors.Orange else UnloopColors.Muted,
            onClick = onTogglePaused,
        )
        ActionRow(
            stringResource(R.string.daily_limit_app),
            state = limitMinutes?.let { limit(it) } ?: stringResource(R.string.toggle_off),
            stateColor = if (limitMinutes != null) UnloopColors.Orange else UnloopColors.Muted,
            onClick = onDailyLimit,
        )
        ActionRow(
            stringResource(R.string.allowed_in_focus),
            state = if (isFocusApp) stringResource(R.string.state_yes) else stringResource(R.string.state_no),
            stateColor = if (isFocusApp) UnloopColors.Lime else UnloopColors.Muted,
            onClick = onToggleFocusApp,
        )
        ActionRow(if (isHidden) stringResource(R.string.show_in_list) else stringResource(R.string.hide_from_list), onClick = onToggleHidden)
        ActionRow(stringResource(R.string.app_info), onClick = onAppInfo)
        ActionRow(stringResource(R.string.uninstall), color = UnloopColors.Orange, onClick = onUninstall)
    }
}

@Composable
private fun ActionRow(
    text: String,
    onClick: () -> Unit,
    color: Color = UnloopColors.Text,
    state: String? = null,
    stateColor: Color = UnloopColors.Muted,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = color,
            fontFamily = Display,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f),
        )
        if (state != null) Label(state, color = stateColor, size = 12.sp)
    }
}

/** Limit choices in minutes; null = no limit. */
private val limitChoices: List<Int?> = listOf(null, 15, 30, 45, 60, 120)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyLimitSheet(
    appLabel: String,
    current: Int?,
    onDismiss: () -> Unit,
    onChoose: (Int?) -> Unit,
) {
    UnloopSheet(onDismiss) {
        Text(
            stringResource(R.string.daily_limit_for, appLabel),
            fontFamily = Display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            letterSpacing = tracking(appLabel, (-1).sp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.daily_limit_sheet_body),
            color = UnloopColors.TextSoft,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            limitChoices.forEach { minutes ->
                Chip(
                    minutes?.let { limit(it) } ?: stringResource(R.string.toggle_off),
                    selected = current == minutes,
                    onClick = { onChoose(minutes) },
                )
            }
        }
    }
}
