package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.Snapshot
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.text.CoreTextRules
import com.nofuzznotes.domain.repository.NoteRepository
import com.nofuzznotes.domain.repository.SnapshotRepository
import com.nofuzznotes.domain.repository.UndoRedoRepository

data class SnapshotListItem(
    val id: Long,
    val noteId: Long,
    val title: String,
    val created: java.time.Instant,
) {
    init {
        assert(id > 0L)
        assert(noteId > 0L)
    }
}

data class SnapshotViewResult(
    val note: Note,
    val snapshot: Snapshot,
    val mode: EditorMode,
    val canEdit: Boolean,
    val displayedContent: DisplayedContent,
) {
    init {
        assert(snapshot.noteId == note.id)
        assert(mode == EditorMode.ViewSnapshot)
        assert(!canEdit)
    }
}

data class RestoreSnapshotResult(
    val note: Note,
    val snapshot: Snapshot,
    val undoEntry: UndoEntry,
    val mode: EditorMode,
) {
    init {
        assert(snapshot.noteId == note.id)
        assert(undoEntry.noteId == note.id)
        assert(undoEntry.operationKind == UndoOperationKind.SnapshotRestore)
        assert(mode == EditorMode.Edit)
    }
}

data class DisplayedContent(
    val content: String,
) {
    val exportFilename: String
        // Derive the filename here because export/share must use the same displayed text source.
        get() = CoreTextRules.exportFilename(content)

    // Return the exact visible text because platform share must not reload a different draft or snapshot.
    fun shareText(): String = content

    // Return the exact visible text because platform export must not reload a different draft or snapshot.
    fun exportText(): String = content

    companion object {
        // Build displayed draft content through the shared path because exports must depend on what is visible.
        fun draft(content: String): DisplayedContent = DisplayedContent(content)

        // Build displayed snapshot content through the shared path because snapshots must export like drafts.
        fun snapshot(content: String): DisplayedContent = DisplayedContent(content)
    }
}

class HistoryService(
    private val notes: NoteRepository,
    private val snapshots: SnapshotRepository,
    private val undoRedo: UndoRedoRepository,
) {
    // Report history availability from stored snapshots because empty history must not be opened.
    fun canOpenHistory(noteId: Long): Boolean {
        assert(noteId > 0L)
        val note = requireNote(noteId)
        if (note.isTrashed()) return false
        val latest = latestSnapshot(noteId)
        return latest != null && note.content == latest.content
    }

    // List newest first by repository save order because multiple saves may share one timestamp.
    fun listSnapshotsNewestFirst(noteId: Long): List<SnapshotListItem> {
        assert(noteId > 0L)
        val note = requireNote(noteId)
        check(!note.isTrashed()) { "History is unavailable for trashed notes" }
        val latest = latestSnapshot(noteId) ?: error("History requires at least one snapshot")
        check(note.content == latest.content) { "History requires no pending changes" }
        return snapshots.listForNote(noteId).asReversed().map {
            SnapshotListItem(id = it.id, noteId = it.noteId, title = it.title, created = it.created)
        }
    }

    // Open a snapshot as displayed content because snapshot view must be read-only and exportable.
    fun openSnapshot(noteId: Long, snapshotId: Long): SnapshotViewResult {
        assert(noteId > 0L)
        assert(snapshotId > 0L)
        val note = requireCleanNote(noteId, "Snapshot view")
        val snapshot = requireSnapshot(noteId, snapshotId)
        return SnapshotViewResult(
            note = note,
            snapshot = snapshot,
            mode = EditorMode.ViewSnapshot,
            canEdit = false,
            displayedContent = DisplayedContent.snapshot(snapshot.content),
        )
    }

    // Restore only clean drafts because rollback must not silently discard pending user edits.
    fun restoreSnapshot(noteId: Long, snapshotId: Long): RestoreSnapshotResult {
        assert(noteId > 0L)
        assert(snapshotId > 0L)
        val before = requireCleanNote(noteId, "Restore")
        val snapshot = requireSnapshot(noteId, snapshotId)
        undoRedo.deleteForNote(noteId, UndoDirection.Redo)
        val after = notes.updateContent(noteId, snapshot.content)
        val undoEntry = undoRedo.create(
            noteId = noteId,
            direction = UndoDirection.Undo,
            operationKind = UndoOperationKind.SnapshotRestore,
            position = 0,
            textBefore = before.content,
            textAfter = after.content,
            cursorBefore = before.content.length,
            cursorAfter = after.content.length,
        )
        return RestoreSnapshotResult(note = after, snapshot = snapshot, undoEntry = undoEntry, mode = EditorMode.Edit)
    }

    // Require a saved draft because history actions must not discard pending user edits.
    private fun requireCleanNote(noteId: Long, action: String): Note {
        val note = requireNote(noteId)
        check(!note.isTrashed()) { "$action is unavailable for trashed notes" }
        val latest = latestSnapshot(noteId) ?: error("$action requires at least one snapshot")
        check(note.content == latest.content) { "$action requires no pending changes" }
        return note
    }

    // Load the selected snapshot through the repository because history rows are addressed by immutable ids.
    private fun requireSnapshot(noteId: Long, snapshotId: Long): Snapshot {
        val snapshot = snapshots.read(snapshotId) ?: error("Missing snapshot: $snapshotId")
        assert(snapshot.noteId == noteId)
        return snapshot
    }

    // Select the newest saved version because pending-change checks compare against the latest save only.
    private fun latestSnapshot(noteId: Long): Snapshot? = snapshots.listForNote(noteId).lastOrNull()

    // Surface missing notes because history services should only receive existing note identities.
    private fun requireNote(noteId: Long): Note = notes.read(noteId) ?: error("Missing note: $noteId")
}
