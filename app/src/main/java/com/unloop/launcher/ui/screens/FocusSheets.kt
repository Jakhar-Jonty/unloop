package com.unloop.launcher.ui.screens

import com.unloop.launcher.ui.theme.tracking
import com.unloop.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.ui.components.Chip
import com.unloop.launcher.ui.components.GhostButton
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.UnloopColors

/** Durations offered when starting Focus mode, in minutes; null = until stopped. */
private val durations: List<Int?> = listOf(25, 50, 90, null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartFocusSheet(
    allowedLabels: List<String>,
    silence: Boolean,
    hasDndAccess: Boolean,
    onSilenceChange: (Boolean) -> Unit,
    onGrantDnd: () -> Unit,
    onDismiss: () -> Unit,
    onStart: (minutes: Int?) -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(1) }
    UnloopSheet(onDismiss) {
        Text(stringResource(R.string.focus_mode), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = tracking(stringResource(R.string.focus_mode), (-1).sp))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.focus_sheet_body),
            color = UnloopColors.TextSoft,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(22.dp))
        Label(stringResource(R.string.how_long))
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            durations.forEachIndexed { i, minutes ->
                val label = minutes?.let { stringResource(R.string.minutes_short, it) } ?: stringResource(R.string.until_i_stop)
                Chip(label, selected = choice == i, onClick = { choice = i })
            }
        }
        Spacer(Modifier.height(22.dp))
        Label(stringResource(R.string.still_available))
        Spacer(Modifier.height(8.dp))
        Text(
            allowedLabels.joinToString("  ·  ").ifEmpty { stringResource(R.string.no_focus_apps) },
            fontFamily = Display,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.change_focus_apps),
            color = UnloopColors.Muted,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(22.dp))
        Label(stringResource(R.string.notifications))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(stringResource(R.string.silence_them), selected = silence && hasDndAccess, onClick = {
                if (hasDndAccess) onSilenceChange(true) else onGrantDnd()
            })
            Chip(stringResource(R.string.let_through), selected = !(silence && hasDndAccess), onClick = { onSilenceChange(false) })
        }
        if (!hasDndAccess) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.dnd_needed),
                color = UnloopColors.Muted,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(26.dp))
        PopButton(stringResource(R.string.start_focus), onClick = { onStart(durations[choice]) }, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndFocusSheet(
    remainingText: String,
    /** Set while strict mode is on: explain instead of offering "End now". */
    strictUntilText: String?,
    onDismiss: () -> Unit,
    onEnd: () -> Unit,
) {
    UnloopSheet(onDismiss) {
        Text(stringResource(R.string.end_focus_title), fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = tracking(stringResource(R.string.end_focus_title), (-1).sp))
        Spacer(Modifier.height(8.dp))
        Text(
            remainingText,
            color = UnloopColors.TextSoft,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(26.dp))
        if (strictUntilText != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.strict_blocked, strictUntilText),
                color = UnloopColors.Orange,
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(20.dp))
            PopButton(stringResource(R.string.keep_focusing), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        } else {
            PopButton(stringResource(R.string.keep_focusing), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            GhostButton(stringResource(R.string.end_now), onClick = onEnd)
        }
    }
}

/** Bottom sheet in Unloop styling. Shared by every sheet in the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnloopSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = UnloopColors.Surface,
        contentColor = UnloopColors.Text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(UnloopColors.Border, CircleShape),
            )
        },
    ) {
        Column(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 24.dp)) {
            content()
        }
    }
}
