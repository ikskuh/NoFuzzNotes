package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.TestClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FakeUndoRedoRepositoryTest {
    // Verify undo rows can be stored separately by direction because undo and redo are independent stacks.
    @Test
    fun createsAndListsUndoRedoEntries() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val repository = FakeUndoRedoRepository(clock)
        val undo = repository.create(
            noteId = 1L,
            direction = UndoDirection.Undo,
            operationKind = UndoOperationKind.Replacement,
            position = 0,
            textBefore = "",
            textAfter = "a",
            cursorBefore = 0,
            cursorAfter = 1,
        )
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))
        val redo = repository.create(
            noteId = 1L,
            direction = UndoDirection.Redo,
            operationKind = UndoOperationKind.Replacement,
            position = 0,
            textBefore = "a",
            textAfter = "",
            cursorBefore = 1,
            cursorAfter = 0,
        )

        assertEquals(listOf(undo), repository.listForNote(1L, UndoDirection.Undo))
        assertEquals(listOf(redo), repository.listForNote(1L, UndoDirection.Redo))
    }


    // Verify stack APIs expose the latest row first because services should not manage row ids directly.
    @Test
    fun peeksAndPopsTopStackEntry() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val repository = FakeUndoRedoRepository(clock)
        repository.create(1L, UndoDirection.Undo, UndoOperationKind.Typing, 0, "", "a", 0, 1)
        val second = repository.create(1L, UndoDirection.Undo, UndoOperationKind.Typing, 1, "a", "ab", 1, 2)

        assertEquals(second, repository.peek(1L, UndoDirection.Undo))
        assertEquals(second, repository.pop(1L, UndoDirection.Undo))
        assertEquals(1, repository.listForNote(1L, UndoDirection.Undo).size)
    }

    // Verify redo can be cleared without touching undo because a new edit invalidates only future history.
    @Test
    fun deletesOnlyRequestedDirection() {
        val repository = FakeUndoRedoRepository(TestClock(Instant.parse("2026-06-28T10:00:00Z")))
        val undo = repository.create(1L, UndoDirection.Undo, UndoOperationKind.Typing, 0, "", "a", 0, 1)
        repository.create(1L, UndoDirection.Redo, UndoOperationKind.Typing, 0, "", "a", 0, 1)

        repository.deleteForNote(1L, UndoDirection.Redo)

        assertEquals(listOf(undo), repository.listForNote(1L, UndoDirection.Undo))
        assertEquals(emptyList<Nothing>(), repository.listForNote(1L, UndoDirection.Redo))
    }

    // Verify note destruction can clear edit-stack rows because orphan undo records are invalid.
    @Test
    fun deletesEntriesForNote() {
        val repository = FakeUndoRedoRepository(TestClock(Instant.parse("2026-06-28T10:00:00Z")))
        repository.create(1L, UndoDirection.Undo, UndoOperationKind.Replacement, 0, "", "a", 0, 1)

        repository.deleteForNote(1L)

        assertEquals(emptyList<Nothing>(), repository.listForNote(1L, UndoDirection.Undo))
    }
}
