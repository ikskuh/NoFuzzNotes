package com.nofuzznotes.presentation.editor

import androidx.lifecycle.ViewModel
import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.domain.service.DisplayedContent
import com.nofuzznotes.domain.service.EditorMode
import com.nofuzznotes.domain.service.ExportService
import com.nofuzznotes.domain.service.HistoryService
import com.nofuzznotes.domain.service.NoteLifecycleService
import com.nofuzznotes.domain.service.TextEdit
import com.nofuzznotes.domain.service.TrashService
import com.nofuzznotes.domain.service.UndoRedoService
import com.nofuzznotes.presentation.common.AppRoute
import com.nofuzznotes.presentation.common.EffectBuffer
import com.nofuzznotes.presentation.common.PresentationEffect
import com.nofuzznotes.presentation.common.PromptKind
import com.nofuzznotes.presentation.common.PromptMessages
import com.nofuzznotes.presentation.common.PromptState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class EditorState(
    val noteId: Long,
    val snapshotId: Long? = null,
    val content: String = "",
    val mode: EditorMode = EditorMode.Edit,
    val canEnterEdit: Boolean = false,
    val canSave: Boolean = false,
    val canCancel: Boolean = false,
    val canDelete: Boolean = false,
    val canRestore: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val canOpenHistory: Boolean = false,
    val canExport: Boolean = true,
    val canShare: Boolean = true,
    val isTrashed: Boolean = false,
    val prompt: PromptState? = null,
)

