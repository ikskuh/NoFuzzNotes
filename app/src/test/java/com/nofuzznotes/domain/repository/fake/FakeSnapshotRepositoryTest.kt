package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.time.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FakeSnapshotRepositoryTest {
    // Verify a new note can have no snapshots because first save is a separate later operation.
    @Test
    fun newNoteHasNoSnapshots() {
        val repository = FakeSnapshotRepository(TestClock(Instant.parse("2026-06-28T10:00:00Z")))

        assertEquals(emptyList<Nothing>(), repository.listForNote(1L))
    }

    // Verify snapshots are stored by note because history must not leak across note identities.
    @Test
    fun createsAndListsSnapshotsForNote() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val repository = FakeSnapshotRepository(clock)
        val first = repository.create(noteId = 1L, content = "Title\r\nBody")
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))
        val second = repository.create(noteId = 1L, content = "Next")
        repository.create(noteId = 2L, content = "Other")

        assertEquals("Title\nBody", first.content)
        assertEquals("Title", first.title)
        assertEquals(listOf(first, second), repository.listForNote(1L))
        assertEquals(first, repository.read(first.id))
    }

    // Verify note destruction can remove history because permanent deletion must erase the aggregate.
    @Test
    fun deletesSnapshotsForNote() {
        val repository = FakeSnapshotRepository(TestClock(Instant.parse("2026-06-28T10:00:00Z")))
        val snapshot = repository.create(noteId = 1L, content = "Saved")

        repository.deleteForNote(1L)

        assertEquals(emptyList<Nothing>(), repository.listForNote(1L))
        assertNull(repository.read(snapshot.id))
    }
}
