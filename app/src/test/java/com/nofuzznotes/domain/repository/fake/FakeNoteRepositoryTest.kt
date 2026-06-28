package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.time.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FakeNoteRepositoryTest {
    // Verify note creation persists an empty row because empty notes are valid domain objects.
    @Test
    fun createEmptyImmediatelyCreatesNoteRow() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val repository = FakeNoteRepository(clock)

        val note = repository.createEmpty()

        assertEquals(1L, note.id)
        assertEquals("", note.content)
        assertEquals(clock.now(), note.created)
        assertEquals(clock.now(), note.edited)
        assertNull(note.deleted)
        assertEquals(note, repository.read(note.id))
        assertEquals(listOf(note), repository.listNormal())
    }

    // Verify trash state controls list membership because normal lists must exclude recoverable deletions.
    @Test
    fun trashMovesNoteFromNormalListToTrashList() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val repository = FakeNoteRepository(clock)
        val note = repository.createEmpty()
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))

        val trashed = repository.markDeleted(note.id)

        assertTrue(trashed.isTrashed())
        assertEquals(Instant.parse("2026-06-28T10:01:00Z"), trashed.deleted)
        assertEquals(emptyList<Nothing>(), repository.listNormal())
        assertEquals(listOf(trashed), repository.listTrash())
    }

    // Verify deleted can be cleared because trash is recoverable until permanent destruction.
    @Test
    fun clearDeletedRestoresNormalListMembership() {
        val repository = FakeNoteRepository(TestClock(Instant.parse("2026-06-28T10:00:00Z")))
        val note = repository.createEmpty()
        repository.markDeleted(note.id)

        val restored = repository.clearDeleted(note.id)

        assertFalse(restored.isTrashed())
        assertEquals(listOf(restored), repository.listNormal())
        assertEquals(emptyList<Nothing>(), repository.listTrash())
    }

    // Verify default normal list order is newest edited first because this is the required notebook ordering.
    @Test
    fun normalListSortsByEditedDescending() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val repository = FakeNoteRepository(clock)
        val first = repository.createEmpty()
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))
        val second = repository.createEmpty()
        clock.set(Instant.parse("2026-06-28T10:02:00Z"))
        val updatedFirst = repository.updateContent(first.id, "first edited")

        assertEquals(listOf(updatedFirst, second), repository.listNormal())
    }

    // Verify empty notes remain until explicit destruction because creation alone must not be cleaned up.
    @Test
    fun emptyNoteRemainsUntilDestroyed() {
        val repository = FakeNoteRepository(TestClock(Instant.parse("2026-06-28T10:00:00Z")))
        val note = repository.createEmpty()

        assertEquals(note, repository.read(note.id))
        repository.destroy(note.id)
        assertNull(repository.read(note.id))
    }
}
