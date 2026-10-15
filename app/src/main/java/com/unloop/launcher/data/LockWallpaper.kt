package com.unloop.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import com.unloop.launcher.ui.components.drawPlant
import com.unloop.launcher.ui.components.plantStage
import com.unloop.launcher.R
import com.unloop.launcher.ui.formatDuration
import com.unloop.launcher.ui.formatLimit

/**
 * Draws Unloop's lock-screen wallpaper: today's screen time against the limit, unlocks, the
 * streak and a quote, in the app's black-and-lime style. Content sits in the middle band of the
 * screen, clear of the system lock clock at the top and the shortcuts at the bottom.
 */
object LockWallpaper {
    private const val BG = 0xFF0A0A0A.toInt()
    private const val TEXT = 0xFFF2F0EA.toInt()
    private const val TEXT_SOFT = 0xFFBDBBB4.toInt()
    private const val MUTED = 0xFF7C7B77.toInt()
    private const val SURFACE = 0xFF141414.toInt()
    private const val BORDER = 0xFF262626.toInt()
    private const val LIME = 0xFFD4F25A.toInt()
    private const val ORANGE = 0xFFF07A4A.toInt()

    /** Redraws and applies the wallpaper if the user turned it on. Blocking: call off the main thread. */
    fun update(context: Context, force: Boolean = false) {
        val prefs = Prefs(context)
        if (!prefs.lockWallpaper) return
        val now = System.currentTimeMillis()
        // Setting a wallpaper is not free; a couple of minutes between redraws is plenty.
        if (!force && now - prefs.lastWallpaperAt < 2 * 60_000L) return
        val usageRepo = UsageRepository(context)
        val usage = if (usageRepo.hasAccess()) usageRepo.today() else null
        val quotes = context.resources.getStringArray(R.array.quotes)
        val bitmap = render(context, usage, prefs.dailyLimitMinutes, prefs.lastStreak, quotes.random())
        runCatching {
            context.getSystemService(WallpaperManager::class.java)
                .setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            prefs.lastWallpaperAt = now
        }
        bitmap.recycle()
    }

    /** Hands the lock screen back to the system's default wallpaper. */
    fun clear(context: Context) {
        runCatching { context.getSystemService(WallpaperManager::class.java).clear(WallpaperManager.FLAG_LOCK) }
        Prefs(context).lastWallpaperAt = 0L
    }

    private fun render(context: Context, usage: DayUsage?, limitMinutes: Int, streak: Int, quote: String): Bitmap {
        val res = context.resources
        val (w, h) = screenSize(context)
        val dm = res.displayMetrics
        fun dp(v: Float) = v * dm.density
        fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)

