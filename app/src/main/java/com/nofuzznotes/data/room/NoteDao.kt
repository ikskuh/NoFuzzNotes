package com.nofuzznotes.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NoteDao {
    // Insert a row through Room because generated ids must match SQLite row identity.
    @Insert
    fun insert(entity: NoteEntity): Long

    // Read one note because repositories expose id-based aggregate loading.
    @Query("SELECT * FROM notes WHERE id = :id")
    fun read(id: Long): NoteEntity?

    // List active notes by edited time because the notebook default is newest edited first.
    @Query("SELECT * FROM notes WHERE deleted IS NULL ORDER BY edited DESC, id DESC")
    fun listNormal(): List<NoteEntity>

    // List trashed notes separately because deleted rows remain recoverable.
    @Query("SELECT * FROM notes WHERE deleted IS NOT NULL ORDER BY edited DESC, id DESC")
    fun listTrash(): List<NoteEntity>

    // Write content and edited together because every draft write is durable user work.
    @Query("UPDATE notes SET content = :content, edited = :edited WHERE id = :id")
    fun updateContent(id: Long, content: String, edited: String): Int

    // Write edited alone because explicit save is user-visible even when content is unchanged.
    @Query("UPDATE notes SET edited = :edited WHERE id = :id")
    fun touchEdited(id: Long, edited: String): Int

    // Write deleted alone because trashing must not reorder notes by edited time.
    @Query("UPDATE notes SET deleted = :deleted WHERE id = :id")
    fun markDeleted(id: Long, deleted: String): Int

    // Clear deleted alone because untrash must preserve draft and edited timestamps.
    @Query("UPDATE notes SET deleted = NULL WHERE id = :id")
    fun clearDeleted(id: Long): Int

    // Search draft content because full-text search must include current note rows.
    @Query("SELECT * FROM notes WHERE content LIKE '%' || :query || '%' ORDER BY edited DESC, id DESC")
    fun searchContent(query: String): List<NoteEntity>

    // Delete one note because foreign keys cascade dependent history and undo rows.
    @Query("DELETE FROM notes WHERE id = :id")
    fun delete(id: Long): Int
}
