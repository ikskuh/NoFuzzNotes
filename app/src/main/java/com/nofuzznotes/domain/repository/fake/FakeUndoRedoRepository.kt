package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.Clock
import com.nofuzznotes.domain.repository.UndoRedoRepository

class FakeUndoRedoRepository(private val clock: Clock) : UndoRedoRepository {
    private val entries = linkedMapOf<Long, UndoEntry>()
    private var nextId = 1L

    // Create stack rows in memory because service tests need persistence semantics before Room exists.
    override fun create(
        noteId: Long,
        direction: UndoDirection,
        operationKind: UndoOperationKind,
        position: Int,
        textBefore: String,
        textAfter: String,
        cursorBefore: Int,
        cursorAfter: Int,
        selectionBefore: TextSelection,
        selectionAfter: TextSelection,
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
            selectionBefore = selectionBefore,
            selectionAfter = selectionAfter,
            created = clock.now(),
        )
        entries[entry.id] = entry
        nextId += 1L
        return entry
    }

    // List oldest-first so callers can treat the last row as the top of the persisted stack.
    override fun listForNote(noteId: Long, direction: UndoDirection): List<UndoEntry> {
        assert(noteId > 0L)
        return entries.values.filter { it.noteId == noteId && it.direction == direction }.sortedBy { it.id }
    }

    // Peek without removing because toolbar availability must not mutate the durable stack.
    override fun peek(noteId: Long, direction: UndoDirection): UndoEntry? {
        assert(noteId > 0L)
        return listForNote(noteId, direction).lastOrNull()
    }

    // Remove and return the top row because undo/redo move one operation at a time.
    override fun pop(noteId: Long, direction: UndoDirection): UndoEntry? {
        assert(noteId > 0L)
        val entry = peek(noteId, direction) ?: return null
        entries.remove(entry.id)
        return entry
    }

    // Delete one stack when stack rules invalidate only one direction.
    override fun deleteForNote(noteId: Long, direction: UndoDirection) {
        assert(noteId > 0L)
        entries.entries.removeIf { it.value.noteId == noteId && it.value.direction == direction }
    }

    // Delete edit-stack rows when a note is permanently destroyed or saved.
    override fun deleteForNote(noteId: Long) {
        assert(noteId > 0L)
        entries.entries.removeIf { it.value.noteId == noteId }
    }
}
