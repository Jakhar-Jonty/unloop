package com.unloop.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances

/** [title] may be blank; the UI shows a localized placeholder. */
data class NextEvent(val title: String, val begin: Long)

/** Reads the next timed calendar event in the coming 24 hours. Needs READ_CALENDAR. */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun next(): NextEvent? {
        if (!hasPermission()) return null
        val now = System.currentTimeMillis()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + 24 * 60 * 60_000L)
        }.build()
        return context.contentResolver.query(
            uri,
            arrayOf(Instances.TITLE, Instances.BEGIN),
            // Skip all-day events (birthdays, holidays): they are not "next up".
            "${Instances.ALL_DAY} = 0 AND ${Instances.END} > ?",
            arrayOf(now.toString()),
            "${Instances.BEGIN} ASC",
        )?.use { c ->
            if (!c.moveToFirst()) return null
            NextEvent(title = c.getString(0).orEmpty().trim(), begin = c.getLong(1))
        }
    }
}
