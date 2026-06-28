package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.UndoEntry

data class CancelEditResult(
    val note: Note,
    val undoEntry: UndoEntry,
    val mode: EditorMode,
) {
    init {
        assert(mode == EditorMode.View)
        assert(undoEntry.noteId == note.id)
    }
}