        val display = ResourcesCompat.getFont(context, R.font.bricolage)
        val mono = ResourcesCompat.getFont(context, R.font.jbmono)
        fun displayPaint(size: Float, weight: Int, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = display
            fontVariationSettings = "'wght' $weight, 'opsz' ${if (weight >= 800) 96 else 24}"
            textSize = size
            this.color = color
        }
        fun labelPaint(color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            fontVariationSettings = "'wght' 500"
            textSize = sp(12f)
            letterSpacing = 0.2f
            this.color = color
        }

        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)

        // Samsung puts its lock clock at the top, leaving the middle free. Most other phones
        // (Pixel and AOSP-like) show a big clock in the centre, so use a compact card below it.
        val compact = !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val margin = dp(24f)
        val pad = dp(if (compact) 20f else 24f)
        val cardLeft = margin
        val cardRight = w - margin
        val inner = cardRight - cardLeft - pad * 2
        val edge = dp(6f)
        val radius = dp(28f)

        if (usage == null) {
            drawQuote(canvas, quote, displayPaint(sp(22f), 600, MUTED), margin, h * 0.62f, w - margin * 2)
            return bitmap
        }

        val limitMs = limitMinutes * 60_000L
        val over = usage.totalMs >= limitMs
        val accent = if (over) ORANGE else LIME
        val label = labelPaint(TEXT_SOFT)
        val big = displayPaint(sp(if (compact) 40f else 60f), 800, TEXT).apply { letterSpacing = -0.03f }
        val soft = displayPaint(sp(if (compact) 15f else 17f), 600, TEXT_SOFT)
        val streakPaint = labelPaint(accent)
        val plantW = dp(if (compact) 34f else 44f)
        val plantH = dp(if (compact) 40f else 52f)
        val barH = dp(8f)

        // Card height, measured from its rows.
        val labelH = -label.fontMetrics.ascent
        val bigH = -big.fontMetrics.ascent
        val cardH = if (compact) {
            pad + labelH + dp(12f) + bigH + dp(16f) + barH + pad
        } else {
            pad + labelH + dp(14f) + bigH + dp(10f) + -soft.fontMetrics.ascent + dp(18f) + barH + dp(20f) + plantH + pad
        }
        val cardTop = if (compact) h * 0.835f - cardH - edge else h * 0.44f
        val cardBottom = cardTop + cardH

        // NeoPOP card: accent edge underneath, dark face, hairline border.
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(cardLeft, cardTop + edge, cardRight, cardBottom + edge), radius, radius, fill.apply { color = accent })
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), radius, radius, fill.apply { color = SURFACE })
        canvas.drawRoundRect(
            RectF(cardLeft, cardTop, cardRight, cardBottom), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f); color = BORDER },
        )

        val x = cardLeft + pad
        var y = cardTop + pad + labelH
        // Row 1: label, unlocks on the right.
        canvas.drawText(res.getString(R.string.screen_time_today).uppercase(), x, y, label)
        val unlocks = res.getQuantityString(R.plurals.unlocks, usage.unlocks, usage.unlocks).uppercase()
        canvas.drawText(unlocks, x + inner - label.measureText(unlocks), y, label)

        // Row 2: the number, and the limit beside it (compact) or under it.
        y += dp(if (compact) 12f else 14f) + bigH
        val duration = formatDuration(res, usage.totalMs)
        canvas.drawText(duration, x, y, big)
        val limitText = res.getString(if (over) R.string.over_limit else R.string.of_limit, formatLimit(res, limitMinutes))
        if (compact) {
            canvas.drawText(limitText, x + big.measureText(duration) + dp(10f), y, soft)
        } else {
            y += dp(10f) + -soft.fontMetrics.ascent
            canvas.drawText(limitText, x, y, soft)
        }

        // Row 3: progress against the daily limit.
        y += dp(if (compact) 16f else 18f)
        canvas.drawRoundRect(RectF(x, y, x + inner, y + barH), barH / 2, barH / 2, fill.apply { color = BORDER })
        val fraction = (usage.totalMs.toFloat() / limitMs).coerceIn(0f, 1f)
        if (fraction > 0f) {
            canvas.drawRoundRect(RectF(x, y, x + inner * fraction.coerceAtLeast(0.03f), y + barH), barH / 2, barH / 2, fill.apply { color = accent })
        }

        if (!compact) {
            // Row 4: plant and streak.
            y += barH + dp(20f)
            drawPlantBitmap(context, canvas, streak, over, x - dp(6f), y, plantW, plantH)
            val streakText = res.getQuantityString(R.plurals.streak_label, streak, streak).uppercase()
            canvas.drawText(streakText, x + plantW + dp(8f), y + plantH / 2 + labelH / 2, streakPaint)

            // Quote under the card.
            drawQuote(canvas, quote, displayPaint(sp(19f), 600, MUTED), margin + dp(4f), cardBottom + edge + dp(28f), w - margin * 2)
        }
        return bitmap
    }

    private fun drawQuote(canvas: Canvas, quote: String, paint: TextPaint, x: Float, y: Float, width: Float) {
        val text = "“$quote”"
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setLineSpacing(0f, 1.15f)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    /** The same plant as on the home screen, drawn with Compose into a small bitmap. */
    private fun drawPlantBitmap(context: Context, canvas: Canvas, streak: Int, wilting: Boolean, x: Float, y: Float, w: Float, h: Float) {
        val image = ImageBitmap(w.toInt(), h.toInt())
        CanvasDrawScope().draw(Density(context), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w, h)) {
            drawPlant(plantStage(streak), if (wilting) 1f else 0f)
        }
        canvas.drawBitmap(image.asAndroidBitmap(), x, y, null)
    }

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }
}
