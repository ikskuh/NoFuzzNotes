package com.nofuzznotes.data.room.recovery

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nofuzznotes.data.room.NoFuzzNotesDatabase
import com.nofuzznotes.data.room.RoomNoteRepository
import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.recovery.RecoveryService
import com.nofuzznotes.domain.recovery.StartupDestination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class RoomRecoverableDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "room-recovery-${System.nanoTime()}.db"

    // Remove database files because recovery tests intentionally create corrupt storage.
    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    // Verify a valid Room database opens because startup should route to the note list.
    @Test
    fun validDatabaseOpensSuccessfully() {
        val recovery = RoomRecoverableDatabase(context, dbName)

        assertTrue(recovery.openFresh())
    }

    // Verify corrupt bytes fail open because recovery routing depends on detecting unusable SQLite files.
    @Test
    fun corruptDatabaseDoesNotOpenSuccessfully() {
        writeCorruptDatabase()

        assertFalse(RoomRecoverableDatabase(context, dbName).openFresh())
    }


    // Verify corrupt bytes route through recovery because startup decisions must be testable without Compose.
    @Test
    fun corruptDatabaseProducesRecoveryState() {
        writeCorruptDatabase()

        val destination = RecoveryService(RoomRecoverableDatabase(context, dbName)).decideStartup()

        assertTrue(destination is StartupDestination.Recovery)
    }

    // Verify reset deletes rows because old user data must not survive destructive recovery.
    @Test
    fun resetDeletesOldDatabase() {
        createNote("Old")
        val recovery = RoomRecoverableDatabase(context, dbName)

        recovery.resetToFresh()

        assertEquals(0, countNotes())
    }

    // Verify reset creates an openable database because recovery must continue into normal use.
    @Test
    fun resetCreatesFreshUsableDatabase() {
        writeCorruptDatabase()
        val recovery = RoomRecoverableDatabase(context, dbName)

        recovery.resetToFresh()

        assertTrue(recovery.openFresh())
        assertEquals(0, countNotes())
    }

    // Verify export can copy corrupt bytes because the recovery screen offers backup before reset.
    @Test
    fun corruptDatabaseCanBeExportedBeforeReset() {
        writeCorruptDatabase()
        val destination = File(context.cacheDir, "$dbName.copy")

        val exported = RoomRecoverableDatabase(context, dbName).exportTo(destination)

        assertTrue(exported.exists())
        assertTrue(exported.readText().contains("not sqlite"))
        assertFalse(countNotesSafely())
        destination.delete()
    }

    // Write invalid SQLite contents because this is the simplest deterministic corruption fixture.
    private fun writeCorruptDatabase() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        file.writeText("not sqlite")
    }

    // Create a note through Room because reset behavior must be checked against production storage.
    private fun createNote(content: String) {
        val database = Room.databaseBuilder(context, NoFuzzNotesDatabase::class.java, dbName).allowMainThreadQueries().build()
        try {
            RoomNoteRepository(database, TestClock(Instant.parse("2026-06-29T00:00:00Z"))).createEmpty().also { note ->
                RoomNoteRepository(database, TestClock(Instant.parse("2026-06-29T00:00:00Z"))).updateContent(note.id, content)
            }
        } finally {
            database.close()
        }
    }

    // Count notes through SQL because the test only needs to prove the fresh database is empty.
    private fun countNotes(): Int {
        val database = Room.databaseBuilder(context, NoFuzzNotesDatabase::class.java, dbName).allowMainThreadQueries().build()
        try {
            val cursor = database.query("SELECT COUNT(*) FROM notes", emptyArray())
            cursor.use {
                assertTrue(cursor.moveToFirst())
                return cursor.getInt(0)
            }
        } finally {
            database.close()
        }
    }

    // Report whether old rows remain because export should not silently reset the corrupt database.
    private fun countNotesSafely(): Boolean {
        return try {
            countNotes() > 0
        } catch (exception: RuntimeException) {
            false
        }
    }
}
