package com.nofuzznotes.data.room

import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.Clock
import com.nofuzznotes.domain.repository.UndoRedoRepository

class RoomUndoRedoRepository(private val database: NoFuzzNotesDatabase, private val clock: Clock) : UndoRedoRepository {
    private val dao = database.undoRedoDao()

    // Create stack rows in SQLite because undo and redo must survive app restarts.
    override fun create(noteId: Long, direction: UndoDirection, operationKind: UndoOperationKind, position: Int, textBefore: String, textAfter: String, cursorBefore: Int, cursorAfter: Int, selectionBefore: TextSelection, selectionAfter: TextSelection): UndoEntry {
        assert(noteId > 0L)
        val id = dao.insert(UndoRedoEntity(0L, noteId, direction.name, operationKind.name, position, textBefore, textAfter, cursorBefore, cursorAfter, selectionBefore.start, selectionBefore.end, selectionAfter.start, selectionAfter.end, clock.now().toStoredTimestamp()))
        assert(id > 0L)
        return dao.peek(noteId, direction.name)!!.toModel()
    }

    // Peek the latest row because the persisted stack top is the newest entry.
    override fun peek(noteId: Long, direction: UndoDirection): UndoEntry? {
        assert(noteId > 0L)
        return dao.peek(noteId, direction.name)?.toModel()
    }

    // Remove the stack top because undo and redo consume exactly one persisted operation.
    override fun pop(noteId: Long, direction: UndoDirection): UndoEntry? {
        assert(noteId > 0L)
        var popped: UndoEntry? = null
        database.runInTransaction {
            val entry = dao.peek(noteId, direction.name) ?: return@runInTransaction
            val deleted = dao.deleteById(entry.id)
            assert(deleted == 1)
            popped = entry.toModel()
        }
        return popped
    }

    // List oldest-first because tests compare fake and Room stack order directly.
    override fun listForNote(noteId: Long, direction: UndoDirection): List<UndoEntry> {
        assert(noteId > 0L)
        return dao.listForNote(noteId, direction.name).map { it.toModel() }
    }

    // Clear one stack direction because new edits invalidate redo only.
    override fun deleteForNote(noteId: Long, direction: UndoDirection) {
        assert(noteId > 0L)
        dao.deleteForNote(noteId, direction.name)
    }

    // Clear all stack rows because save and destruction remove edit history.
    override fun deleteForNote(noteId: Long) {
        assert(noteId > 0L)
        dao.deleteForNote(noteId)
    }
}
