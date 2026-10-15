package com.unloop.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.R
import com.unloop.launcher.data.Note
import com.unloop.launcher.data.Task
import com.unloop.launcher.ui.components.GhostButton
import com.unloop.launcher.ui.components.Label
import com.unloop.launcher.ui.components.PopButton
import com.unloop.launcher.ui.theme.Display
import com.unloop.launcher.ui.theme.UnloopColors

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

/** Round check that fills lime and pops a tick when done. */
@Composable
fun TaskCheck(done: Boolean, onClick: () -> Unit, size: Int = 26) {
    val fill by animateColorAsState(if (done) UnloopColors.Lime else UnloopColors.Bg, tween(180), label = "fill")
    val ring by animateColorAsState(if (done) UnloopColors.Lime else UnloopColors.Muted, tween(180), label = "ring")
    val tick by animateFloatAsState(
        if (done) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "tick",
    )
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (tick > 0.01f) {
            Text(
                "✓",
                color = UnloopColors.Ink,
                fontFamily = Display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size * 0.55f).sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = tick
                    scaleY = tick
                    alpha = tick.coerceIn(0f, 1f)
                },
            )
        }
    }
}

/** Edit a task's title and note, or delete it. */
@Composable
fun TaskSheet(task: Task, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDelete: () -> Unit) {
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

/** Full-screen note editor. Saves as you type; back returns to wherever it was opened from. */
@Composable
fun NoteEditor(note: Note, onChange: (String) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
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
                "←  " + stringResource(R.string.today),
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
            .heightIn(min = if (singleLine) 52.dp else 120.dp)
            .background(container, shape)
            .border(1.dp, UnloopColors.Border, shape),
        decorationBox = { inner ->
            Box(
                Modifier.padding(horizontal = 20.dp, vertical = if (singleLine) 0.dp else 16.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (singleLine) Spacer(Modifier.heightIn(min = 52.dp))
                if (value.isEmpty()) {
                    Text(hint, color = UnloopColors.Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
                inner()
            }
        },
    )
}
