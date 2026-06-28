package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.Snapshot

data class OpenNoteResult(
    val note: Note,
    val latestSnapshot: Snapshot?,
    val mode: EditorMode,
) {
    init {
        assert(latestSnapshot == null || latestSnapshot.noteId == note.id)
    }

    val hasPendingChanges: Boolean
        // Recompute pending state because editor mode must reflect current persisted content.
        get() = latestSnapshot?.content != note.content
}
