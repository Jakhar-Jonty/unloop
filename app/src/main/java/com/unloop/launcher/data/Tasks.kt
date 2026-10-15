package com.unloop.launcher.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** A to-do item. [note] is optional free text attached to the task. */
data class Task(
    val id: Long,
    val title: String,
    val note: String = "",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long = 0L,
)

/** A free-form note. The first line acts as its title. */
data class Note(val id: Long, val text: String, val updatedAt: Long = System.currentTimeMillis())

/** Tasks and notes, stored on the phone only as JSON in private preferences. */
class TaskStore(context: Context) {
    private val sp = context.getSharedPreferences("unloop_tasks", Context.MODE_PRIVATE)

    fun tasks(): List<Task> = read("tasks") { o ->
        Task(
            id = o.getLong("id"),
            title = o.getString("title"),
            note = o.optString("note"),
            done = o.optBoolean("done"),
            createdAt = o.optLong("createdAt"),
            doneAt = o.optLong("doneAt"),
        )
    }

    fun saveTasks(tasks: List<Task>) = write("tasks", tasks) { t ->
        JSONObject()
            .put("id", t.id)
            .put("title", t.title)
            .put("note", t.note)
            .put("done", t.done)
            .put("createdAt", t.createdAt)
            .put("doneAt", t.doneAt)
    }

    fun notes(): List<Note> = read("notes") { o -> Note(o.getLong("id"), o.getString("text"), o.optLong("updatedAt")) }

    fun saveNotes(notes: List<Note>) = write("notes", notes) { n ->
        JSONObject().put("id", n.id).put("text", n.text).put("updatedAt", n.updatedAt)
    }

    private fun <T> read(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = sp.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { parse(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun <T> write(key: String, items: List<T>, toJson: (T) -> JSONObject) {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        sp.edit { putString(key, array.toString()) }
    }
}
