package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.Snapshot

data class SaveNoteResult(
    val note: Note,
    val createdSnapshot: Snapshot?,
    val mode: EditorMode,
) {
    init {
        assert(mode == EditorMode.View)
        assert(createdSnapshot == null || createdSnapshot.noteId == note.id)
    }
}
