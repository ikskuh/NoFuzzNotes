package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.text.CoreTextRules
import com.nofuzznotes.core.time.Clock
import com.nofuzznotes.domain.repository.NoteRepository

class FakeNoteRepository(private val clock: Clock) : NoteRepository {
    private val notes = linkedMapOf<Long, Note>()
    private var nextId = 1L

    // Create an empty durable row because a new note must exist before it has user content.
    override fun createEmpty(): Note {
        assert(nextId > 0L)
        val now = clock.now()
        val note = Note(id = nextId, content = "", created = now, edited = now, deleted = null)
        notes[note.id] = note
        nextId += 1L
        return note
    }

    // Read by id so services can reload a note aggregate without database dependencies.
    override fun read(id: Long): Note? {
        assert(id > 0L)
        return notes[id]
    }

    // Return active notes newest-edited first because that is the default notebook list order.
    fun listNormal(): List<Note> = notes.values.filter { !it.isTrashed() }.sortedByDescending { it.edited }

    // Return trashed notes separately because deletion is recoverable state in the domain.
    fun listTrash(): List<Note> = notes.values.filter { it.isTrashed() }.sortedByDescending { it.edited }

    // Replace draft content and edited time because draft persistence happens at the repository boundary.
    override fun updateContent(id: Long, content: String): Note {
        val note = requireNote(id)
        val updated = note.copy(content = CoreTextRules.normalizeLf(content), edited = clock.now())
        notes[id] = updated
        return updated
    }

    // Update edited without content changes because pressing save is itself a persisted note event.
    override fun touchEdited(id: Long): Note {
        assert(id > 0L)
        val note = requireNote(id)
        val updated = note.copy(edited = clock.now())
        notes[id] = updated
        return updated
    }

    // Mark a note as trashed without changing edited because trash state has its own timestamp.
    fun markDeleted(id: Long): Note {
        val note = requireNote(id)
        assert(!note.isTrashed())
        val updated = note.copy(deleted = clock.now())
        notes[id] = updated
        return updated
    }

    // Clear trash state without changing edited because recovery should not reorder content by itself.
    fun clearDeleted(id: Long): Note {
        val note = requireNote(id)
        assert(note.isTrashed())
        val updated = note.copy(deleted = null)
        notes[id] = updated
        return updated
    }

    // Remove a note row because permanent destruction is a repository-level lifecycle operation.
    fun destroy(id: Long) {
        assert(id > 0L)
        assert(notes.containsKey(id))
        notes.remove(id)
    }

    // Surface missing notes as implementation bugs because services should keep valid note identities.
    private fun requireNote(id: Long): Note {
        assert(id > 0L)
        return notes.getValue(id)
    }
}
