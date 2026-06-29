package com.nofuzznotes.data.room

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.text.CoreTextRules
import com.nofuzznotes.core.time.Clock
import com.nofuzznotes.domain.repository.NoteRepository

class RoomNoteRepository(private val database: NoFuzzNotesDatabase, private val clock: Clock) : NoteRepository {
    private val dao = database.noteDao()

    // Create an empty durable row because a new note must exist before user typing begins.
    override fun createEmpty(): Note {
        val now = clock.now().toStoredTimestamp()
        val id = dao.insert(NoteEntity(content = "", created = now, edited = now, deleted = null))
        assert(id > 0L)
        return requireNote(id)
    }

    // Read by id so services can reload notes without knowing Room entities.
    override fun read(id: Long): Note? {
        assert(id > 0L)
        return dao.read(id)?.toModel()
    }

    // List active notes in SQLite sort order because UI order must survive reopen.
    override fun listNormal(): List<Note> = dao.listNormal().map { it.toModel() }

    // List trashed notes separately because recoverable deletion is not normal browsing.
    override fun listTrash(): List<Note> = dao.listTrash().map { it.toModel() }

    // Persist normalized draft text because database content is the current draft source of truth.
    override fun updateContent(id: Long, content: String): Note {
        assert(id > 0L)
        val updated = dao.updateContent(id, CoreTextRules.normalizeLf(content), clock.now().toStoredTimestamp())
        assert(updated == 1)
        return requireNote(id)
    }

    // Persist a save click timestamp because edited tracks explicit saves too.
    override fun touchEdited(id: Long): Note {
        assert(id > 0L)
        val updated = dao.touchEdited(id, clock.now().toStoredTimestamp())
        assert(updated == 1)
        return requireNote(id)
    }

    // Mark trash state without changing edited because deletion has a separate timestamp.
    override fun markDeleted(id: Long): Note {
        assert(id > 0L)
        assert(!requireNote(id).isTrashed())
        val updated = dao.markDeleted(id, clock.now().toStoredTimestamp())
        assert(updated == 1)
        return requireNote(id)
    }

    // Clear trash state without changing edited because recovery should not imply content editing.
    override fun clearDeleted(id: Long): Note {
        assert(id > 0L)
        assert(requireNote(id).isTrashed())
        val updated = dao.clearDeleted(id)
        assert(updated == 1)
        return requireNote(id)
    }

    // Delete the note row because Room foreign keys cascade dependent rows.
    override fun destroy(id: Long) {
        assert(id > 0L)
        database.runInTransaction {
            val deleted = dao.delete(id)
            assert(deleted == 1)
        }
    }

    // Surface missing rows as implementation bugs because service code should pass valid ids.
    private fun requireNote(id: Long): Note = dao.read(id)!!.toModel()
}
