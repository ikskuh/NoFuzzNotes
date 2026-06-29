package com.nofuzznotes.domain.service

import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.repository.fake.FakeNoteRepository
import com.nofuzznotes.domain.repository.fake.FakeSnapshotRepository
import com.nofuzznotes.domain.repository.fake.FakeUndoRedoRepository
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportServiceTest {
    private val service = ExportService(LocalDateProvider { LocalDate.of(2026, 6, 29) })

    // Verify draft view uses the visible draft because export must not reload alternate content.
    @Test
    fun draftViewExportsDraftContent() {
        val displayed = DisplayedContent.draft("Draft\nbody")

        val request = service.exportDisplayedContent(displayed)

        assertEquals("Draft\nbody", request.content)
        assertEquals("Draft.txt", request.suggestedFileName)
    }

    // Verify edit mode uses the current draft because unsaved edits are still displayed content.
    @Test
    fun editModeExportsCurrentDraftContent() {
        val displayed = DisplayedContent.draft("Current draft\nunsaved")

        val request = service.exportDisplayedContent(displayed)

        assertEquals("Current draft\nunsaved", request.content)
        assertEquals("Current draft.txt", request.suggestedFileName)
    }

    // Verify snapshot view uses snapshot text because saved versions share the same export path.
    @Test
    fun snapshotViewExportsSnapshotContent() {
        val displayed = DisplayedContent.snapshot("Saved\nversion")

        val request = service.exportDisplayedContent(displayed)

        assertEquals("Saved\nversion", request.content)
        assertEquals("Saved.txt", request.suggestedFileName)
    }

    // Verify export is read-only because exporting must not save or alter note state.
    @Test
    fun exportDoesNotCreateSnapshotOrModifyNote() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val snapshots = FakeSnapshotRepository(clock)
        val undoRedo = FakeUndoRedoRepository(clock)
        val lifecycle = NoteLifecycleService(notes, snapshots, undoRedo)
        val opened = lifecycle.createNote()
        lifecycle.editDraft(opened.note.id, "Title\nbody")

        service.exportDisplayedContent(DisplayedContent.draft(notes.read(opened.note.id)!!.content))

        assertEquals("Title\nbody", notes.read(opened.note.id)!!.content)
        assertEquals(emptyList<Any>(), snapshots.listForNote(opened.note.id))
    }

    // Verify filenames preserve the first line because the system picker owns filename correction.
    @Test
    fun exportFilenameUsesFirstLineVerbatim() {
        val request = service.exportDisplayedContent(DisplayedContent.draft("C:\\Windows  \nbody"))

        assertEquals("C:\\Windows  .txt", request.suggestedFileName)
    }

    // Verify empty titles keep only the extension because filenames come directly from content titles.
    @Test
    fun emptyTitleExportsAsExtensionOnlyName() {
        val request = service.exportDisplayedContent(DisplayedContent.draft("\nbody"))

        assertEquals(".txt", request.suggestedFileName)
    }

    // Verify bytes are UTF-8 because exported plaintext must be portable across platforms.
    @Test
    fun outputIsUtf8() {
        val request = service.exportDisplayedContent(DisplayedContent.draft("Emoji 😀\ntext"))

        assertArrayEquals("Emoji 😀\ntext".toByteArray(StandardCharsets.UTF_8), request.bytes)
    }

    // Verify export normalizes newline variants because exported plaintext must use LF.
    @Test
    fun outputUsesLf() {
        val request = service.exportDisplayedContent(DisplayedContent.draft("Title\r\nbody\rend"))

        assertEquals("Title\nbody\nend", request.content)
        assertArrayEquals("Title\nbody\nend".toByteArray(StandardCharsets.UTF_8), request.bytes)
    }

    // Verify share uses the visible text because platform share must not query a different source.
    @Test
    fun shareUsesCurrentlyDisplayedContent() {
        val request = service.shareDisplayedContent(DisplayedContent.snapshot("Visible\r\ntext"))

        assertEquals("Visible\r\ntext", request.content)
    }

    // Verify share is read-only because sharing must not alter notebook data.
    @Test
    fun shareDoesNotModifyNote() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val note = notes.createEmpty()
        notes.updateContent(note.id, "Title\nbody")

        service.shareDisplayedContent(DisplayedContent.draft(notes.read(note.id)!!.content))

        assertEquals("Title\nbody", notes.read(note.id)!!.content)
    }

    // Verify backup names include the current date because database exports need stable default names.
    @Test
    fun databaseExportSuggestedNameUsesCurrentDate() {
        val request = service.exportDatabase()

        assertEquals("notes-2026-06-29.db3", request.suggestedFileName)
    }

    // Verify backup requests represent the whole database because import is out of MVP scope.
    @Test
    fun databaseExportRequestIncludesFullDatabase() {
        val request = service.exportDatabase()

        assertEquals(true, request.includesFullDatabase)
    }

    // Verify backup request creation is read-only because backups must not change the notebook.
    @Test
    fun databaseExportDoesNotModifyDatabaseRepositories() {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val note = notes.createEmpty()
        val before = notes.read(note.id)

        service.exportDatabase()

        assertEquals(before, notes.read(note.id))
        assertNull(notes.read(note.id)!!.deleted)
    }
}
