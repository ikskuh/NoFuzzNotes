package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.domain.repository.NoteRepository
import com.nofuzznotes.domain.repository.SnapshotRepository
import com.nofuzznotes.domain.repository.UndoRedoRepository

data class TrashViewResult(
    val note: Note,
    val mode: EditorMode,
    val canEdit: Boolean,
    val canOpenHistory: Boolean,
    val displayedContent: DisplayedContent,
) {
    init {
        assert(note.isTrashed())
        assert(mode == EditorMode.View)
        assert(!canEdit)
        assert(!canOpenHistory)
    }
}

class TrashService(
    private val notes: NoteRepository,
    private val snapshots: SnapshotRepository,
    private val undoRedo: UndoRedoRepository,
) {
    // Mark a note deleted because normal delete must be recoverable trash state first.
    fun trashNote(noteId: Long): Note {
        assert(noteId > 0L)
        val note = requireNote(noteId)
        check(!note.isTrashed()) { "Note is already trashed" }
        return notes.markDeleted(noteId)
    }

    // Clear only deleted state because untrash must not rewrite user content or history.
    fun untrashNote(noteId: Long): Note {
        assert(noteId > 0L)
        val note = requireNote(noteId)
        check(note.isTrashed()) { "Only trashed notes can be untrashed" }
        return notes.clearDeleted(noteId)
    }

    // Delete all data for one trashed note because permanent destruction must leave no recovery path.
    fun destroyNote(noteId: Long) {
        assert(noteId > 0L)
        val note = requireNote(noteId)
        check(note.isTrashed()) { "Only trashed notes can be destroyed" }
        snapshots.deleteForNote(noteId)
        undoRedo.deleteForNote(noteId)
        notes.destroy(noteId)
    }

    // Destroy current trash contents because empty trash must not affect active notes.
    fun emptyTrash(): Int {
        val trashedIds = notes.listTrash().map { it.id }
        trashedIds.forEach { destroyNote(it) }
        return trashedIds.size
    }

    // Open trashed content read-only because deleted notes remain viewable but not editable.
    fun openTrashedNote(noteId: Long): TrashViewResult {
        assert(noteId > 0L)
        val note = requireNote(noteId)
        check(note.isTrashed()) { "Trash view requires a trashed note" }
        return TrashViewResult(
            note = note,
            mode = EditorMode.View,
            canEdit = false,
            canOpenHistory = false,
            displayedContent = DisplayedContent.draft(note.content),
        )
    }

    // Surface missing notes because trash operations should only receive existing identities.
    private fun requireNote(noteId: Long): Note = notes.read(noteId) ?: error("Missing note: $noteId")
}
