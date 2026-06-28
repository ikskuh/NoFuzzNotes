package com.nofuzznotes.domain.repository

import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind

interface UndoRedoRepository {
    // Create stack rows because services need durable edit history across editor lifetimes.
    fun create(
        noteId: Long,
        direction: UndoDirection,
        operationKind: UndoOperationKind,
        position: Int,
        textBefore: String,
        textAfter: String,
        cursorBefore: Int,
        cursorAfter: Int,
        selectionBefore: TextSelection = TextSelection(cursorBefore, cursorBefore),
        selectionAfter: TextSelection = TextSelection(cursorAfter, cursorAfter),
    ): UndoEntry

    // Peek at the next stack operation because availability must not mutate edit history.
    fun peek(noteId: Long, direction: UndoDirection): UndoEntry?

    // Pop the next stack operation because undo and redo move exactly one persisted entry.
    fun pop(noteId: Long, direction: UndoDirection): UndoEntry?

    // List entries for tests and later Room parity checks because stack persistence must be inspectable.
    fun listForNote(noteId: Long, direction: UndoDirection): List<UndoEntry>

    // Clear one stack because a new edit invalidates only redo history.
    fun deleteForNote(noteId: Long, direction: UndoDirection)

    // Clear all edit-stack rows for a note because save and destruction make history unavailable.
    fun deleteForNote(noteId: Long)
}
