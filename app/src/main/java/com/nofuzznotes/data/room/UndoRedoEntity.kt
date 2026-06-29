package com.nofuzznotes.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "undo_redo",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["noteId", "direction", "id"])],
)
data class UndoRedoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val noteId: Long,
    val direction: String,
    val operationKind: String,
    val position: Int,
    val textBefore: String,
    val textAfter: String,
    val cursorBefore: Int,
    val cursorAfter: Int,
    val selectionBeforeStart: Int,
    val selectionBeforeEnd: Int,
    val selectionAfterStart: Int,
    val selectionAfterEnd: Int,
    val created: String,
)
