package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.repository.fake.FakeNoteRepository
import com.nofuzznotes.domain.repository.fake.FakeSnapshotRepository
import com.nofuzznotes.domain.repository.fake.FakeUndoRedoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class HistoryServiceTest {
    // Verify history exposes only saved versions and orders them for the newest-first UI.
    @Test
    fun snapshotsAreListedNewestFirstWithContentDerivedTitles() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "First\nbody")
        fixture.clock.set(Instant.parse("2026-06-28T10:01:00Z"))
        val first = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        fixture.lifecycle.editDraft(noteId, "Second\nbody")
        fixture.clock.set(Instant.parse("2026-06-28T10:02:00Z"))
        val second = fixture.lifecycle.saveNote(noteId).createdSnapshot!!

        val result = fixture.history.listSnapshotsNewestFirst(noteId)

        assertEquals(listOf(second.id, first.id), result.map { it.id })
        assertEquals(listOf("Second", "First"), result.map { it.title })
    }

    // Verify equal timestamps still use save order because multiple saves can happen in one second.
    @Test
    fun snapshotsWithSameTimestampUseNewestSaveFirst() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "First")
        val first = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        fixture.lifecycle.editDraft(noteId, "Second")
        val second = fixture.lifecycle.saveNote(noteId).createdSnapshot!!

        val result = fixture.history.listSnapshotsNewestFirst(noteId)

        assertEquals(listOf(second.id, first.id), result.map { it.id })
    }

    // Verify an empty first line stays empty because snapshot titles must not invent fallback text.
    @Test
    fun emptySnapshotTitleRemainsEmpty() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.saveNote(noteId)

        val result = fixture.history.listSnapshotsNewestFirst(noteId)

        assertEquals("", result.single().title)
    }

    // Verify history cannot be opened before a save because there is no immutable version to display.
    @Test
    fun zeroSnapshotsMeansHistoryUnavailable() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id

        assertFalse(fixture.history.canOpenHistory(noteId))
    }

    // Verify history cannot be opened with pending changes because rollback must not hide unsaved edits.
    @Test
    fun pendingChangesMeanHistoryUnavailable() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.saveNote(noteId)
        fixture.lifecycle.editDraft(noteId, "pending")

        assertFalse(fixture.history.canOpenHistory(noteId))
        assertThrows(IllegalStateException::class.java) { fixture.history.listSnapshotsNewestFirst(noteId) }
    }

    // Verify displayed content centralizes export/share selection for both draft and snapshot surfaces.
    @Test
    fun displayedContentUsesSameExportAndSharePathForDraftAndSnapshot() {
        val draft = DisplayedContent.draft("Draft\nbody")
        val snapshot = DisplayedContent.snapshot("Snapshot\nbody")

        assertEquals("Draft\nbody", draft.exportText())
        assertEquals("Draft\nbody", draft.shareText())
        assertEquals("Snapshot\nbody", snapshot.exportText())
        assertEquals("Snapshot\nbody", snapshot.shareText())
    }

    // Verify snapshot view is read-only while export and share use the displayed snapshot text.
    @Test
    fun snapshotViewIsReadOnlyAndExportsDisplayedSnapshotContent() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "Saved\nbody")
        val snapshot = fixture.lifecycle.saveNote(noteId).createdSnapshot!!

        val result = fixture.history.openSnapshot(noteId, snapshot.id)

        assertEquals(EditorMode.ViewSnapshot, result.mode)
        assertFalse(result.canEdit)
        assertEquals("Saved\nbody", result.displayedContent.exportText())
        assertEquals("Saved\nbody", result.displayedContent.shareText())
        assertEquals("Saved.txt", result.displayedContent.exportFilename)
    }

    // Verify restore is one edit-mode replacement and leaves immutable history unchanged.
    @Test
    fun restoreReplacesDraftUpdatesEditedCreatesUndoAndKeepsSnapshots() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "old")
        val old = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        fixture.lifecycle.editDraft(noteId, "new")
        fixture.clock.set(Instant.parse("2026-06-28T10:02:00Z"))
        val latest = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        fixture.clock.set(Instant.parse("2026-06-28T10:03:00Z"))

        val result = fixture.history.restoreSnapshot(noteId, old.id)

        assertEquals("old", result.note.content)
        assertEquals(Instant.parse("2026-06-28T10:03:00Z"), result.note.edited)
        assertEquals(EditorMode.Edit, result.mode)
        assertEquals(UndoOperationKind.SnapshotRestore, result.undoEntry.operationKind)
        assertEquals("new", result.undoEntry.textBefore)
        assertEquals("old", result.undoEntry.textAfter)
        assertEquals(listOf(old, latest), fixture.snapshots.listForNote(noteId))
    }

    // Verify the latest snapshot can be restored because the spec permits idempotent rollback.
    @Test
    fun restoringLatestSnapshotIsAllowed() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "latest")
        val latest = fixture.lifecycle.saveNote(noteId).createdSnapshot!!

        val result = fixture.history.restoreSnapshot(noteId, latest.id)

        assertEquals("latest", result.note.content)
        assertEquals(EditorMode.Edit, result.mode)
    }

    // Verify opening a snapshot refuses pending drafts because snapshot view enables restore actions.
    @Test
    fun openSnapshotRequiresNoPendingChanges() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "saved")
        val snapshot = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        fixture.lifecycle.editDraft(noteId, "pending")

        assertThrows(IllegalStateException::class.java) { fixture.history.openSnapshot(noteId, snapshot.id) }
    }

    // Verify restore refuses pending drafts because restoring must not discard unsaved user text.
    @Test
    fun restoreRequiresNoPendingChanges() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "saved")
        val snapshot = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        fixture.lifecycle.editDraft(noteId, "pending")

        assertThrows(IllegalStateException::class.java) { fixture.history.restoreSnapshot(noteId, snapshot.id) }
    }

    // Verify restore undo and redo replay the replacement as one operation.
    @Test
    fun undoAfterRestoreReturnsPreviousDraftAndRedoReappliesRestore() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "old")
        val old = fixture.lifecycle.saveNote(noteId).createdSnapshot!!
        fixture.lifecycle.editDraft(noteId, "new")
        fixture.clock.set(Instant.parse("2026-06-28T10:02:00Z"))
        fixture.lifecycle.saveNote(noteId)

        fixture.history.restoreSnapshot(noteId, old.id)
        val undo = fixture.undoRedoService.undo(noteId, EditorMode.Edit)
        val redo = fixture.undoRedoService.redo(noteId, EditorMode.Edit)

        assertEquals("new", undo.note.content)
        assertEquals("old", redo.note.content)
        assertEquals(1, fixture.undoRedo.listForNote(noteId, UndoDirection.Undo).size)
    }

    private data class Fixture(
        val clock: TestClock,
        val snapshots: FakeSnapshotRepository,
        val lifecycle: NoteLifecycleService,
        val history: HistoryService,
        val undoRedo: FakeUndoRedoRepository,
        val undoRedoService: UndoRedoService,
    )

    // Build isolated collaborators because history behavior must not leak across tests.
    private fun fixture(): Fixture {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val snapshots = FakeSnapshotRepository(clock)
        val undoRedo = FakeUndoRedoRepository(clock)
        return Fixture(
            clock = clock,
            snapshots = snapshots,
            lifecycle = NoteLifecycleService(notes, snapshots, undoRedo),
            history = HistoryService(notes, snapshots, undoRedo),
            undoRedo = undoRedo,
            undoRedoService = UndoRedoService(notes, undoRedo),
        )
    }
}
