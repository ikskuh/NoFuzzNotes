package com.nofuzznotes.core.model

import com.nofuzznotes.core.text.CoreTextRules

data class NotebookEntry(
    val note: Note,
    val snapshots: List<Snapshot>,
) {
    init {
        assert(snapshots.all { it.noteId == note.id })
        assert(snapshots.zipWithNext().all { (older, newer) -> !newer.created.isBefore(older.created) })
    }

    val latestSnapshot: Snapshot?
        // Use creation order because snapshots are immutable save events.
        get() = snapshots.lastOrNull()

    val hasPendingChanges: Boolean
        // Centralize pending state because no-snapshot notes are always considered unsaved work.
        get() = CoreTextRules.hasPendingChanges(note.content, latestSnapshot?.content)
}
