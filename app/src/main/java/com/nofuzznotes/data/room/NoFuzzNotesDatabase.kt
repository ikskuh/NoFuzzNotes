package com.nofuzznotes.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, SnapshotEntity::class, UndoRedoEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NoFuzzNotesDatabase : RoomDatabase() {
    // Expose note SQL because repositories keep persistence details outside services.
    abstract fun noteDao(): NoteDao

    // Expose snapshot SQL because save history is stored separately from drafts.
    abstract fun snapshotDao(): SnapshotDao

    // Expose undo SQL because edit stacks have independent persistence rules.
    abstract fun undoRedoDao(): UndoRedoDao
}
