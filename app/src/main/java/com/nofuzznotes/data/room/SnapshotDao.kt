package com.nofuzznotes.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SnapshotDao {
    // Insert immutable history because saves preserve full text versions.
    @Insert
    fun insert(entity: SnapshotEntity): Long

    // Read selected history rows because snapshot viewing opens by id.
    @Query("SELECT * FROM history WHERE id = :id")
    fun read(id: Long): SnapshotEntity?

    // List oldest first because history ordering must stay stable when timestamps tie.
    @Query("SELECT * FROM history WHERE noteId = :noteId ORDER BY created ASC, id ASC")
    fun listForNote(noteId: Long): List<SnapshotEntity>

    // Delete note history explicitly for repository parity and as a cascade fallback.
    @Query("DELETE FROM history WHERE noteId = :noteId")
    fun deleteForNote(noteId: Long): Int

    // Search history content because full-text behavior must work against SQLite storage.
    @Query("SELECT * FROM history WHERE content LIKE '%' || :query || '%' ORDER BY created DESC, id DESC")
    fun searchContent(query: String): List<SnapshotEntity>
}
