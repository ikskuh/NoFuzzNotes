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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TrashServiceTest {
    // Verify trash flips only deleted because recoverable deletion must preserve user work.
    @Test
    fun trashSetsDeletedMovesListsAndPreservesDraftSnapshotsUndoRedoAndEdited() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "Saved\nbody")
        val edited = fixture.notes.read(noteId)!!.edited
        val snapshot = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        val undo = fixture.undoRedo.create(noteId, UndoDirection.Undo, UndoOperationKind.Replacement, 0, "a", "b", 0, 1)
        fixture.clock.set(Instant.parse("2026-06-28T11:00:00Z"))

        val trashed = fixture.trash.trashNote(noteId)

        assertEquals(Instant.parse("2026-06-28T11:00:00Z"), trashed.deleted)
        assertEquals(edited, trashed.edited)
        assertEquals(emptyList<Nothing>(), fixture.notes.listNormal())
        assertEquals(listOf(trashed), fixture.notes.listTrash())
        assertEquals("Saved\nbody", fixture.notes.read(noteId)!!.content)
        assertEquals(listOf(snapshot), fixture.snapshots.listForNote(noteId))
        assertEquals(listOf(undo), fixture.undoRedo.listForNote(noteId, UndoDirection.Undo))
    }

    // Verify untrash clears only deleted because restore from trash must not rewrite note data.
    @Test
    fun untrashClearsDeletedAndPreservesAllNoteDataAndEdited() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "Draft")
        val edited = fixture.notes.read(noteId)!!.edited
        val snapshot = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        val undo = fixture.undoRedo.create(noteId, UndoDirection.Undo, UndoOperationKind.Replacement, 0, "a", "b", 0, 1)
        fixture.trash.trashNote(noteId)
        fixture.clock.set(Instant.parse("2026-06-28T12:00:00Z"))

        val restored = fixture.trash.untrashNote(noteId)

        assertNull(restored.deleted)
        assertEquals(edited, restored.edited)
        assertEquals("Draft", restored.content)
        assertEquals(listOf(restored), fixture.notes.listNormal())
        assertEquals(emptyList<Nothing>(), fixture.notes.listTrash())
        assertEquals(listOf(snapshot), fixture.snapshots.listForNote(noteId))
        assertEquals(listOf(undo), fixture.undoRedo.listForNote(noteId, UndoDirection.Undo))
    }

    // Verify permanent delete cascades all note-owned data and refuses active notes.
    @Test
    fun destroyRemovesOnlyTrashedNotesSnapshotsAndUndoRedo() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "Draft")
        fixture.lifecycle.saveNote(noteId)
        fixture.undoRedo.create(noteId, UndoDirection.Undo, UndoOperationKind.Replacement, 0, "a", "b", 0, 1)

        assertThrows(IllegalStateException::class.java) { fixture.trash.destroyNote(noteId) }
        fixture.trash.trashNote(noteId)
        fixture.trash.destroyNote(noteId)

        assertNull(fixture.notes.read(noteId))
        assertEquals(emptyList<Nothing>(), fixture.snapshots.listForNote(noteId))
        assertEquals(emptyList<Nothing>(), fixture.undoRedo.listForNote(noteId, UndoDirection.Undo))
    }

    // Verify empty trash uses trash scope because active notes must survive destructive cleanup.
    @Test
    fun emptyTrashDestroysAllTrashedNotesAndLeavesNormalNotes() {
        val fixture = fixture()
        val active = fixture.lifecycle.createNote().note.id
        val trashedOne = fixture.lifecycle.createNote().note.id
        val trashedTwo = fixture.lifecycle.createNote().note.id
        fixture.trash.trashNote(trashedOne)
        fixture.trash.trashNote(trashedTwo)

        val count = fixture.trash.emptyTrash()

        assertEquals(2, count)
        assertTrue(fixture.notes.read(active) != null)
        assertNull(fixture.notes.read(trashedOne))
        assertNull(fixture.notes.read(trashedTwo))
    }

    // Verify trashed notes are read-only but still expose displayed text for export and share.
    @Test
    fun trashedNotesAreViewableNotEditableWithoutHistoryAndExportableShareable() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "Title\nbody")
        val snapshot = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        fixture.trash.trashNote(noteId)

        val open = fixture.lifecycle.openNote(noteId)
        val trashView = fixture.trash.openTrashedNote(noteId)

        assertEquals(EditorMode.View, open.mode)
        assertFalse(open.canEdit)
        assertEquals(EditorMode.View, trashView.mode)
        assertFalse(trashView.canEdit)
        assertFalse(trashView.canOpenHistory)
        assertEquals("Title\nbody", trashView.displayedContent.exportText())
        assertEquals("Title\nbody", trashView.displayedContent.shareText())
        assertThrows(IllegalStateException::class.java) { fixture.lifecycle.editDraft(noteId, "blocked") }
        assertFalse(fixture.history.canOpenHistory(noteId))
        assertThrows(IllegalStateException::class.java) { fixture.history.listSnapshotsNewestFirst(noteId) }
        assertThrows(IllegalStateException::class.java) { fixture.history.openSnapshot(noteId, snapshot.id) }
    }

    private data class Fixture(
        val clock: TestClock,
        val notes: FakeNoteRepository,
        val snapshots: FakeSnapshotRepository,
        val undoRedo: FakeUndoRedoRepository,
        val lifecycle: NoteLifecycleService,
        val history: HistoryService,
        val trash: TrashService,
    )

    // Build isolated collaborators because trash state must not leak between lifecycle tests.
    private fun fixture(): Fixture {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val snapshots = FakeSnapshotRepository(clock)
        val undoRedo = FakeUndoRedoRepository(clock)
        return Fixture(
            clock = clock,
            notes = notes,
            snapshots = snapshots,
            undoRedo = undoRedo,
            lifecycle = NoteLifecycleService(notes, snapshots, undoRedo),
            history = HistoryService(notes, snapshots, undoRedo),
            trash = TrashService(notes, snapshots, undoRedo),
        )
    }
}
