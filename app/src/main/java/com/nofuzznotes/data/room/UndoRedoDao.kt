package com.nofuzznotes.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UndoRedoDao {
    // Insert stack entries because editor history must survive process death.
    @Insert
    fun insert(entity: UndoRedoEntity): Long

    // List oldest-first because the stack top is the newest inserted row.
    @Query("SELECT * FROM undo_redo WHERE noteId = :noteId AND direction = :direction ORDER BY id ASC")
    fun listForNote(noteId: Long, direction: String): List<UndoRedoEntity>

    // Read the stack top without mutating because availability checks must be side-effect free.
    @Query("SELECT * FROM undo_redo WHERE noteId = :noteId AND direction = :direction ORDER BY id DESC LIMIT 1")
    fun peek(noteId: Long, direction: String): UndoRedoEntity?

    // Delete one stack entry because pop moves exactly one operation.
    @Query("DELETE FROM undo_redo WHERE id = :id")
    fun deleteById(id: Long): Int

    // Delete one direction because new edits invalidate redo without touching undo.
    @Query("DELETE FROM undo_redo WHERE noteId = :noteId AND direction = :direction")
    fun deleteForNote(noteId: Long, direction: String): Int

    // Delete all stack entries because saves and destruction clear editor history.
    @Query("DELETE FROM undo_redo WHERE noteId = :noteId")
    fun deleteForNote(noteId: Long): Int
}
