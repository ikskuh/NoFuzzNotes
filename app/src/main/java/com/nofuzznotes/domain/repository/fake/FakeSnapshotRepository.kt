package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.model.Snapshot
import com.nofuzznotes.core.text.CoreTextRules
import com.nofuzznotes.core.time.Clock
import com.nofuzznotes.domain.repository.SnapshotRepository

class FakeSnapshotRepository(private val clock: Clock) : SnapshotRepository {
    private val snapshots = linkedMapOf<Long, Snapshot>()
    private var nextId = 1L

    // Create immutable history rows because saves must preserve full note text at a point in time.
    override fun create(noteId: Long, content: String): Snapshot {
        assert(noteId > 0L)
        assert(nextId > 0L)
        val snapshot = Snapshot(
            id = nextId,
            noteId = noteId,
            content = CoreTextRules.normalizeLf(content),
            created = clock.now(),
        )
        snapshots[snapshot.id] = snapshot
        nextId += 1L
        return snapshot
    }

    // Read by id so services can open a selected saved version.
    override fun read(id: Long): Snapshot? {
        assert(id > 0L)
        return snapshots[id]
    }

    // List snapshots oldest-first because history order must remain stable when timestamps tie.
    override fun listForNote(noteId: Long): List<Snapshot> {
        assert(noteId > 0L)
        return snapshots.values.filter { it.noteId == noteId }.sortedBy { it.created }
    }

    // Delete note history when a note is permanently destroyed.
    fun deleteForNote(noteId: Long) {
        assert(noteId > 0L)
        snapshots.entries.removeIf { it.value.noteId == noteId }
    }
}
