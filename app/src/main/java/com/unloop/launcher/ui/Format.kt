package com.unloop.launcher.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.unloop.launcher.R

/** 4_320_000 → "1h 12m", 300_000 → "5m" (localized). */
fun formatDuration(res: Resources, ms: Long): String {
    val minutes = ms / 60_000
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return if (h > 0) res.getString(R.string.duration_hm, h, m) else res.getString(R.string.duration_m, m)
}

/** 120 → "2h", 90 → "1h 30m" (localized). */
fun formatLimit(res: Resources, minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> res.getString(R.string.duration_m, m)
        m == 0 -> res.getString(R.string.duration_h, h)
        else -> res.getString(R.string.duration_hm, h, m)
    }
}

@Composable
fun duration(ms: Long): String = formatDuration(LocalContext.current.resources, ms)

@Composable
fun limit(minutes: Int): String = formatLimit(LocalContext.current.resources, minutes)
