package com.unloop.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.R
import com.unloop.launcher.data.AppInfo
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.Mono
import com.unloop.launcher.ui.theme.UnloopColors
import com.unloop.launcher.ui.theme.tracking

/**
 * Searchable list of installed apps with a tick for each one in [selected]. Tapping a row calls
 * [onToggle]; the list updates live, so several apps can be added in one go. [trailing] shows a
 * small value next to selected apps (e.g. a limit).
 */
@Composable
fun AppPickerSheet(
    title: String,
    apps: List<AppInfo>,
    selected: Set<String>,
    onToggle: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    trailing: (String) -> String? = { null },
) {
    var query by rememberSaveable { mutableStateOf("") }
    // One row per package: the same app can have several launcher entries.
    val unique = remember(apps) { apps.distinctBy { it.packageName } }
    val shown = remember(unique, query) {
        val q = query.trim()
        unique.filter { it.label.contains(q, ignoreCase = true) }
    }

    UnloopSheet(onDismiss) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Text(
                title,
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                letterSpacing = tracking(title, (-1).sp),
            )
            Spacer(Modifier.height(14.dp))
            SearchField(value = query, onValueChange = { query = it }, onGo = { shown.firstOrNull()?.let(onToggle) })
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.packageName }) { app ->
                    val on = app.packageName in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app) }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            app.label,
                            color = if (on) UnloopColors.Text else UnloopColors.TextSoft,
                            fontFamily = Display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (on) {
                            trailing(app.packageName)?.let {
                                Text(it, color = UnloopColors.Orange, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                            }
                        }
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (on) UnloopColors.Lime else UnloopColors.Border),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Text("✓", color = UnloopColors.Ink, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(UnloopColors.Divider))
                }
            }
            Spacer(Modifier.height(12.dp))
            PopButton(stringResource(R.string.done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
