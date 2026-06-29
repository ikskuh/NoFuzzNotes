package com.nofuzznotes.data.room

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.Snapshot
import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.IsoUtcTimestampFormatter
import java.time.Instant

// Parse persisted UTC strings at the Room boundary because domain models use typed instants.
fun NoteEntity.toModel(): Note = Note(id, content, Instant.parse(created), Instant.parse(edited), deleted?.let(Instant::parse))

// Parse persisted UTC strings at the Room boundary because snapshots keep immutable save time.
fun SnapshotEntity.toModel(): Snapshot = Snapshot(id, noteId, content, Instant.parse(created))

// Parse persisted stack strings at the Room boundary because services should use enums and selections.
fun UndoRedoEntity.toModel(): UndoEntry = UndoEntry(
    id = id,
    noteId = noteId,
    direction = UndoDirection.valueOf(direction),
    operationKind = UndoOperationKind.valueOf(operationKind),
    position = position,
    textBefore = textBefore,
    textAfter = textAfter,
    cursorBefore = cursorBefore,
    cursorAfter = cursorAfter,
    selectionBefore = TextSelection(selectionBeforeStart, selectionBeforeEnd),
    selectionAfter = TextSelection(selectionAfterStart, selectionAfterEnd),
    created = Instant.parse(created),
)

// Format timestamps centrally because persisted values must use UTC seconds everywhere.
fun Instant.toStoredTimestamp(): String = IsoUtcTimestampFormatter.format(this)
