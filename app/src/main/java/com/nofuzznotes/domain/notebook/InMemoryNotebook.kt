package com.nofuzznotes.domain.notebook

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.NotebookEntry
import com.nofuzznotes.core.model.Snapshot
import com.nofuzznotes.core.text.CoreTextRules
import com.nofuzznotes.core.time.Clock

class InMemoryNotebook(private val clock: Clock) {
    private val notes = linkedMapOf<Long, Note>()
    private val snapshotsByNote = linkedMapOf<Long, MutableList<Snapshot>>()
    private var nextNoteId = 1L
    private var nextSnapshotId = 1L

    // Create notes with immediately durable draft text because an empty note is still a real note.
    fun createNote(content: String = ""): NotebookEntry {
        assert(nextNoteId > 0L)
        val now = clock.now()
        val note = Note(
            id = nextNoteId,
            content = CoreTextRules.normalizeLf(content),
            created = now,
            edited = now,
            deleted = null,
        )
        notes[note.id] = note
        snapshotsByNote[note.id] = mutableListOf()
        nextNoteId += 1L
        return entryFor(note.id)
    }

    // Read note state as an aggregate because callers need draft and history to decide view mode.
    fun readNote(noteId: Long): NotebookEntry? {
        assert(noteId > 0L)
        return if (notes.containsKey(noteId)) entryFor(noteId) else null
    }

    // List active notes separately because trash is a first-class lifecycle state, not deletion.
    fun listNotes(): List<NotebookEntry> {
        return notes.values.filter { !it.isTrashed() }.sortedByDescending { it.edited }.map { entryFor(it.id) }
    }

    // List trashed notes separately because recovery must not mix deleted drafts with normal notes.
    fun listTrash(): List<NotebookEntry> {
        return notes.values.filter { it.isTrashed() }.sortedByDescending { it.edited }.map { entryFor(it.id) }
    }

    // Replace the durable draft because edits must be persisted as the user types.
    fun editDraft(noteId: Long, content: String): NotebookEntry {
        val note = requireNote(noteId)
        assert(!note.isTrashed())
        val normalized = CoreTextRules.normalizeLf(content)
        val now = clock.now()
        notes[noteId] = note.copy(content = normalized, edited = now)
        return entryFor(noteId)
    }

    // Save creates an immutable snapshot only when it represents a new saved version.
    fun saveSnapshot(noteId: Long): Snapshot? {
        val entry = entryFor(noteId)
        assert(!entry.note.isTrashed())
        if (!entry.hasPendingChanges) {
            notes[noteId] = entry.note.copy(edited = clock.now())
            return null
        }

        assert(nextSnapshotId > 0L)
        val now = clock.now()
        val snapshot = Snapshot(
            id = nextSnapshotId,
            noteId = noteId,
            content = entry.note.content,
            created = now,
        )
        snapshotsByNote.getValue(noteId).add(snapshot)
        notes[noteId] = entry.note.copy(edited = now)
        nextSnapshotId += 1L
        return snapshot
    }

    // Restore saved content into the draft because historical snapshots themselves must stay immutable.
    fun restoreSnapshot(noteId: Long, snapshotId: Long): NotebookEntry {
        val note = requireNote(noteId)
        assert(!note.isTrashed())
        val snapshot = requireSnapshot(noteId, snapshotId)
        val now = clock.now()
        notes[noteId] = note.copy(content = snapshot.content, edited = now)
        return entryFor(noteId)
    }

    // Trash only marks deletion time because draft and history must remain recoverable.
    fun trashNote(noteId: Long): NotebookEntry {
        val note = requireNote(noteId)
        assert(!note.isTrashed())
        notes[noteId] = note.copy(deleted = clock.now())
        return entryFor(noteId)
    }

    // Untrash only clears deletion time because recovery must not mutate draft timestamps or content.
    fun untrashNote(noteId: Long): NotebookEntry {
        val note = requireNote(noteId)
        assert(note.isTrashed())
        notes[noteId] = note.copy(deleted = null)
        return entryFor(noteId)
    }

    // Destroy removes the aggregate because permanent deletion is not recoverable.
    fun destroyNote(noteId: Long) {
        val note = requireNote(noteId)
        assert(note.isTrashed())
        notes.remove(noteId)
        snapshotsByNote.remove(noteId)
    }

    // Build immutable aggregate copies because callers must not mutate fake persistence internals.
    private fun entryFor(noteId: Long): NotebookEntry {
        val note = requireNote(noteId)
        return NotebookEntry(note = note, snapshots = snapshotsByNote.getValue(noteId).toList())
    }

    // Surface missing notes as implementation bugs because callers should keep valid note identities.
    private fun requireNote(noteId: Long): Note {
        assert(noteId > 0L)
        return notes.getValue(noteId)
    }

    // Surface missing snapshots as implementation bugs because callers should choose from known history.
    private fun requireSnapshot(noteId: Long, snapshotId: Long): Snapshot {
        assert(snapshotId > 0L)
        return snapshotsByNote.getValue(noteId).first { it.id == snapshotId }
    }
}
