package com.nofuzznotes.presentation.editor

import androidx.lifecycle.ViewModel
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
        setContent(result.note.content, result.mode)
    }

    // Enter edit mode without mutating content because explicit editing is a view-state transition only.
    fun enterEditMode() {
        check(mutableState.value.mode == EditorMode.View)
        setContent(mutableState.value.content, EditorMode.Edit)
    }

    // Persist text and undo information together because user edits must survive process death.
    fun textChanged(newContent: String) {
        val current = mutableState.value
        check(current.mode == EditorMode.Edit) { "Text changes require edit mode" }
        if (newContent == current.content) return
        undoRedo.recordEdit(
            noteId = noteId,
            edit = TextEdit(
                operationKind = if (newContent.length >= current.content.length) UndoOperationKind.Typing else UndoOperationKind.Deletion,
                position = 0,
                textBefore = current.content,
                textAfter = newContent,
                cursorBefore = current.content.length,
                cursorAfter = newContent.length,
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
    private fun setContent(content: String, mode: EditorMode, snapshotId: Long? = null, prompt: PromptState? = mutableState.value.prompt) {
        mutableState.value = mutableState.value.copy(
            snapshotId = snapshotId,
            content = content,
            mode = mode,
            canEnterEdit = mode == EditorMode.View,
            canSave = mode == EditorMode.Edit,
            canCancel = mode == EditorMode.Edit && lifecycle.canCancelEdit(noteId),
            canDelete = mode == EditorMode.View,
            canRestore = mode == EditorMode.ViewSnapshot,
            canUndo = undoRedo.canUndo(noteId, mode),
            canRedo = undoRedo.canRedo(noteId, mode),
            canOpenHistory = mode == EditorMode.View && history.canOpenHistory(noteId),
            canExport = true,
            canShare = true,
            prompt = prompt,
        )
    }

    // Fail fast for mismatched prompts because confirmation handlers must match visible dialogs.
    private fun requirePrompt(kind: PromptKind): PromptState = mutableState.value.prompt?.takeIf { it.kind == kind } ?: error("Prompt $kind is required")
}