class EditorViewModel(
    private val noteId: Long,
    private val snapshotId: Long? = null,
    private val lifecycle: NoteLifecycleService,
    private val undoRedo: UndoRedoService,
    private val trash: TrashService,
    private val history: HistoryService,
    private val export: ExportService,
) : ViewModel() {
    private val effectBuffer = EffectBuffer()
    private val mutableState = MutableStateFlow(EditorState(noteId, snapshotId))

    val state: StateFlow<EditorState> = mutableState
    val effects = effectBuffer.effects

    init { open() }

    // Open draft or snapshot state through services because mode is derived from persistence.
    fun open() {
        if (snapshotId != null) {
            val result = history.openSnapshot(noteId, snapshotId)
            setContent(result.displayedContent.content, result.mode, snapshotId = snapshotId)
            return
        }
        val result = lifecycle.openNote(noteId)
        if (result.note.isTrashed()) {
            val trashResult = trash.openTrashedNote(noteId)
            setContent(trashResult.displayedContent.content, trashResult.mode, isTrashed = true)
            return
        }
        setContent(result.note.content, result.mode)
    }

    // Enter edit mode without mutating content because explicit editing is a view-state transition only.
    fun enterEditMode() {
        check(mutableState.value.mode == EditorMode.View)
        setContent(mutableState.value.content, EditorMode.Edit)
    }

    // Persist text and undo information together because user edits must survive process death.
    fun textChanged(newContent: String) {
        // Preserve the old API for tests because callers without selection still represent a cursor at text end.
        textEdited(newContent, TextSelection(newContent.length, newContent.length))
    }

    // Persist text with selection because durable undo must restore cursor state after replay.
    fun textEdited(newContent: String, selection: TextSelection) {
        val current = mutableState.value
        textEdited(current.content, newContent, TextSelection(current.content.length, current.content.length), selection)
    }

    // Persist the full editor event because undo replay needs both before and after selection state.
    fun textEdited(textBefore: String, newContent: String, selectionBefore: TextSelection, selectionAfter: TextSelection) {
        val current = mutableState.value
        check(current.mode == EditorMode.Edit) { "Text changes require edit mode" }
        assert(current.content == textBefore)
        assert(selectionBefore.start <= textBefore.length)
        assert(selectionBefore.end <= textBefore.length)
        assert(selectionAfter.start <= newContent.length)
        assert(selectionAfter.end <= newContent.length)
        if (newContent == current.content) return
        undoRedo.recordEdit(
            noteId = noteId,
            edit = TextEdit(
                operationKind = operationKindFor(textBefore, newContent),
                position = firstChangedPosition(textBefore, newContent),
                textBefore = textBefore,
                textAfter = newContent,
                cursorBefore = selectionBefore.end,
                cursorAfter = selectionAfter.end,
                selectionBefore = selectionBefore,
                selectionAfter = selectionAfter,
            ),
        )
        setContent(newContent, EditorMode.Edit)
    }

    // Save returns to view mode because the draft now matches the latest snapshot.
    fun save() {
        check(mutableState.value.mode == EditorMode.Edit)
        val result = lifecycle.saveNote(noteId)
        setContent(result.note.content, result.mode, prompt = null)
    }

    // Ask before cancel because unsaved draft content may be discarded.
    fun cancel() {
        check(mutableState.value.canCancel)
        mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.CancelEdit, PromptMessages.basic("Discard unsaved changes?"), noteId))
    }

    // Cancel after confirmation because prompts are presentation-level safety gates.
    fun confirmCancel() {
        val prompt = requirePrompt(PromptKind.CancelEdit)
        assert(prompt.targetId == noteId)
        val result = lifecycle.cancelEdit(noteId)
        setContent(result.note.content, result.mode, prompt = null)
    }

    // Undo one durable operation because the ViewModel must drive editor behavior without Compose.
    fun undo() {
        val result = undoRedo.undo(noteId, mutableState.value.mode)
        setContent(result.note.content, result.mode)
    }

    // Redo one durable operation because redo availability is part of editor state.
    fun redo() {
        val result = undoRedo.redo(noteId, mutableState.value.mode)
        setContent(result.note.content, result.mode)
    }

    // Ask before moving the note to trash because delete exits the editor.
    fun delete() {
        check(mutableState.value.canDelete)
        mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.DeleteNote, PromptMessages.DELETE_NOTE, noteId))
    }

    // Trash after confirmation and navigate away because deleted notes are not editable drafts.
    fun confirmDelete() {
        val prompt = requirePrompt(PromptKind.DeleteNote)
        assert(prompt.targetId == noteId)
        trash.trashNote(noteId)
        effectBuffer.emit(PresentationEffect.Navigate(AppRoute.NoteList))
    }

    // Ask before restore because snapshot rollback rewrites the draft.
    fun restoreSnapshot() {
        val id = mutableState.value.snapshotId ?: error("Snapshot restore requires snapshot mode")
        check(mutableState.value.canRestore)
        mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.RestoreSnapshot, PromptMessages.basic("Restore this snapshot?"), id))
    }

    // Restore after confirmation and navigate to the draft editor because restored content is pending.
    fun confirmRestoreSnapshot() {
        val id = requirePrompt(PromptKind.RestoreSnapshot).targetId ?: error("Restore target is required")
        history.restoreSnapshot(noteId, id)
        effectBuffer.emit(PresentationEffect.Navigate(AppRoute.Editor(noteId)))
    }

    // Export visible content because external file handling is represented as an effect.
    fun exportDisplayed() { effectBuffer.emit(PresentationEffect.ExportText(export.exportDisplayedContent(DisplayedContent.draft(mutableState.value.content)))) }

    // Share visible content because platform share is represented as an effect.
    fun shareDisplayed() { effectBuffer.emit(PresentationEffect.ShareText(export.shareDisplayedContent(DisplayedContent.draft(mutableState.value.content)))) }

    // Navigate to history only when domain rules say saved history is available.
    fun openHistory() {
        check(mutableState.value.canOpenHistory)
        effectBuffer.emit(PresentationEffect.Navigate(AppRoute.History(noteId)))
    }

    // Clear prompts because dismissal must leave visible content unchanged.
    fun dismissPrompt() { mutableState.value = mutableState.value.copy(prompt = null) }

    // Store editor state atomically because toolbar flags depend on content mode.
    private fun setContent(content: String, mode: EditorMode, snapshotId: Long? = null, isTrashed: Boolean = mutableState.value.isTrashed, prompt: PromptState? = mutableState.value.prompt) {
        mutableState.value = mutableState.value.copy(
            snapshotId = snapshotId,
            content = content,
            mode = mode,
            canEnterEdit = mode == EditorMode.View && !isTrashed,
            canSave = mode == EditorMode.Edit && !isTrashed,
            canCancel = mode == EditorMode.Edit && !isTrashed && lifecycle.canCancelEdit(noteId),
            canDelete = mode == EditorMode.View && !isTrashed,
            canRestore = mode == EditorMode.ViewSnapshot,
            canUndo = !isTrashed && undoRedo.canUndo(noteId, mode),
            canRedo = !isTrashed && undoRedo.canRedo(noteId, mode),
            canOpenHistory = mode == EditorMode.View && !isTrashed && history.canOpenHistory(noteId),
            canExport = true,
            canShare = true,
            isTrashed = isTrashed,
            prompt = prompt,
        )
    }

    // Classify text edits conservatively because paste-like chunks must form undo boundaries.
    private fun operationKindFor(before: String, after: String): UndoOperationKind {
        if (after.isEmpty() && before.isNotEmpty()) return UndoOperationKind.Clear
        val position = firstChangedPosition(before, after)
        val removed = before.length - commonSuffixLength(before, after, position) - position
        val inserted = after.length - commonSuffixLength(before, after, position) - position
        return when {
            removed == 0 && inserted == 1 -> UndoOperationKind.Typing
            inserted == 0 -> UndoOperationKind.Deletion
            removed == 0 -> UndoOperationKind.Paste
            else -> UndoOperationKind.Replacement
        }
    }

    // Count the unchanged tail because operation classification needs the replaced span length.
    private fun commonSuffixLength(before: String, after: String, prefixLength: Int): Int {
        var count = 0
        while (count < before.length - prefixLength && count < after.length - prefixLength && before[before.lastIndex - count] == after[after.lastIndex - count]) {
            count += 1
        }
        return count
    }

    // Locate the edit boundary because undo grouping needs a stable operation position.
    private fun firstChangedPosition(before: String, after: String): Int {
        val limit = minOf(before.length, after.length)
        for (index in 0 until limit) {
            if (before[index] != after[index]) return index
        }
        return limit
    }

    // Fail fast for mismatched prompts because confirmation handlers must match visible dialogs.
    private fun requirePrompt(kind: PromptKind): PromptState = mutableState.value.prompt?.takeIf { it.kind == kind } ?: error("Prompt $kind is required")
}
