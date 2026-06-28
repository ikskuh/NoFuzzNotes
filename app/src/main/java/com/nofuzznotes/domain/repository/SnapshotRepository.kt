package com.nofuzznotes.domain.repository

import com.nofuzznotes.core.model.Snapshot

interface SnapshotRepository {
    // Create immutable history rows because saves must preserve full note text at a point in time.
    fun create(noteId: Long, content: String): Snapshot

    // Read by id because history actions operate on a selected immutable save.
    fun read(id: Long): Snapshot?

    // List snapshots oldest-first because callers need stable save order when timestamps tie.
    fun listForNote(noteId: Long): List<Snapshot>
}
