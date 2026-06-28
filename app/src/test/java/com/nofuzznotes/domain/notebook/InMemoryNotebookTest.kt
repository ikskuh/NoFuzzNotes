package com.nofuzznotes.domain.notebook

import com.nofuzznotes.core.time.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class InMemoryNotebookTest {
    // Verify new notes start as pending drafts because first save must create history.
    @Test
    fun createNoteStoresDraftAndHasPendingChanges() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notebook = InMemoryNotebook(clock)

        val entry = notebook.createNote("Title\r\nBody")

        assertEquals(1L, entry.note.id)
        assertEquals("Title\nBody", entry.note.content)
        assertEquals("Title", entry.note.title)
        assertEquals(clock.now(), entry.note.created)
        assertEquals(clock.now(), entry.note.edited)
        assertNull(entry.note.deleted)
        assertTrue(entry.hasPendingChanges)
        assertEquals(emptyList<Nothing>(), entry.snapshots)
        assertEquals(listOf(entry), notebook.listNotes())
        assertEquals(emptyList<Nothing>(), notebook.listTrash())
    }

    // Verify draft edits update content time because the current draft is the durable user work.
    @Test
    fun editDraftReplacesContentAndUpdatesEdited() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notebook = InMemoryNotebook(clock)
        val created = notebook.createNote("Old")
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))

        val edited = notebook.editDraft(created.note.id, "New\rText")

        assertEquals("New\nText", edited.note.content)
        assertEquals(Instant.parse("2026-06-28T10:01:00Z"), edited.note.edited)
        assertEquals(created.note.created, edited.note.created)
        assertTrue(edited.hasPendingChanges)
    }

    // Verify save is immutable and idempotent because repeated saves must not duplicate identical history.
    @Test
    fun saveSnapshotCreatesOnlyChangedVersions() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notebook = InMemoryNotebook(clock)
        val noteId = notebook.createNote("First").note.id
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))

        val firstSnapshot = notebook.saveSnapshot(noteId)
        val afterFirstSave = notebook.readNote(noteId)!!
        val duplicateSave = notebook.saveSnapshot(noteId)

        assertEquals(1L, firstSnapshot!!.id)
        assertEquals(noteId, firstSnapshot.noteId)
        assertEquals("First", firstSnapshot.content)
        assertEquals("First", firstSnapshot.title)
        assertFalse(afterFirstSave.hasPendingChanges)
        assertNull(duplicateSave)
        assertEquals(listOf(firstSnapshot), notebook.readNote(noteId)!!.snapshots)
    }

    // Verify a later save appends history because snapshots represent explicit save points.
    @Test
    fun saveAfterEditAppendsSnapshot() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notebook = InMemoryNotebook(clock)
        val noteId = notebook.createNote("First").note.id
        val firstSnapshot = notebook.saveSnapshot(noteId)!!
        clock.set(Instant.parse("2026-06-28T10:02:00Z"))
        notebook.editDraft(noteId, "Second")

        val secondSnapshot = notebook.saveSnapshot(noteId)!!
        val entry = notebook.readNote(noteId)!!

        assertEquals(2L, secondSnapshot.id)
        assertEquals(listOf(firstSnapshot, secondSnapshot), entry.snapshots)
        assertFalse(entry.hasPendingChanges)
    }

    // Verify restore changes only the draft because saved history must remain immutable.
    @Test
    fun restoreSnapshotCopiesSavedContentIntoDraft() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notebook = InMemoryNotebook(clock)
        val noteId = notebook.createNote("First").note.id
        val firstSnapshot = notebook.saveSnapshot(noteId)!!
        notebook.editDraft(noteId, "Second")
        notebook.saveSnapshot(noteId)!!
        clock.set(Instant.parse("2026-06-28T10:03:00Z"))

        val restored = notebook.restoreSnapshot(noteId, firstSnapshot.id)

        assertEquals("First", restored.note.content)
        assertEquals(2, restored.snapshots.size)
        assertTrue(restored.hasPendingChanges)
        assertEquals(Instant.parse("2026-06-28T10:03:00Z"), restored.note.edited)
    }


    // Verify default listing is newest edited first because note list sorting starts from edited DESC.
    @Test
    fun listNotesUsesEditedDescendingByDefault() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notebook = InMemoryNotebook(clock)
        val first = notebook.createNote("First")
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))
        val second = notebook.createNote("Second")
        clock.set(Instant.parse("2026-06-28T10:02:00Z"))
        val editedFirst = notebook.editDraft(first.note.id, "First edited")

        assertEquals(listOf(editedFirst.note.id, second.note.id), notebook.listNotes().map { it.note.id })
    }

    // Verify duplicate saves still touch edited because pressing save is persisted user intent.
    @Test
    fun duplicateSaveTouchesEditedWithoutCreatingSnapshot() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notebook = InMemoryNotebook(clock)
        val noteId = notebook.createNote("First").note.id
        notebook.saveSnapshot(noteId)
        clock.set(Instant.parse("2026-06-28T10:05:00Z"))

        val duplicate = notebook.saveSnapshot(noteId)
        val entry = notebook.readNote(noteId)!!

        assertNull(duplicate)
        assertEquals(Instant.parse("2026-06-28T10:05:00Z"), entry.note.edited)
        assertEquals(1, entry.snapshots.size)
    }

    // Verify trash lifecycle preserves content and history until permanent destruction.
    @Test
    fun trashUntrashAndDestroyNote() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notebook = InMemoryNotebook(clock)
        val noteId = notebook.createNote("Trash me").note.id
        val snapshot = notebook.saveSnapshot(noteId)!!
        clock.set(Instant.parse("2026-06-28T10:04:00Z"))

        val trashed = notebook.trashNote(noteId)
        assertTrue(trashed.note.isTrashed())
        assertEquals(Instant.parse("2026-06-28T10:04:00Z"), trashed.note.deleted)
        assertEquals(listOf(snapshot), trashed.snapshots)
        assertEquals(emptyList<Nothing>(), notebook.listNotes())
        assertEquals(listOf(trashed), notebook.listTrash())

        val restored = notebook.untrashNote(noteId)
        assertFalse(restored.note.isTrashed())
        assertEquals(listOf(restored), notebook.listNotes())
        assertEquals(emptyList<Nothing>(), notebook.listTrash())

        notebook.trashNote(noteId)
        notebook.destroyNote(noteId)
        assertNull(notebook.readNote(noteId))
        assertEquals(emptyList<Nothing>(), notebook.listTrash())
    }
}
