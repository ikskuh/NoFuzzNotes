package com.nofuzznotes.core.model

import java.time.Instant

enum class UndoDirection {
    Undo,
    Redo,
}

enum class UndoOperationKind {
    Replace,
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
    val created: Instant,
) {
    init {
        assert(id > 0L)
        assert(noteId > 0L)
        assert(position >= 0)
        assert(cursorBefore >= 0)
        assert(cursorAfter >= 0)
    }
}
