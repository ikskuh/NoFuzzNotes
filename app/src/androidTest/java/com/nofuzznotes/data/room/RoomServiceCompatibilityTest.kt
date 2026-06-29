package com.nofuzznotes.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.repository.fake.FakeNoteRepository
import com.nofuzznotes.domain.repository.fake.FakeSnapshotRepository
import com.nofuzznotes.domain.repository.fake.FakeUndoRedoRepository
import com.nofuzznotes.domain.service.NoteLifecycleService
import com.nofuzznotes.domain.service.TrashService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RoomServiceCompatibilityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "room-service-${System.nanoTime()}.db"
    private var database: NoFuzzNotesDatabase? = null
    private val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))

    // Close and delete SQLite state because compatibility checks must not share rows.
    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(dbName)
    }

    // Verify lifecycle services produce equivalent persisted state because fake and Room repositories are interchangeable.
    @Test
    fun noteLifecycleServiceBehavesLikeFakeRepositories() {
        val fakeClock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val fakeNotes = FakeNoteRepository(fakeClock)
        val fakeSnapshots = FakeSnapshotRepository(fakeClock)
        val fakeUndoRedo = FakeUndoRedoRepository(fakeClock)
        val fakeService = NoteLifecycleService(fakeNotes, fakeSnapshots, fakeUndoRedo)
        val roomDb = openDatabase()
        val roomNotes = RoomNoteRepository(roomDb, clock)
        val roomSnapshots = RoomSnapshotRepository(roomDb, clock)
        val roomUndoRedo = RoomUndoRedoRepository(roomDb, clock)
        val roomService = NoteLifecycleService(roomNotes, roomSnapshots, roomUndoRedo)

        val fakeNoteId = fakeService.createNote().note.id
        val roomNoteId = roomService.createNote().note.id
        fakeService.editDraft(fakeNoteId, "Saved\r\nBody")
        roomService.editDraft(roomNoteId, "Saved\r\nBody")
        fakeService.saveNote(fakeNoteId)
        roomService.saveNote(roomNoteId)
        fakeService.editDraft(fakeNoteId, "Unsaved")
        roomService.editDraft(roomNoteId, "Unsaved")
        val fakeCancel = fakeService.cancelEdit(fakeNoteId)
        val roomCancel = roomService.cancelEdit(roomNoteId)

        assertEquals(fakeCancel.note.content, roomCancel.note.content)
        assertEquals(fakeSnapshots.listForNote(fakeNoteId).map { it.content }, roomSnapshots.listForNote(roomNoteId).map { it.content })
        assertEquals(fakeUndoRedo.peek(fakeNoteId, com.nofuzznotes.core.model.UndoDirection.Undo)!!.textBefore, roomUndoRedo.peek(roomNoteId, com.nofuzznotes.core.model.UndoDirection.Undo)!!.textBefore)
    }

    // Verify trash services produce equivalent state because deletion rules must not depend on storage backend.
    @Test
    fun trashServiceBehavesLikeFakeRepositories() {
        val fakeClock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val fakeNotes = FakeNoteRepository(fakeClock)
        val fakeTrash = TrashService(fakeNotes, FakeSnapshotRepository(fakeClock), FakeUndoRedoRepository(fakeClock))
        val roomDb = openDatabase()
        val roomNotes = RoomNoteRepository(roomDb, clock)
        val roomTrash = TrashService(roomNotes, RoomSnapshotRepository(roomDb, clock), RoomUndoRedoRepository(roomDb, clock))
        val fake = fakeNotes.createEmpty()
        val room = roomNotes.createEmpty()

        fakeTrash.trashNote(fake.id)
        roomTrash.trashNote(room.id)
        fakeTrash.untrashNote(fake.id)
        roomTrash.untrashNote(room.id)

        assertNull(roomNotes.read(room.id)!!.deleted)
        assertEquals(fakeNotes.listNormal().size, roomNotes.listNormal().size)
    }

    // Open a file-backed Room database because compatibility must exercise SQLite persistence.
    private fun openDatabase(): NoFuzzNotesDatabase {
        database = Room.databaseBuilder(context, NoFuzzNotesDatabase::class.java, dbName).allowMainThreadQueries().build()
        return database!!
    }
}
