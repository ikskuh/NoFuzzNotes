package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.undo.UndoBoundaryDetector
import com.nofuzznotes.domain.repository.NoteRepository
import com.nofuzznotes.domain.repository.UndoRedoRepository
import java.time.Instant

data class TextEdit(
    val operationKind: UndoOperationKind,
    val position: Int,
    val textBefore: String,
    val textAfter: String,
    val cursorBefore: Int,
    val cursorAfter: Int,
    val selectionBefore: TextSelection = TextSelection(cursorBefore, cursorBefore),
    val selectionAfter: TextSelection = TextSelection(cursorAfter, cursorAfter),
    val timestamp: Instant? = null,
) {
    init {
        assert(position >= 0)
        assert(cursorBefore >= 0)
        assert(cursorAfter >= 0)
    }
}

data class UndoRedoResult(
    val note: Note,
    val movedEntry: UndoEntry,
    val mode: EditorMode,
    val cursor: Int,
    val selection: TextSelection,
)

class UndoRedoService(
    private val notes: NoteRepository,
    private val undoRedo: UndoRedoRepository,
    private val boundaryDetector: UndoBoundaryDetector = UndoBoundaryDetector(),
) {
    // Record a new user edit because draft persistence and undo persistence must happen together.
    fun recordEdit(noteId: Long, edit: TextEdit): UndoEntry {
        assert(noteId > 0L)
        assert(notes.read(noteId)?.content == edit.textBefore)
        notes.updateContent(noteId, edit.textAfter)
        undoRedo.deleteForNote(noteId, UndoDirection.Redo)
        val previous = undoRedo.peek(noteId, UndoDirection.Undo)
        if (boundaryDetector.shouldGroup(previous, edit)) {
            val groupedPrevious = previous ?: error("Grouped edit requires a previous undo entry")
            undoRedo.pop(noteId, UndoDirection.Undo)
            return undoRedo.create(
                noteId = noteId,
                direction = UndoDirection.Undo,
                operationKind = groupedPrevious.operationKind,
                position = groupedPrevious.position,
                textBefore = groupedPrevious.textBefore,
                textAfter = edit.textAfter,
                cursorBefore = groupedPrevious.cursorBefore,
                cursorAfter = edit.cursorAfter,
                selectionBefore = groupedPrevious.selectionBefore,
                selectionAfter = edit.selectionAfter,
            )
        }
        return undoRedo.create(
            noteId = noteId,
            direction = UndoDirection.Undo,
            operationKind = edit.operationKind,
            position = edit.position,
            textBefore = edit.textBefore,
            textAfter = edit.textAfter,
            cursorBefore = edit.cursorBefore,
            cursorAfter = edit.cursorAfter,
            selectionBefore = edit.selectionBefore,
            selectionAfter = edit.selectionAfter,
        )
    }

    // Undo the top entry because the persisted undo stack is the source of editor recovery state.
    fun undo(noteId: Long, currentMode: EditorMode): UndoRedoResult {
        assert(noteId > 0L)
        val available = undoRedo.peek(noteId, UndoDirection.Undo) ?: error("Undo requires an available undo entry")
        check(canReplayUndoInMode(currentMode, available.operationKind)) { "Undo is not available in mode $currentMode" }
        val entry = undoRedo.pop(noteId, UndoDirection.Undo) ?: error("Undo requires an available undo entry")
        val note = notes.updateContent(noteId, entry.textBefore)
        val redoEntry = copyToDirection(entry, UndoDirection.Redo)
        return UndoRedoResult(
            note = note,
            movedEntry = redoEntry,
            mode = EditorMode.Edit,
            cursor = entry.cursorBefore,
            selection = entry.selectionBefore,
        )
    }

    // Redo the top entry because redo must reapply exactly the operation that undo removed.
    fun redo(noteId: Long, currentMode: EditorMode): UndoRedoResult {
        assert(noteId > 0L)
        check(currentMode == EditorMode.Edit) { "Redo is not available in mode $currentMode" }
        val entry = undoRedo.pop(noteId, UndoDirection.Redo) ?: error("Redo requires an available redo entry")
        val note = notes.updateContent(noteId, entry.textAfter)
        val undoEntry = copyToDirection(entry, UndoDirection.Undo)
        val mode = if (entry.operationKind == UndoOperationKind.CancelEdit) EditorMode.View else EditorMode.Edit
        return UndoRedoResult(
            note = note,
            movedEntry = undoEntry,
            mode = mode,
            cursor = entry.cursorAfter,
            selection = entry.selectionAfter,
        )
    }

    // Report undo availability from mode because view mode permits only undoing a cancel edit.
    fun canUndo(noteId: Long, mode: EditorMode): Boolean {
        assert(noteId > 0L)
        val entry = undoRedo.peek(noteId, UndoDirection.Undo) ?: return false
        return canReplayUndoInMode(mode, entry.operationKind)
    }

    // Report redo availability from mode because normal redo belongs to the editable surface.
    fun canRedo(noteId: Long, mode: EditorMode): Boolean {
        assert(noteId > 0L)
        return mode == EditorMode.Edit && undoRedo.peek(noteId, UndoDirection.Redo) != null
    }

    // Clear all stacks on save because saved notes intentionally lose undo and redo history.
    fun clearForSave(noteId: Long) {
        assert(noteId > 0L)
        undoRedo.deleteForNote(noteId)
    }

    // Allow only draft edit/view modes because snapshot view is not the draft editor undo surface.
    private fun canReplayUndoInMode(mode: EditorMode, operationKind: UndoOperationKind): Boolean {
        return mode == EditorMode.Edit || (mode == EditorMode.View && operationKind == UndoOperationKind.CancelEdit)
    }

    // Reinsert an operation on the opposite stack because stack movement must keep operation details intact.
    private fun copyToDirection(entry: UndoEntry, direction: UndoDirection): UndoEntry = undoRedo.create(
        noteId = entry.noteId,
        direction = direction,
        operationKind = entry.operationKind,
        position = entry.position,
        textBefore = entry.textBefore,
        textAfter = entry.textAfter,
        cursorBefore = entry.cursorBefore,
        cursorAfter = entry.cursorAfter,
        selectionBefore = entry.selectionBefore,
        selectionAfter = entry.selectionAfter,
    )
}
