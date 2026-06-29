package com.nofuzznotes.ui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.text.CoreTextRules
import com.nofuzznotes.domain.service.EditorMode

private const val EditorTag = "editor-text"

data class TextEditEvent(
    val before: String,
    val after: String,
    val selectionBefore: TextSelection,
    val selectionAfter: TextSelection,
)

@Composable
fun EditorTextSurface(
    content: String,
    mode: EditorMode,
    onTextEdit: (TextEditEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Switch on domain mode because snapshots and saved drafts share the same read-only selectable surface.
    if (mode == EditorMode.Edit) {
        EditableEditorTextSurface(content, onTextEdit, modifier)
    } else {
        ReadOnlyEditorTextSurface(content, modifier)
    }
}

@Composable
private fun EditableEditorTextSurface(content: String, onTextEdit: (TextEditEvent) -> Unit, modifier: Modifier) {
    // Keep TextFieldValue local because Compose owns platform selection while persistence owns text content.
    var value by remember { mutableStateOf(TextFieldValue(content, TextRange(content.length))) }
    LaunchedEffect(content) {
        if (content != value.text) value = TextFieldValue(content, TextRange(content.length))
    }
    BasicTextField(
        value = value,
        onValueChange = { incoming ->
            val mapped = EditorTextEventMapper.map(value, incoming)
            value = TextFieldValue(mapped.after, TextRange(mapped.selectionAfter.start, mapped.selectionAfter.end))
            onTextEdit(mapped)
        },
        modifier = modifier.fillMaxSize().testTag(EditorTag),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Text,
        ),
    )
}

@Composable
private fun ReadOnlyEditorTextSurface(content: String, modifier: Modifier) {
    // Wrap text in SelectionContainer because view modes must allow copy without accepting edits.
    SelectionContainer(modifier = modifier.fillMaxSize().testTag(EditorTag)) {
        Text(content, style = MaterialTheme.typography.bodyLarge)
    }
}

object EditorTextEventMapper {
    // Sanitize before event delivery because persistence must never receive raw platform paste text.
    fun map(previous: TextFieldValue, incoming: TextFieldValue): TextEditEvent {
        val sanitizedText = CoreTextRules.sanitizePaste(incoming.text)
        return TextEditEvent(
            before = previous.text,
            after = sanitizedText,
            selectionBefore = sanitizeSelection(previous.selection, previous.text.length),
            selectionAfter = sanitizeSelection(incoming.selection, sanitizedText.length),
        )
    }

    // Clamp selection because sanitized text length is the only valid editor invariant after mapping.
    private fun sanitizeSelection(selection: TextRange, contentLength: Int): TextSelection {
        assert(contentLength >= 0)
        val start = selection.start.coerceIn(0, contentLength)
        val end = selection.end.coerceIn(0, contentLength)
        return if (start <= end) TextSelection(start, end) else TextSelection(end, start)
    }
}
