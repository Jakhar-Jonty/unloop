package com.unloop.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
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
        val left = dp(32f)
        val contentWidth = w - left * 2
        // Samsung puts its lock clock at the top, leaving the middle free. Most other phones
        // (Pixel and AOSP-like) show a big clock in the centre, so use a compact band below it.
        val compact = !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val bigSize = if (compact) 40f else 64f
        var y = h * if (compact) 0.685f else 0.50f

        if (usage != null) {
            val limitMs = limitMinutes * 60_000L
            val over = usage.totalMs >= limitMs
            val accent = if (over) ORANGE else LIME

            canvas.drawText(res.getString(R.string.screen_time_today).uppercase(), left, y, labelPaint(TEXT_SOFT))
            y += sp(bigSize)
            val big = displayPaint(sp(bigSize), 800, TEXT).apply { letterSpacing = -0.03f }
            val duration = formatDuration(res, usage.totalMs)
            canvas.drawText(duration, left, y, big)
            val limitText = res.getString(if (over) R.string.over_limit else R.string.of_limit, formatLimit(res, limitMinutes))
            val limitPaint = displayPaint(sp(17f), 600, TEXT_SOFT)
            if (compact) {
                // Same line as the number, to save height.
                canvas.drawText(limitText, left + big.measureText(duration) + dp(12f), y, limitPaint)
            } else {
                y += sp(30f)
                canvas.drawText(limitText, left, y, limitPaint)
            }

            // Progress against the daily limit.
            y += dp(22f)
            val barH = dp(8f)
            val track = RectF(left, y, left + contentWidth, y + barH)
            canvas.drawRoundRect(track, barH / 2, barH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BORDER })
            val fraction = (usage.totalMs.toFloat() / limitMs).coerceIn(0f, 1f)
            if (fraction > 0f) {
                val fill = RectF(left, y, left + contentWidth * fraction.coerceAtLeast(0.03f), y + barH)
                canvas.drawRoundRect(fill, barH / 2, barH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
            }

            y += barH + dp(if (compact) 24f else 32f)
            val stats = listOf(
                res.getQuantityString(R.plurals.unlocks, usage.unlocks, usage.unlocks),
                res.getQuantityString(R.plurals.streak_label, streak, streak),
            ).joinToString("  ·  ").uppercase()
            canvas.drawText(stats, left, y, labelPaint(LIME))
            y += dp(56f)
        } else {
            y = h * 0.62f
        }

        if (compact && usage != null) return bitmap

        // Quote, wrapped to the content width.
        val quotePaint = displayPaint(sp(20f), 600, MUTED)
        val layout = StaticLayout.Builder.obtain("“$quote”", 0, quote.length + 2, quotePaint, contentWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(left, y.coerceAtMost(h * 0.85f - layout.height))
        layout.draw(canvas)
        canvas.restore()
        return bitmap
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
