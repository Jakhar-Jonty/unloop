package com.unloop.launcher.ui.components

import com.unloop.launcher.ui.theme.tracking
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.unloop.launcher.R
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.UnloopColors
import com.unloop.launcher.ui.theme.Mono

/** How far a NeoPOP surface sits above its edge. Pressing sinks it by this much. */
private val Depth = 5.dp

/** NeoPOP pill: solid face over a darker edge; sinks onto the edge while pressed. */
@Composable
fun PopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    face: Color = UnloopColors.Lime,
    edge: Color = UnloopColors.LimeEdge,
    textColor: Color = UnloopColors.Ink,
    height: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed) Depth else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "sink",
    )
    val view = LocalView.current
    Box(modifier.height(height + Depth)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .align(Alignment.BottomCenter)
                .background(edge, CircleShape),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset { IntOffset(0, sink.roundToPx()) }
                .clip(CircleShape)
                .background(face)
                .clickable(interactionSource = interaction, indication = null) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = textColor, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

/** Flat outline pill for the secondary, discouraged action. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .border(1.dp, UnloopColors.Border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) UnloopColors.TextSoft else UnloopColors.Faint,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

/** Card with a coloured NeoPOP edge along the bottom. */
@Composable
fun PopCard(
    modifier: Modifier = Modifier,
    edge: Color = UnloopColors.SurfaceEdge,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = Depth)
                .background(edge, shape),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Depth)
                .clip(shape)
                .background(UnloopColors.Surface)
                .border(1.dp, UnloopColors.Border, shape)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            content = content,
        )
    }
}

/** Small uppercase monospace label, e.g. "SCREEN TIME TODAY". */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = UnloopColors.TextSoft,
    size: TextUnit = 11.sp,
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = size,
        letterSpacing = tracking(text.uppercase(), 2.5.sp),
    )
}

@Composable
fun PauseBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(UnloopColors.Orange)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            stringResource(R.string.pause_badge).uppercase(),
            color = UnloopColors.Ink,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = tracking(stringResource(R.string.pause_badge).uppercase(), 1.5.sp),
        )
    }
}

/** Selectable pill: lime when chosen, outlined otherwise. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (selected) UnloopColors.Lime else UnloopColors.Surface)
            .border(1.dp, if (selected) UnloopColors.Lime else UnloopColors.Border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) UnloopColors.Ink else UnloopColors.Text,
            fontFamily = Display,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

/** On/off switch in the NeoPOP style: lime track with a dark knob when on. Display only; the row handles taps. */
@Composable
fun UnloopSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(if (on) 22.dp else 0.dp, label = "knob")
    Box(
        modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(CircleShape)
            .background(if (on) UnloopColors.Lime else UnloopColors.Border)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.roundToPx(), 0) }
                .size(22.dp)
                .clip(CircleShape)
                .background(if (on) UnloopColors.Ink else UnloopColors.Muted),
        )
    }
}
