package com.nofuzznotes.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index(value = ["edited"]), Index(value = ["deleted"])],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val content: String,
    val created: String,
    val edited: String,
    val deleted: String?,
)
