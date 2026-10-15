package com.unloop.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.unloop.launcher.ui.theme.UnloopColors

/** Growth stage for a streak: seed, sprout, young plant, bush, flowering, tree. */
fun plantStage(streak: Int): Int = when {
    streak <= 0 -> 0
    streak <= 2 -> 1
    streak <= 6 -> 2
    streak <= 13 -> 3
    streak <= 20 -> 4
    else -> 5
}

/**
 * A small plant that grows with the streak. [wilting] (over the limit today) greys it out and
 * droops the leaves, as a gentle signal rather than a punishment.
 */
@Composable
fun Plant(streak: Int, wilting: Boolean, modifier: Modifier = Modifier) {
    val stage = plantStage(streak)
    val wilt by animateFloatAsState(if (wilting) 1f else 0f, tween(600), label = "wilt")
    Canvas(modifier) { drawPlant(stage, wilt) }
}

/**
 * Draws the plant for [stage] (0 seed … 5 tree) filling this draw scope. [wilt] 0..1 fades it to
 * grey and droops the leaves. Shared by the home screen and the lock-screen wallpaper.
 */
fun DrawScope.drawPlant(stage: Int, wilt: Float) {
    val leafColor = lerp(UnloopColors.Lime, UnloopColors.Muted, wilt)
    val stemColor = lerp(UnloopColors.LimeEdge, UnloopColors.Faint, wilt)
    val flowerColor = lerp(UnloopColors.Orange, UnloopColors.Muted, wilt)
    run {
        val w = size.width
        val h = size.height
        val ground = h * 0.92f
        val cx = w / 2

        // Soil.
        drawLine(UnloopColors.Border, Offset(w * 0.18f, ground), Offset(w * 0.82f, ground), strokeWidth = h * 0.05f, cap = StrokeCap.Round)

        if (stage == 0) {
            // A seed with the first hint of a shoot, so day 0 still reads as "something to grow".
            drawOval(stemColor, topLeft = Offset(cx - w * 0.14f, ground - h * 0.16f), size = Size(w * 0.28f, h * 0.15f))
            drawLine(leafColor, Offset(cx, ground - h * 0.14f), Offset(cx, ground - h * 0.3f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
            leaf(Offset(cx, ground - h * 0.3f), w * 0.16f, -40f + wilt * 70f, leafColor)
            return@run
        }

        val stemTop = ground - h * listOf(0f, 0.3f, 0.48f, 0.62f, 0.7f, 0.78f)[stage]
        drawLine(stemColor, Offset(cx, ground), Offset(cx, stemTop), strokeWidth = w * 0.05f, cap = StrokeCap.Round)

        if (stage == 5) {
            // Tree: a full canopy with a few blossoms.
            val r = w * 0.2f
            listOf(Offset(cx, stemTop - r * 0.4f), Offset(cx - r * 0.9f, stemTop + r * 0.2f), Offset(cx + r * 0.9f, stemTop + r * 0.2f))
                .forEach { drawCircle(leafColor, radius = r, center = it) }
            listOf(Offset(cx - r * 0.5f, stemTop - r * 0.6f), Offset(cx + r * 0.7f, stemTop), Offset(cx - r * 1.1f, stemTop + r * 0.3f))
                .forEach { drawCircle(flowerColor, radius = w * 0.045f, center = it) }
            return@run
        }

        // Pairs of leaves up the stem; wilting tips them downward.
        val pairs = listOf(0, 1, 1, 2, 3)[stage]
        val leafLength = w * (0.2f + stage * 0.035f)
        for (i in 0 until pairs) {
            val y = ground - (ground - stemTop) * (0.35f + 0.5f * i / maxOf(pairs, 1))
            val lift = -35f + wilt * 70f
            leaf(Offset(cx, y), leafLength, 180f - lift, leafColor)
            leaf(Offset(cx, y), leafLength, lift, leafColor)
        }
        // Top sprout leaves.
        leaf(Offset(cx, stemTop), leafLength * 0.8f, -60f + wilt * 90f, leafColor)
        leaf(Offset(cx, stemTop), leafLength * 0.8f, -120f - wilt * 90f, leafColor)

        if (stage == 4) {
            val r = w * 0.065f
            for (k in 0 until 5) {
                rotate(k * 72f, pivot = Offset(cx, stemTop)) {
                    drawCircle(flowerColor, radius = r, center = Offset(cx, stemTop - r * 1.2f))
                }
            }
            drawCircle(lerp(UnloopColors.Paper, UnloopColors.Muted, wilt), radius = r * 0.8f, center = Offset(cx, stemTop))
        }
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
