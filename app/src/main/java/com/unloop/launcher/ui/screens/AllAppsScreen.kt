package com.unloop.launcher.ui.screens

import com.unloop.launcher.ui.theme.tracking
import androidx.compose.ui.res.pluralStringResource
import com.unloop.launcher.R
import androidx.compose.ui.res.stringResource
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.UiState
import com.unloop.launcher.data.AppInfo
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.PauseBadge
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.UnloopColors
import com.unloop.launcher.ui.theme.Mono

@Composable
fun AllAppsScreen(
    state: UiState,
    onOpenApp: (AppInfo) -> Unit,
    onAppActions: (AppInfo) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    val visible = remember(state.apps, state.hidden, query) {
        val q = query.trim()
        state.apps.filter { it.packageName !in state.hidden && it.label.contains(q, ignoreCase = true) }
    }
    val hiddenApps = remember(state.apps, state.hidden) {
        state.apps.filter { it.packageName in state.hidden }
    }
    val sections = remember(visible) { visible.groupBy { sectionOf(it.label) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Row {
            Text(
                stringResource(R.string.all_apps),
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 44.sp,
                letterSpacing = tracking(stringResource(R.string.all_apps), (-1.5).sp),
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
            )
            Text(
                pluralStringResource(R.plurals.installed, state.apps.size, state.apps.size),
                color = UnloopColors.TextSoft,
                fontFamily = Mono,
                fontSize = 13.sp,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(24.dp))
        Label(stringResource(R.string.search))
        Spacer(Modifier.height(10.dp))
        SearchField(
            value = query,
            onValueChange = { query = it },
            onGo = { visible.firstOrNull()?.let(onOpenApp) },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            sections.forEach { (letter, apps) ->
                item(key = "section_$letter") {
                    Text(
                        letter,
                        color = UnloopColors.Lime,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                    )
                }
                items(apps, key = { it.key }) { app ->
                    AppRow(
                        app = app,
                        paused = app.packageName in state.paused,
                        onClick = { onOpenApp(app) },
                        onLongClick = { onAppActions(app) },
                    )
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_match, query.trim()),
                        color = UnloopColors.Muted,
                        fontFamily = Display,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            if (query.isBlank()) {
                item(key = "settings") {
                    Label(
                        stringResource(R.string.unloop_settings),
                        color = UnloopColors.Lime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSettings)
                            .padding(top = 32.dp, bottom = 8.dp),
                    )
                }
            }
            if (hiddenApps.isNotEmpty() && query.isBlank()) {
                item(key = "hidden_toggle") {
                    Label(
                        if (showHidden) stringResource(R.string.collapse_hidden) else stringResource(R.string.show_hidden, hiddenApps.size),
                        color = UnloopColors.Muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHidden = !showHidden }
                            .padding(top = 32.dp, bottom = 8.dp),
                    )
                }
                if (showHidden) {
                    items(hiddenApps, key = { "hidden_${it.key}" }) { app ->
                        AppRow(
                            app = app,
                            paused = app.packageName in state.paused,
                            dim = true,
                            onClick = { onOpenApp(app) },
                            onLongClick = { onAppActions(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    paused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    dim: Boolean = false,
) {
    val view = LocalView.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongClick()
                    },
                )
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                app.label,
                color = if (paused || dim) UnloopColors.Muted else UnloopColors.Text,
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (paused) PauseBadge(Modifier.padding(start = 12.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(UnloopColors.Divider),
        )
    }
}

@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, onGo: () -> Unit) {
    Box(Modifier.height(60.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .background(UnloopColors.SurfaceEdge, CircleShape),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = UnloopColors.Text,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
            cursorBrush = SolidColor(UnloopColors.Lime),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(UnloopColors.Surface, CircleShape)
                .border(1.dp, UnloopColors.Border, CircleShape),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            color = UnloopColors.Muted,
                            fontFamily = Display,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

private fun sectionOf(label: String): String {
    val c = label.firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c.isLetter()) c.toString() else "#"
}
