@file:OptIn(ExperimentalTextApi::class)

package com.unloop.launcher.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.unloop.launcher.R

object UnloopColors {
    val Bg = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141414)
    val SurfaceEdge = Color(0xFF1F1F1F)
    val Border = Color(0xFF262626)
    val Divider = Color(0xFF1C1C1C)
    val Text = Color(0xFFF2F0EA)
    val TextSoft = Color(0xFFBDBBB4)
    val Muted = Color(0xFF7C7B77)
    val Faint = Color(0xFF4A4A47)
    val Lime = Color(0xFFD4F25A)
    val LimeEdge = Color(0xFF7E9433)
    val Orange = Color(0xFFF07A4A)
    val Paper = Color(0xFFF2F0EA)
    val PaperEdge = Color(0xFF8C8A84)
    val Ink = Color(0xFF0A0A0A)
}

/** Heavy grotesk for clock, titles and app names. Variable font: weight + optical size. */
val Display = FontFamily(
    Font(
        R.font.bricolage, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.Setting("opsz", 24f)),
    ),
    Font(
        R.font.bricolage, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("opsz", 36f)),
    ),
    Font(
        R.font.bricolage, FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.Setting("opsz", 96f)),
    ),
)

/** Monospace for small uppercase labels and numbers. */
val Mono = FontFamily(
    Font(R.font.jbmono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jbmono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.jbmono, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@Composable
fun UnloopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = UnloopColors.Bg,
            surface = UnloopColors.Surface,
            primary = UnloopColors.Lime,
            onPrimary = UnloopColors.Ink,
            onBackground = UnloopColors.Text,
            onSurface = UnloopColors.Text,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides UnloopColors.Text, content = content)
    }
}

/**
 * Letter-spacing for [text]: the tight (or wide) tracking tuned for the Latin fonts breaks
 * measurement and shaping of scripts that fall back to a system font, such as Devanagari.
 */
fun tracking(text: String, spacing: TextUnit): TextUnit =
    if (text.all { it.code < 0x0370 }) spacing else 0.sp
