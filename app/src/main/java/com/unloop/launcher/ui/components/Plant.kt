package com.unloop.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.unloop.launcher.ui.theme.UnloopColors

/** Leaves the plant starts each day with. */
const val MAX_LEAVES = 7

/** After the first leaf (lost when the limit is crossed), one more falls every this many minutes. */
const val LEAF_MINUTES = 15

/** Days in a row with every leaf kept before the plant flowers. */
const val FLOWER_DAYS = 7

/**
 * Leaves left today. The plant starts with all [MAX_LEAVES]; crossing the daily limit costs one,
 * then another falls every [LEAF_MINUTES] minutes over it, down to a bare stem.
 */
fun leavesLeft(usedMs: Long, limitMinutes: Int): Int {
    val overMs = usedMs - limitMinutes * 60_000L
    if (overMs < 0) return MAX_LEAVES
    return (MAX_LEAVES - 1 - (overMs / (LEAF_MINUTES * 60_000L)).toInt()).coerceAtLeast(0)
}

/** Milliseconds until the next leaf falls, or null once the plant is bare. */
fun nextLeafInMs(usedMs: Long, limitMinutes: Int): Long? {
    val limitMs = limitMinutes * 60_000L
    if (leavesLeft(usedMs, limitMinutes) == 0) return null
    if (usedMs < limitMs) return limitMs - usedMs
    val step = LEAF_MINUTES * 60_000L
    return step - (usedMs - limitMs) % step
}

/**
 * A plant to protect. It starts the day full; leaves fall once screen time goes over the limit
 * and lie on the soil. Keeping every leaf for [FLOWER_DAYS] days in a row makes it flower.
 */
@Composable
fun Plant(leaves: Int, flowering: Boolean, modifier: Modifier = Modifier, growing: Boolean = false) {
    Canvas(modifier) { drawPlant(leaves, flowering, growing) }
}

/** Draws the plant filling this draw scope. Shared by the home screen and the lock-screen wallpaper. */
/** [growing]: leaves not there yet are simply missing (no fallen leaves, no droop), for grow-in animations. */
fun DrawScope.drawPlant(leaves: Int, flowering: Boolean, growing: Boolean = false) {
    val kept = leaves.coerceIn(0, MAX_LEAVES)
    val lost = MAX_LEAVES - kept
    // The more it has lost, the more the remaining leaves droop and fade.
    val wilt = if (growing) 0f else lost / MAX_LEAVES.toFloat()
    val leafColor = lerp(UnloopColors.Lime, UnloopColors.Muted, wilt * 0.6f)
    val stemColor = if (kept == 0) UnloopColors.Faint else lerp(UnloopColors.LimeEdge, UnloopColors.Faint, wilt)

    val w = size.width
    val h = size.height
    val ground = h * 0.9f
    val cx = w / 2
    val top = h * 0.14f
    val leafLength = w * 0.3f

    // Soil.
    drawLine(UnloopColors.Border, Offset(w * 0.06f, ground), Offset(w * 0.94f, ground), strokeWidth = h * 0.045f, cap = StrokeCap.Round)
    // Stem.
    drawLine(stemColor, Offset(cx, ground), Offset(cx, top), strokeWidth = w * 0.05f, cap = StrokeCap.Round)

    // Seven slots, bottom first: three pairs up the stem and one on top. Lower leaves fall first.
    val droop = wilt * 45f
    val slots = listOf(
        Triple(0.72f, 215f - droop, leafLength),
        Triple(0.72f, -35f + droop, leafLength),
        Triple(0.52f, 215f - droop, leafLength),
        Triple(0.52f, -35f + droop, leafLength),
        Triple(0.32f, 215f - droop, leafLength * 0.9f),
        Triple(0.32f, -35f + droop, leafLength * 0.9f),
        Triple(0f, -90f + droop * 0.5f, leafLength * 0.75f),
    )
    slots.forEachIndexed { i, (at, angle, length) ->
        if (i < lost) return@forEachIndexed
        val y = top + (ground - top) * at
        leaf(Offset(cx, y), length, angle, leafColor)
    }

    // Fallen leaves lying on the soil, so the loss stays visible for the day.
    for (i in 0 until if (growing) 0 else lost) {
        val x = w * (0.12f + 0.11f * i)
        leaf(Offset(x, ground - h * 0.03f), w * 0.16f, if (i % 2 == 0) -8f else 188f, UnloopColors.Muted)
    }

    if (flowering && kept == MAX_LEAVES) {
        val r = w * 0.06f
        val centre = Offset(cx, top - r * 0.6f)
        for (k in 0 until 5) {
            rotate(k * 72f, pivot = centre) {
                drawCircle(UnloopColors.Orange, radius = r, center = Offset(centre.x, centre.y - r * 1.2f))
            }
        }
        drawCircle(UnloopColors.Paper, radius = r * 0.8f, center = centre)
    }
}

/** Leaf from [base], pointing at [angle] degrees (0 = right, -90 = up). */
private fun DrawScope.leaf(base: Offset, length: Float, angle: Float, color: Color) {
    val path = Path().apply {
        moveTo(0f, 0f)
        quadraticTo(length * 0.5f, -length * 0.38f, length, 0f)
        quadraticTo(length * 0.5f, length * 0.38f, 0f, 0f)
        close()
    }
    translate(base.x, base.y) {
        rotate(angle, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}
