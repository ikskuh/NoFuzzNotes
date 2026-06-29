package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.repository.fake.FakeNoteRepository
import com.nofuzznotes.domain.repository.fake.FakeSnapshotRepository
import com.nofuzznotes.domain.repository.fake.FakeUndoRedoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class UndoRedoServiceTest {
    // Verify a typed edit writes the draft and creates an undo operation because both must be durable.
    @Test
    fun typingCreatesUndoOperationAndUndoRedoRestoreText() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id

        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "abc", 0, 3))
        val undo = fixture.undoRedoService.undo(noteId, EditorMode.Edit)
        val redo = fixture.undoRedoService.redo(noteId, EditorMode.Edit)

        assertEquals("", undo.note.content)
        assertEquals(0, undo.cursor)
        assertEquals("abc", redo.note.content)
        assertEquals(3, redo.cursor)
        assertEquals(EditorMode.Edit, redo.mode)
    }

    // Verify selection ranges are restored because cursor-only undo would lose selected user context.
    @Test
    fun undoRedoRestoreCursorAndSelection() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        val edit = edit(
            kind = UndoOperationKind.Replacement,
            before = "hello world",
            after = "hello Kotlin",
            cursorBefore = 6,
            cursorAfter = 12,
            selectionBefore = TextSelection(6, 11),
            selectionAfter = TextSelection(6, 12),
        )
        fixture.lifecycle.editDraft(noteId, "hello world")
        fixture.undoRedoService.recordEdit(noteId, edit)

        val undo = fixture.undoRedoService.undo(noteId, EditorMode.Edit)
        val redo = fixture.undoRedoService.redo(noteId, EditorMode.Edit)

        assertEquals(TextSelection(6, 11), undo.selection)
        assertEquals(TextSelection(6, 12), redo.selection)
    }

    // Verify a new edit clears redo because the old future no longer matches the current draft branch.
    @Test
    fun newEditClearsRedoStack() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "a", 0, 1))
        fixture.undoRedoService.undo(noteId, EditorMode.Edit)

        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "b", 0, 1))

        assertFalse(fixture.undoRedoService.canRedo(noteId, EditorMode.Edit))
        assertEquals(emptyList<Nothing>(), fixture.undoRedo.listForNote(noteId, UndoDirection.Redo))
    }


    // Verify grouped typing creates one durable undo row because the boundary detector owns edit coalescing.
    @Test
    fun continuousTypingGroupsIntoOneUndoOperation() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        val start = Instant.parse("2026-06-28T10:00:00Z")

        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "a", 0, 1, timestamp = start))
        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "a", "ab", 1, 2, timestamp = start.plusMillis(100)))

        val stack = fixture.undoRedo.listForNote(noteId, UndoDirection.Undo)
        assertEquals(1, stack.size)
        assertEquals("", stack.single().textBefore)
        assertEquals("ab", stack.single().textAfter)
    }


    // Verify grouped deletion creates one durable undo row because repeated backspace should restore the whole run.
    @Test
    fun continuousDeletionGroupsIntoOneUndoOperation() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        val start = Instant.parse("2026-06-28T10:00:00Z")
        fixture.lifecycle.editDraft(noteId, "abc")

        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Deletion, "abc", "ab", 3, 2, timestamp = start))
        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Deletion, "ab", "a", 2, 1, timestamp = start.plusMillis(100)))

        val stack = fixture.undoRedo.listForNote(noteId, UndoDirection.Undo)
        assertEquals(1, stack.size)
        assertEquals("abc", stack.single().textBefore)
        assertEquals("a", stack.single().textAfter)
    }

    // Verify save clears both stacks because explicit save ends the edit history window.
    @Test
    fun saveClearsUndoRedo() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "a", 0, 1))
        fixture.undoRedoService.undo(noteId, EditorMode.Edit)

        fixture.lifecycle.saveNote(noteId)

        assertFalse(fixture.undoRedoService.canUndo(noteId, EditorMode.Edit))
        assertFalse(fixture.undoRedoService.canRedo(noteId, EditorMode.Edit))
    }

    // Verify all required operation kinds can be stored and replayed because event source does not matter.
    @Test
    fun requiredOperationKindsAreUndoable() {
        val kinds = listOf(
            UndoOperationKind.Deletion,
            UndoOperationKind.Paste,
            UndoOperationKind.Cut,
            UndoOperationKind.Replacement,
            UndoOperationKind.Clear,
            UndoOperationKind.Autocorrect,
            UndoOperationKind.CancelEdit,
            UndoOperationKind.SnapshotRestore,
        )
        kinds.forEachIndexed { index, kind ->
            val fixture = fixture()
            val noteId = fixture.lifecycle.createNote().note.id
            val before = "before-$index"
            val after = "after-$index"
            fixture.lifecycle.editDraft(noteId, before)

            fixture.undoRedoService.recordEdit(noteId, edit(kind, before, after, before.length, after.length))
            val undo = fixture.undoRedoService.undo(noteId, if (kind == UndoOperationKind.CancelEdit) EditorMode.View else EditorMode.Edit)
            val redo = fixture.undoRedoService.redo(noteId, EditorMode.Edit)

            assertEquals(before, undo.note.content)
            assertEquals(after, redo.note.content)
        }
    }

    // Verify repository-backed stacks survive recreating services because state is not held in the service.
    @Test
    fun undoRedoSurvivesServiceRecreationAndEditorReentry() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "a", 0, 1))
        val recreated = UndoRedoService(fixture.notes, fixture.undoRedo)

        assertTrue(recreated.canUndo(noteId, EditorMode.Edit))
        assertEquals("", recreated.undo(noteId, EditorMode.Edit).note.content)
    }

    // Verify stacks are per note because editing one note must not affect another note's history.
    @Test
    fun undoRedoIsPerNote() {
        val fixture = fixture()
        val first = fixture.lifecycle.createNote().note.id
        val second = fixture.lifecycle.createNote().note.id
        fixture.undoRedoService.recordEdit(first, edit(UndoOperationKind.Typing, "", "a", 0, 1))

        assertTrue(fixture.undoRedoService.canUndo(first, EditorMode.Edit))
        assertFalse(fixture.undoRedoService.canUndo(second, EditorMode.Edit))
    }

    // Verify trash state changes do not clear stacks because deletion is recoverable until destruction.
    @Test
    fun trashAndUntrashDoNotClearUndoRedoButDestroyDoes() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "a", 0, 1))

        fixture.notes.markDeleted(noteId)
        fixture.notes.clearDeleted(noteId)
        assertTrue(fixture.undoRedoService.canUndo(noteId, EditorMode.Edit))
        fixture.notes.destroy(noteId)
        fixture.undoRedo.deleteForNote(noteId)

        assertFalse(fixture.undoRedoService.canUndo(noteId, EditorMode.Edit))
    }

    // Verify trashed notes reject new edits because deleted drafts are view-only until restored.
    @Test
    fun trashedNotesCannotRecordNewEdits() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.notes.markDeleted(noteId)

        assertThrows(IllegalStateException::class.java) {
            fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "a", 0, 1))
        }
    }

    // Verify snapshot view never exposes draft undo because historical content is read-only.
    @Test
    fun snapshotViewDoesNotAllowUndo() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "a", 0, 1))

        assertFalse(fixture.undoRedoService.canUndo(noteId, EditorMode.ViewSnapshot))
        assertThrows(IllegalStateException::class.java) { fixture.undoRedoService.undo(noteId, EditorMode.ViewSnapshot) }
        assertTrue(fixture.undoRedoService.canUndo(noteId, EditorMode.Edit))
    }

    // Verify snapshot view never exposes draft redo because historical content is read-only.
    @Test
    fun snapshotViewDoesNotAllowRedo() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.undoRedoService.recordEdit(noteId, edit(UndoOperationKind.Typing, "", "a", 0, 1))
        fixture.undoRedoService.undo(noteId, EditorMode.Edit)

        assertFalse(fixture.undoRedoService.canRedo(noteId, EditorMode.ViewSnapshot))
        assertThrows(IllegalStateException::class.java) { fixture.undoRedoService.redo(noteId, EditorMode.ViewSnapshot) }
        assertTrue(fixture.undoRedoService.canRedo(noteId, EditorMode.Edit))
    }

    // Verify cancel edit is the only draft view-mode undo and controls edit/view transitions when replayed.
    @Test
    fun cancelEditUndoRedoModeRules() {
        val fixture = fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "saved")
        fixture.lifecycle.saveNote(noteId)
        fixture.lifecycle.editDraft(noteId, "unsaved")
        fixture.lifecycle.cancelEdit(noteId)

        assertTrue(fixture.undoRedoService.canUndo(noteId, EditorMode.View))
        val undo = fixture.undoRedoService.undo(noteId, EditorMode.View)
        val redo = fixture.undoRedoService.redo(noteId, undo.mode)

        assertEquals(EditorMode.Edit, undo.mode)
        assertEquals("unsaved", undo.note.content)
        assertEquals(EditorMode.View, redo.mode)
        assertEquals("saved", redo.note.content)
    }

    private data class Fixture(
        val notes: FakeNoteRepository,
        val undoRedo: FakeUndoRedoRepository,
        val lifecycle: NoteLifecycleService,
        val undoRedoService: UndoRedoService,
    )

    // Build isolated services because stack behavior must not depend on test execution order.
    private fun fixture(): Fixture {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val snapshots = FakeSnapshotRepository(clock)
        val undoRedo = FakeUndoRedoRepository(clock)
        return Fixture(
            notes = notes,
            undoRedo = undoRedo,
            lifecycle = NoteLifecycleService(notes, snapshots, undoRedo),
            undoRedoService = UndoRedoService(notes, undoRedo),
        )
    }

    // Build explicit edit events because operation kind is the only behavior that varies across cases.
    private fun edit(
        kind: UndoOperationKind,
        before: String,
        after: String,
        cursorBefore: Int,
        cursorAfter: Int,
        selectionBefore: TextSelection = TextSelection(cursorBefore, cursorBefore),
        selectionAfter: TextSelection = TextSelection(cursorAfter, cursorAfter),
        timestamp: Instant? = null,
    ) = TextEdit(kind, 0, before, after, cursorBefore, cursorAfter, selectionBefore, selectionAfter, timestamp)
}
