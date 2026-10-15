package com.unloop.launcher.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.R
import com.unloop.launcher.UiState
import com.unloop.launcher.data.Note
import com.unloop.launcher.data.Task
import com.unloop.launcher.ui.components.Chip
import com.unloop.launcher.ui.components.GhostButton
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.Mono
import com.unloop.launcher.ui.theme.UnloopColors
import com.unloop.launcher.ui.theme.tracking

/** Callbacks for everything the to-do list and notes can do. */
class TaskActions(
    val add: (String) -> Unit,
    val toggle: (Long) -> Unit,
    val update: (id: Long, title: String, note: String) -> Unit,
    val delete: (Long) -> Unit,
    val clearDone: () -> Unit,
    val newNote: () -> Long,
    val updateNote: (Long, String) -> Unit,
    val deleteNote: (Long) -> Unit,
    val dropEmptyNotes: () -> Unit,
)

/** Full to-do list and notes, opened from the home card or by swiping left on home. */
@Composable
fun TasksScreen(state: UiState, actions: TaskActions, onBack: () -> Unit, initialNotes: Boolean = false, initialNoteId: Long = 0L) {
    var notesTab by rememberSaveable { mutableStateOf(initialNotes) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var editingNote by rememberSaveable { mutableLongStateOf(initialNoteId) }

    if (editingNote != 0L) {
        val note = state.notes.firstOrNull { it.id == editingNote }
        if (note == null) {
            editingNote = 0L
        } else {
            NoteEditor(note, onChange = { actions.updateNote(note.id, it) }, onDelete = {
                actions.deleteNote(note.id)
                editingNote = 0L
            }, onClose = {
                actions.dropEmptyNotes()
                editingNote = 0L
            })
            return
        }
    }

    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        val title = stringResource(if (notesTab) R.string.notes_tab else R.string.todo)
        Text(title, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, letterSpacing = tracking(title, (-1.5).sp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(stringResource(R.string.todo), selected = !notesTab, onClick = { notesTab = false })
            Chip(stringResource(R.string.notes_tab), selected = notesTab, onClick = { notesTab = true })
        }
        Spacer(Modifier.height(20.dp))

        if (!notesTab) {
            var draft by rememberSaveable { mutableStateOf("") }
            UnloopTextField(
                value = draft,
                onValueChange = { draft = it },
                hint = stringResource(R.string.add_task_hint),
                onDone = {
                    actions.add(draft)
                    draft = ""
                },
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (state.openTasks.isEmpty()) {
                    item { Empty(stringResource(R.string.nothing_to_do)) }
                }
                items(state.openTasks, key = { it.id }) { task ->
                    TaskRow(task, onToggle = { actions.toggle(task.id) }, onOpen = { editingTask = task })
                }
                if (state.doneTasks.isNotEmpty()) {
                    item(key = "done_header") {
                        Row(Modifier.padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Label("${stringResource(R.string.done_section)} · ${state.doneTasks.size}", modifier = Modifier.weight(1f))
                            Label(
                                stringResource(R.string.clear_done),
                                color = UnloopColors.Orange,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = actions.clearDone)
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                            )
                        }
                    }
                    items(state.doneTasks, key = { it.id }) { task ->
                        TaskRow(task, onToggle = { actions.toggle(task.id) }, onOpen = { editingTask = task })
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Text(
                        stringResource(R.string.new_note),
                        color = UnloopColors.Lime,
                        fontFamily = Display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingNote = actions.newNote() }
                            .padding(vertical = 14.dp),
                    )
                }
                if (state.notes.isEmpty()) item { Empty(stringResource(R.string.no_notes)) }
                items(state.notes.sortedByDescending { it.updatedAt }, key = { it.id }) { note ->
                    NoteRow(note, onOpen = { editingNote = note.id })
                }
            }
        }
    }

    editingTask?.let { task ->
        TaskSheet(
            task = task,
            onDismiss = { editingTask = null },
            onSave = { title, note ->
                actions.update(task.id, title, note)
                editingTask = null
            },
            onDelete = {
                actions.delete(task.id)
                editingTask = null
            },
        )
    }
}

