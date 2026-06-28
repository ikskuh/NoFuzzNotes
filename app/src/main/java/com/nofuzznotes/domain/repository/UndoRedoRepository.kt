package com.nofuzznotes.domain.repository

import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind

interface UndoRedoRepository {
    // Create undo rows because services need durable edit-stack semantics across restarts.
    fun create(
        noteId: Long,
        direction: UndoDirection,
        operationKind: UndoOperationKind,
        position: Int,
        textBefore: String,
        textAfter: String,
        cursorBefore: Int,
        cursorAfter: Int,
    ): UndoEntry

    // Clear all edit-stack rows for a note because save makes undo history unavailable.
    fun deleteForNote(noteId: Long)
}
