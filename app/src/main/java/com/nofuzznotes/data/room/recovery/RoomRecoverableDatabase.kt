package com.nofuzznotes.data.room.recovery

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.nofuzznotes.data.room.NoFuzzNotesDatabase
import com.nofuzznotes.data.room.RoomDatabaseExporter
import com.nofuzznotes.domain.recovery.RecoverableDatabase
import java.io.File

class RoomRecoverableDatabase(
    private val context: Context,
    private val databaseName: String,
) : RecoverableDatabase {
    // Open and probe Room because building the database object alone does not prove SQLite usability.
    override fun openFresh(): Boolean {
        val database = openDatabase()
        try {
            database.query("SELECT 1", emptyArray()).close()
            return true
        } catch (exception: SQLiteException) {
            return false
        } finally {
            database.close()
        }
    }

    // Copy the database file because recovery export must work before a destructive reset.
    override fun exportTo(destination: File): File {
        val databaseFile = context.getDatabasePath(databaseName)
        assert(databaseFile.exists())
        return RoomDatabaseExporter(databaseFile).exportTo(destination)
    }

    // Delete every SQLite sidecar before reopening because WAL files can otherwise resurrect old state.
    override fun resetToFresh() {
        context.deleteDatabase(databaseName)
        deleteSidecar("-wal")
        deleteSidecar("-shm")
        val opened = openFresh()
        assert(opened)
    }

    // Build the Room database because recovery checks need the same storage stack as normal app usage.
    private fun openDatabase(): NoFuzzNotesDatabase {
        return Room.databaseBuilder(context, NoFuzzNotesDatabase::class.java, databaseName).build()
    }

    // Remove sidecar files because database reset must leave no stale SQLite state behind.
    private fun deleteSidecar(suffix: String) {
        val sidecar = File(context.getDatabasePath(databaseName).path + suffix)
        if (sidecar.exists()) {
            sidecar.delete()
        }
    }
}
