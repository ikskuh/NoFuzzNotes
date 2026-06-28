package com.nofuzznotes.core.model

import java.time.Instant

enum class UndoDirection {
    Undo,
    Redo,
}

enum class UndoOperationKind {
    Typing,
    Deletion,
    Paste,
    Cut,
    Replacement,
    Clear,
    Autocorrect,
    CancelEdit,
    SnapshotRestore,
}

data class TextSelection(
    val start: Int,
    val end: Int,
) {
    init {
        assert(start >= 0)
        assert(end >= 0)
        assert(start <= end)
    }
}

data class UndoEntry(
    val id: Long,
    val noteId: Long,
    val direction: UndoDirection,
    val operationKind: UndoOperationKind,
    val position: Int,
    val textBefore: String,
    val textAfter: String,
    val cursorBefore: Int,
    val cursorAfter: Int,
    val selectionBefore: TextSelection = TextSelection(cursorBefore, cursorBefore),
    val selectionAfter: TextSelection = TextSelection(cursorAfter, cursorAfter),
    val created: Instant,
) {
    init {
        assert(id > 0L)
        assert(noteId > 0L)
        assert(position >= 0)
        assert(cursorBefore >= 0)
        assert(cursorAfter >= 0)
        assert(cursorBefore <= textBefore.length)
        assert(cursorAfter <= textAfter.length)
        assert(selectionBefore.start <= textBefore.length)
        assert(selectionBefore.end <= textBefore.length)
        assert(selectionAfter.start <= textAfter.length)
        assert(selectionAfter.end <= textAfter.length)
    }
}
