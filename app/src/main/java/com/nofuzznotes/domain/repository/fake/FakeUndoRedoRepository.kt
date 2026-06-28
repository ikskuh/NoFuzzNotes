package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.Clock

class FakeUndoRedoRepository(private val clock: Clock) {
    private val entries = linkedMapOf<Long, UndoEntry>()
    private var nextId = 1L

    // Create undo rows in memory because later services need durable edit-stack semantics before Room exists.
    fun create(
        noteId: Long,
        direction: UndoDirection,
        operationKind: UndoOperationKind,
        position: Int,
        textBefore: String,
        textAfter: String,
        cursorBefore: Int,
        cursorAfter: Int,
    ): UndoEntry {
        assert(nextId > 0L)
        val entry = UndoEntry(
            id = nextId,
            noteId = noteId,
            direction = direction,
            operationKind = operationKind,
            position = position,
            textBefore = textBefore,
            textAfter = textAfter,
            cursorBefore = cursorBefore,
            cursorAfter = cursorAfter,
            created = clock.now(),
        )
        entries[entry.id] = entry
        nextId += 1L
        return entry
    }

    // List entries by note and direction because undo and redo stacks must be queried independently.
    fun listForNote(noteId: Long, direction: UndoDirection): List<UndoEntry> {
        assert(noteId > 0L)
        return entries.values.filter { it.noteId == noteId && it.direction == direction }.sortedBy { it.created }
    }

    // Delete edit-stack rows when a note is permanently destroyed.
    fun deleteForNote(noteId: Long) {
        assert(noteId > 0L)
        entries.entries.removeIf { it.value.noteId == noteId }
    }
}
