package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.domain.repository.NoteRepository
import com.nofuzznotes.domain.repository.SnapshotRepository
import com.nofuzznotes.domain.repository.UndoRedoRepository

class NoteLifecycleService(
    private val notes: NoteRepository,
    private val snapshots: SnapshotRepository,
    private val undoRedo: UndoRedoRepository,
) {
    // Create the durable row immediately because empty notes are valid user data.
    fun createNote(): OpenNoteResult {
        val note = notes.createEmpty()
        return OpenNoteResult(note = note, latestSnapshot = null, mode = EditorMode.Edit)
    }

    // Recalculate mode on every open because edit/view state is derived, not persisted.
    fun openNote(noteId: Long): OpenNoteResult {
        assert(noteId > 0L)
        val note = requireNote(noteId)
        val latest = latestSnapshot(noteId)
        return OpenNoteResult(note = note, latestSnapshot = latest, mode = modeFor(note, latest?.content))
    }

    // Persist every draft change immediately because the draft is the user's current durable work.
    fun editDraft(noteId: Long, content: String): OpenNoteResult {
        assert(noteId > 0L)
        val note = notes.updateContent(noteId, content)
        val latest = latestSnapshot(noteId)
        return OpenNoteResult(note = note, latestSnapshot = latest, mode = EditorMode.Edit)
    }

    // Save creates history only for changed drafts but always records the explicit save time.
    fun saveNote(noteId: Long): SaveNoteResult {
        assert(noteId > 0L)
        val beforeTouch = requireNote(noteId)
        val latest = latestSnapshot(noteId)
        val created = if (latest?.content == beforeTouch.content) null else snapshots.create(noteId, beforeTouch.content)
        val touched = notes.touchEdited(noteId)
        undoRedo.deleteForNote(noteId)
        return SaveNoteResult(note = touched, createdSnapshot = created, mode = EditorMode.View)
    }

    // Allow cancel only when a saved version exists because there is otherwise no safe target.
    fun canCancelEdit(noteId: Long): Boolean {
        assert(noteId > 0L)
        requireNote(noteId)
        return latestSnapshot(noteId) != null
    }

    // Reset to the latest save as one undoable edit because cancel destroys unsaved draft content.
    fun cancelEdit(noteId: Long): CancelEditResult {
        assert(noteId > 0L)
        val before = requireNote(noteId)
        val latest = latestSnapshot(noteId) ?: error(
            "Cancel edit requires a latest snapshot",
        )
        val after = notes.updateContent(noteId, latest.content)
        val undoEntry = undoRedo.create(
            noteId = noteId,
            direction = UndoDirection.Undo,
            operationKind = UndoOperationKind.CancelEdit,
            position = 0,
            textBefore = before.content,
            textAfter = after.content,
            cursorBefore = before.content.length,
            cursorAfter = after.content.length,
        )
        return CancelEditResult(note = after, undoEntry = undoEntry, mode = EditorMode.View)
    }

    // Compare against latest snapshot content because pending changes are not stored separately.
    private fun modeFor(note: Note, latestContent: String?): EditorMode {
        return if (latestContent != null && note.content == latestContent) EditorMode.View else EditorMode.Edit
    }

    // Select the newest saved version because only the latest snapshot determines pending changes.
    private fun latestSnapshot(noteId: Long) = snapshots.listForNote(noteId).lastOrNull()

    // Surface missing notes because lifecycle services should only receive existing identities.
    private fun requireNote(noteId: Long): Note = notes.read(noteId) ?: error("Missing note: $noteId")
}
