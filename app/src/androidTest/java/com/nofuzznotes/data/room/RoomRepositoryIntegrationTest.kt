package com.nofuzznotes.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.core.time.TestClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class RoomRepositoryIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "room-repository-${System.nanoTime()}.db"
    private var database: NoFuzzNotesDatabase? = null
    private val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))

    // Close and delete the database because each integration test owns its SQLite file.
    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(dbName)
    }

    // Verify notes persist after close because SQLite is the durable notebook source of truth.
    @Test
    fun notesPersistAcrossDatabaseReopen() {
        val notes = notes(openDatabase())
        val note = notes.createEmpty()
        notes.updateContent(note.id, "Title\nBody")

        reopenDatabase()

        assertEquals("Title\nBody", notes(openDatabase()).read(note.id)!!.content)
    }

    // Verify snapshots persist after close because saved history must survive app restarts.
    @Test
    fun snapshotsPersistAcrossDatabaseReopen() {
        val db = openDatabase()
        val note = notes(db).createEmpty()
        val snapshot = snapshots(db).create(note.id, "Saved")

        reopenDatabase()

        assertEquals("Saved", snapshots(openDatabase()).read(snapshot.id)!!.content)
    }

    // Verify undo and redo rows persist because edit history is part of the notebook database.
    @Test
    fun undoRedoPersistsAcrossDatabaseReopen() {
        val db = openDatabase()
        val note = notes(db).createEmpty()
        undoRedo(db).create(note.id, UndoDirection.Undo, UndoOperationKind.Typing, 0, "", "a", 0, 1)

        reopenDatabase()

        assertEquals("a", undoRedo(openDatabase()).peek(note.id, UndoDirection.Undo)!!.textAfter)
    }

    // Verify default note order because notebook lists must use edited descending.
    @Test
    fun defaultSortUsesEditedDescending() {
        val notes = notes(openDatabase())
        val older = notes.createEmpty()
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))
        val newer = notes.createEmpty()

        assertEquals(listOf(newer.id, older.id), notes.listNormal().map { it.id })
    }

    // Verify content writes move edited because drafts are persisted immediately.
    @Test
    fun contentUpdateUpdatesEdited() {
        val notes = notes(openDatabase())
        val note = notes.createEmpty()
        clock.set(Instant.parse("2026-06-28T10:01:00Z"))

        val updated = notes.updateContent(note.id, "changed")

        assertEquals(Instant.parse("2026-06-28T10:01:00Z"), updated.edited)
    }

    // Verify save clicks move edited even without content changes.
    @Test
    fun saveTouchUpdatesEditedEvenWithoutContentChange() {
        val notes = notes(openDatabase())
        val note = notes.createEmpty()
        clock.set(Instant.parse("2026-06-28T10:02:00Z"))

        val updated = notes.touchEdited(note.id)

        assertEquals(Instant.parse("2026-06-28T10:02:00Z"), updated.edited)
    }

    // Verify trash state is isolated because trashing must not rewrite edited content metadata.
    @Test
    fun trashUntrashOnlyChangesDeleted() {
        val notes = notes(openDatabase())
        val note = notes.createEmpty()
        val trashed = notes.markDeleted(note.id)
        val untrashed = notes.clearDeleted(note.id)

        assertEquals(note.edited, trashed.edited)
        assertNotNull(trashed.deleted)
        assertEquals(note.edited, untrashed.edited)
        assertNull(untrashed.deleted)
    }

    // Verify permanent deletion cascades because destroyed notes must leave no database-owned data behind.
    @Test
    fun permanentDeleteCascadesToHistoryAndUndoRedo() {
        val db = openDatabase()
        val note = notes(db).createEmpty()
        snapshots(db).create(note.id, "Saved")
        undoRedo(db).create(note.id, UndoDirection.Undo, UndoOperationKind.Typing, 0, "", "a", 0, 1)

        notes(db).destroy(note.id)

        assertTrue(snapshots(db).listForNote(note.id).isEmpty())
        assertTrue(undoRedo(db).listForNote(note.id, UndoDirection.Undo).isEmpty())
    }

    // Verify timestamp strings because persisted timestamps must be ISO UTC with second precision.
    @Test
    fun timestampFormatIsIsoUtcSecondPrecision() {
        clock.set(Instant.parse("2026-06-28T10:00:00.999Z"))
        val db = openDatabase()
        val note = notes(db).createEmpty()

        val raw = db.noteDao().read(note.id)!!

        assertEquals("2026-06-28T10:00:00Z", raw.created)
        assertFalse(raw.created.contains(".999"))
    }

    // Verify SQLite content queries because full-text search can be layered over Room rows.
    @Test
    fun fullTextQueriesReturnExpectedRows() {
        val db = openDatabase()
        val notes = notes(db)
        val first = notes.createEmpty()
        val second = notes.createEmpty()
        snapshots(db).create(first.id, "alpha match")
        snapshots(db).create(second.id, "beta")

        val rows = db.snapshotDao().searchContent("alpha")

        assertEquals(listOf(first.id), rows.map { it.noteId })
    }

    // Verify backup copying because database export must include the actual SQLite file.
    @Test
    fun exportCanCopyDatabaseFile() {
        val db = openDatabase()
        notes(db).createEmpty()
        db.query("PRAGMA wal_checkpoint(FULL)", emptyArray()).close()
        val source = context.getDatabasePath(dbName)
        val destination = File(context.cacheDir, "$dbName.db3")

        val exported = RoomDatabaseExporter(source).exportTo(destination)

        assertTrue(exported.exists())
        assertTrue(exported.length() > 0L)
        destination.delete()
    }

    // Open or reuse the database because repositories share one Room instance per test step.
    private fun openDatabase(): NoFuzzNotesDatabase {
        database?.let { return it }
        database = Room.databaseBuilder(context, NoFuzzNotesDatabase::class.java, dbName).allowMainThreadQueries().build()
        return database!!
    }

    // Reopen the same file because persistence must be checked across Room instances.
    private fun reopenDatabase() {
        database?.close()
        database = null
    }

    // Build the note repository because tests should use production Room behavior.
    private fun notes(database: NoFuzzNotesDatabase) = RoomNoteRepository(database, clock)

    // Build the snapshot repository because tests should use production Room behavior.
    private fun snapshots(database: NoFuzzNotesDatabase) = RoomSnapshotRepository(database, clock)

    // Build the undo repository because tests should use production Room behavior.
    private fun undoRedo(database: NoFuzzNotesDatabase) = RoomUndoRedoRepository(database, clock)
}