/** Round check: lime with a tick when done, outlined when open. */
@Composable
fun TaskCheck(done: Boolean, onClick: () -> Unit, size: Int = 26) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (done) UnloopColors.Lime else UnloopColors.Bg)
            .border(2.dp, if (done) UnloopColors.Lime else UnloopColors.Muted, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Text("✓", color = UnloopColors.Ink, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = (size * 0.55f).sp)
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: () -> Unit, onOpen: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCheck(task.done, onToggle)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = if (task.done) UnloopColors.Muted else UnloopColors.Text,
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.note.isNotBlank()) {
                    Text(
                        task.note,
                        color = UnloopColors.Muted,
                        fontFamily = Display,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(UnloopColors.Divider))
    }
}

@Composable
private fun NoteRow(note: Note, onOpen: () -> Unit) {
    val lines = note.text.trim().lines()
    Column {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 14.dp),
        ) {
            Text(
                lines.firstOrNull()?.ifBlank { null } ?: stringResource(R.string.empty_note),
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val rest = lines.drop(1).joinToString(" ").trim()
            if (rest.isNotEmpty()) {
                Text(rest, color = UnloopColors.Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                DateUtils.getRelativeTimeSpanString(note.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).let { if (System.currentTimeMillis() - note.updatedAt < 60_000) stringResource(R.string.just_now) else it.toString() },
                color = UnloopColors.Faint,
                fontFamily = Mono,
                fontSize = 11.sp,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(UnloopColors.Divider))
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = UnloopColors.Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 18.dp))
}

/** Edit a task's title and note, or delete it. */
@Composable
private fun TaskSheet(task: Task, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDelete: () -> Unit) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var note by rememberSaveable(task.id) { mutableStateOf(task.note) }
    UnloopSheet(onDismiss) {
        UnloopTextField(value = title, onValueChange = { title = it }, hint = stringResource(R.string.add_task_hint), onDone = {})
        Spacer(Modifier.height(12.dp))
        UnloopTextField(
            value = note,
            onValueChange = { note = it },
            hint = stringResource(R.string.task_note_hint),
            onDone = {},
            singleLine = false,
        )
        Spacer(Modifier.height(20.dp))
        PopButton(stringResource(R.string.save), onClick = { onSave(title, note) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.delete), onClick = onDelete)
    }
}

/** Full-screen note editor. Saves as you type. */
@Composable
private fun NoteEditor(note: Note, onChange: (String) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var text by remember(note.id) { mutableStateOf(note.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(note.id) { if (note.text.isEmpty()) focus.requestFocus() }
    Column(
        Modifier
            .fillMaxSize()
            .background(UnloopColors.Bg)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
    ) {
        Row(Modifier.padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(
                "←  " + stringResource(R.string.notes_tab),
                color = UnloopColors.Lime,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClose)
                    .padding(vertical = 10.dp),
            )
            Label(
                stringResource(R.string.delete),
                color = UnloopColors.Orange,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .padding(10.dp),
            )
        }
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            textStyle = TextStyle(color = UnloopColors.Text, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
            cursorBrush = SolidColor(UnloopColors.Lime),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.note_hint), color = UnloopColors.Faint, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    }
                    inner()
                }
            },
        )
    }
}

/** Rounded text field in the app's style. Multi-line when [singleLine] is false. */
@Composable
fun UnloopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    container: Color = UnloopColors.Surface,
) {
    val shape = RoundedCornerShape(if (singleLine) 28.dp else 20.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = UnloopColors.Text, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        cursorBrush = SolidColor(UnloopColors.Lime),
        keyboardOptions = KeyboardOptions(
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            capitalization = KeyboardCapitalization.Sentences,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 56.dp else 120.dp)
            .background(container, shape)
            .border(1.dp, UnloopColors.Border, shape),
        decorationBox = { inner ->
            Box(
                Modifier.padding(horizontal = 22.dp, vertical = if (singleLine) 0.dp else 16.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (singleLine) Spacer(Modifier.heightIn(min = 56.dp))
                if (value.isEmpty()) {
                    Text(hint, color = UnloopColors.Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
                inner()
            }
        },
    )
}
