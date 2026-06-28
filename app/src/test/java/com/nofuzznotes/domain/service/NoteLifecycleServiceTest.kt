package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.repository.fake.FakeNoteRepository
import com.nofuzznotes.domain.repository.fake.FakeSnapshotRepository
import com.nofuzznotes.domain.repository.fake.FakeUndoRedoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NoteLifecycleServiceTest {
    // Verify a new note opens editable because a note with no snapshots always has pending changes.
    @Test
    fun createNoteCreatesEmptyRowAndOpensEditMode() {
        val fixture = fixture()

        val result = fixture.service.createNote()

        assertEquals("", result.note.content)
        assertEquals(EditorMode.Edit, result.mode)
        assertNull(result.latestSnapshot)
        assertTrue(result.hasPendingChanges)
        assertEquals(result.note, fixture.notes.read(result.note.id))
    }

    // Verify open mode is derived from content because previous editor state must not be stored.
    @Test
    fun openNoteRecalculatesModeFromLatestSnapshot() {
        val fixture = fixture()
        val noteId = fixture.service.createNote().note.id

        assertEquals(EditorMode.Edit, fixture.service.openNote(noteId).mode)
        fixture.service.saveNote(noteId)
        assertEquals(EditorMode.View, fixture.service.openNote(noteId).mode)
        fixture.service.editDraft(noteId, "changed")
        assertEquals(EditorMode.Edit, fixture.service.openNote(noteId).mode)
    }

    // Verify draft edits are immediately durable because leaving the editor must not lose work.
    @Test
    fun editDraftUpdatesContentAndEditedImmediately() {
        val fixture = fixture()
        val noteId = fixture.service.createNote().note.id
        fixture.clock.set(Instant.parse("2026-06-28T10:01:00Z"))

        val result = fixture.service.editDraft(noteId, "Title\r\nBody")

        assertEquals("Title\nBody", result.note.content)
        assertEquals(Instant.parse("2026-06-28T10:01:00Z"), result.note.edited)
        assertEquals(result.note, fixture.notes.read(noteId))
        assertEquals(EditorMode.Edit, result.mode)
    }

    // Verify saving creates only meaningful snapshots while still recording every save click.
    @Test
    fun saveCreatesSnapshotsAsSpecifiedAndClearsUndoRedo() {
        val fixture = fixture()
        val noteId = fixture.service.createNote().note.id
        fixture.undoRedo.create(noteId, UndoDirection.Undo, UndoOperationKind.Replacement, 0, "", "x", 0, 1)
        fixture.clock.set(Instant.parse("2026-06-28T10:01:00Z"))

        val firstSave = fixture.service.saveNote(noteId)
        val duplicateSave = fixture.service.saveNote(noteId)
        fixture.service.editDraft(noteId, "changed")
        val changedSave = fixture.service.saveNote(noteId)

        assertEquals("", firstSave.createdSnapshot!!.content)
        assertNull(duplicateSave.createdSnapshot)
        assertEquals("changed", changedSave.createdSnapshot!!.content)
        assertEquals(EditorMode.View, changedSave.mode)
        assertEquals(emptyList<Nothing>(), fixture.undoRedo.listForNote(noteId, UndoDirection.Undo))
        assertEquals(2, fixture.snapshots.listForNote(noteId).size)
    }

    // Verify save updates edited even when no snapshot is needed because explicit save is user-visible state.
    @Test
    fun saveAlwaysUpdatesEdited() {
        val fixture = fixture()
        val noteId = fixture.service.createNote().note.id
        fixture.service.saveNote(noteId)
        fixture.clock.set(Instant.parse("2026-06-28T10:02:00Z"))

        val result = fixture.service.saveNote(noteId)

        assertNull(result.createdSnapshot)
        assertEquals(Instant.parse("2026-06-28T10:02:00Z"), result.note.edited)
    }

    // Verify cancel uses the latest save because unsaved content should be recoverable through undo.
    @Test
    fun cancelEditRequiresSnapshotResetsDraftAndCreatesUndoEntry() {
        val fixture = fixture()
        val noteId = fixture.service.createNote().note.id
        assertFalse(fixture.service.canCancelEdit(noteId))
        fixture.service.editDraft(noteId, "saved")
        fixture.service.saveNote(noteId)
        fixture.service.editDraft(noteId, "unsaved")
        fixture.clock.set(Instant.parse("2026-06-28T10:03:00Z"))

        val result = fixture.service.cancelEdit(noteId)

        assertEquals("saved", result.note.content)
        assertEquals(Instant.parse("2026-06-28T10:03:00Z"), result.note.edited)
        assertEquals(EditorMode.View, result.mode)
        assertEquals("unsaved", result.undoEntry.textBefore)
        assertEquals("saved", result.undoEntry.textAfter)
        assertTrue(fixture.service.canCancelEdit(noteId))
    }

    private data class Fixture(
        val clock: TestClock,
        val notes: FakeNoteRepository,
        val snapshots: FakeSnapshotRepository,
        val undoRedo: FakeUndoRedoRepository,
        val service: NoteLifecycleService,
    )

    // Build isolated repositories because service behavior must not depend on shared state.
    private fun fixture(): Fixture {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val snapshots = FakeSnapshotRepository(clock)
        val undoRedo = FakeUndoRedoRepository(clock)
        return Fixture(clock, notes, snapshots, undoRedo, NoteLifecycleService(notes, snapshots, undoRedo))
    }
}
