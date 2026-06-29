package com.nofuzznotes.data.room

import com.nofuzznotes.core.model.Snapshot
import com.nofuzznotes.core.text.CoreTextRules
import com.nofuzznotes.core.time.Clock
import com.nofuzznotes.domain.repository.SnapshotRepository

class RoomSnapshotRepository(private val database: NoFuzzNotesDatabase, private val clock: Clock) : SnapshotRepository {
    private val dao = database.snapshotDao()

    // Create immutable history rows because saves must preserve the full normalized draft.
    override fun create(noteId: Long, content: String): Snapshot {
        assert(noteId > 0L)
        val id = dao.insert(SnapshotEntity(noteId = noteId, content = CoreTextRules.normalizeLf(content), created = clock.now().toStoredTimestamp()))
        assert(id > 0L)
        return dao.read(id)!!.toModel()
    }

    // Read by id because snapshot viewing operates on selected saved versions.
    override fun read(id: Long): Snapshot? {
        assert(id > 0L)
        return dao.read(id)?.toModel()
    }

    // List history oldest-first because save order must be stable after reopen.
    override fun listForNote(noteId: Long): List<Snapshot> {
        assert(noteId > 0L)
        return dao.listForNote(noteId).map { it.toModel() }
    }

    // Delete history explicitly because repository tests inspect destruction behavior.
    override fun deleteForNote(noteId: Long) {
        assert(noteId > 0L)
        dao.deleteForNote(noteId)
    }
}
