package com.nofuzznotes.presentation

import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.recovery.RecoverableDatabase
import com.nofuzznotes.domain.recovery.RecoveryService
import com.nofuzznotes.domain.repository.fake.FakeNoteRepository
import com.nofuzznotes.domain.repository.fake.FakeSnapshotRepository
import com.nofuzznotes.domain.repository.fake.FakeUndoRedoRepository
import com.nofuzznotes.domain.service.ExportService
import com.nofuzznotes.domain.service.FullTextSearchDepth
import com.nofuzznotes.domain.service.FullTextSearchScope
import com.nofuzznotes.domain.service.FullTextSearchService
import com.nofuzznotes.domain.service.HistoryService
import com.nofuzznotes.domain.service.LocalDateProvider
import com.nofuzznotes.domain.service.NoteLifecycleService
import com.nofuzznotes.domain.service.TitleSearchService
import com.nofuzznotes.domain.service.TrashService
import com.nofuzznotes.domain.service.UndoRedoService
import com.nofuzznotes.presentation.common.AppRoute
import com.nofuzznotes.presentation.common.PresentationEffect
import com.nofuzznotes.presentation.common.NoteSortMode
import com.nofuzznotes.presentation.common.PromptKind
import com.nofuzznotes.presentation.editor.EditorViewModel
import com.nofuzznotes.presentation.history.HistoryViewModel
import com.nofuzznotes.presentation.list.NoteListViewModel
import com.nofuzznotes.presentation.recovery.RecoveryViewModel
import com.nofuzznotes.presentation.search.SearchViewModel
import com.nofuzznotes.presentation.trash.TrashListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PresentationViewModelTest {
    @Test
    fun noteListSupportsSortingFilteringCreationSelectionAndDeletePrompt() = runTest {
        val fixture = Fixture()
        val older = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(older, "Alpha\nbody")
        fixture.clock.set(java.time.Instant.parse("2026-06-29T00:00:01Z"))
        val newer = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(newer, "Beta\nbody")
        val viewModel = fixture.noteListViewModel()

        assertEquals(listOf("Beta", "Alpha"), viewModel.state.value.notes.map { it.title })
        viewModel.sort(NoteSortMode.TitleAscending)
        assertEquals(listOf("Alpha", "Beta"), viewModel.state.value.notes.map { it.title })
        assertTrue(viewModel.state.value.notes.all { it.javaClass.declaredFields.none { field -> field.name == "edited" } })
        viewModel.search("alp")
        assertEquals(listOf("Alpha"), viewModel.state.value.notes.map { it.title })
        viewModel.selectNote(older)
        assertTrue(viewModel.state.value.canDelete)
        assertTrue(viewModel.state.value.canExport)
        assertTrue(viewModel.state.value.canShare)
        viewModel.deleteSelected()
        assertEquals(PromptKind.DeleteNote, viewModel.state.value.prompt?.kind)
        viewModel.confirmDelete()
        assertTrue(fixture.notes.read(older)?.isTrashed() == true)

        viewModel.newNote()
        val effect = viewModel.effects.first { it is PresentationEffect.Navigate } as PresentationEffect.Navigate
        assertTrue(effect.route is AppRoute.Editor)
    }

    @Test
    fun trashPromptsAndConfirmedActionsCallService() {
        val fixture = Fixture()
        val first = fixture.lifecycle.createNote().note.id
        val second = fixture.lifecycle.createNote().note.id
        fixture.trash.trashNote(first)
        fixture.trash.trashNote(second)
        val viewModel = fixture.trashListViewModel()

        assertEquals(2, viewModel.state.value.notes.size)
        viewModel.search("Alpha")
        assertTrue(viewModel.state.value.notes.isEmpty())
        viewModel.search("")
        viewModel.selectNote(first)
        assertTrue(viewModel.state.value.canDestroy)
        assertTrue(viewModel.state.value.canUntrash)
        viewModel.untrash(first)
        assertEquals(PromptKind.UntrashNote, viewModel.state.value.prompt?.kind)
        viewModel.confirmPrompt()
        assertFalse(fixture.notes.read(first)?.isTrashed() == true)
        viewModel.destroy(second)
        assertTrue(viewModel.state.value.prompt?.isSafe == true)
        viewModel.confirmPrompt()
        assertNull(fixture.notes.read(second))
        fixture.trash.trashNote(first)
        viewModel.refresh()
        viewModel.emptyTrash()
        assertEquals(PromptKind.EmptyTrash, viewModel.state.value.prompt?.kind)
        assertTrue(viewModel.state.value.prompt?.isSafe == true)
        viewModel.confirmPrompt()
        assertTrue(viewModel.state.value.notes.isEmpty())
    }

    @Test
    fun editorDrivesModeTextSaveCancelUndoRedoDeleteExportShareAndHistory() = runTest {
        val fixture = Fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        val viewModel = fixture.editorViewModel(noteId)

        assertEquals("", viewModel.state.value.content)
        viewModel.textChanged("Draft")
        assertEquals("Draft", fixture.notes.read(noteId)?.content)
        assertTrue(viewModel.state.value.canUndo)
        viewModel.save()
        assertNull(viewModel.state.value.prompt)
        assertTrue(viewModel.state.value.canOpenHistory)
        assertTrue(viewModel.state.value.canEnterEdit)
        viewModel.enterEditMode()
        assertTrue(viewModel.state.value.canSave)
        assertTrue(viewModel.state.value.canCancel)
        viewModel.textChanged("Draft changed")
        viewModel.cancel()
        assertEquals(PromptKind.CancelEdit, viewModel.state.value.prompt?.kind)
        viewModel.confirmCancel()
        assertEquals("Draft", viewModel.state.value.content)
        viewModel.undo()
        assertEquals("Draft changed", viewModel.state.value.content)
        viewModel.redo()
        assertEquals("Draft", viewModel.state.value.content)
        viewModel.exportDisplayed()
        assertTrue(viewModel.effects.first { it is PresentationEffect.ExportText } is PresentationEffect.ExportText)
        viewModel.shareDisplayed()
        assertTrue(viewModel.effects.first { it is PresentationEffect.ShareText } is PresentationEffect.ShareText)
        viewModel.delete()
        assertEquals(PromptKind.DeleteNote, viewModel.state.value.prompt?.kind)
        viewModel.confirmDelete()
        assertEquals(AppRoute.NoteList, (viewModel.effects.first { it is PresentationEffect.Navigate } as PresentationEffect.Navigate).route)
    }

    @Test
    fun historyListsViewsAndRestoresSnapshots() = runTest {
        val fixture = Fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "One")
        fixture.lifecycle.saveNote(noteId)
        fixture.lifecycle.editDraft(noteId, "Two")
        fixture.lifecycle.saveNote(noteId)
        val viewModel = fixture.historyViewModel(noteId)

        assertEquals(listOf("Two", "One"), viewModel.state.value.snapshots.map { it.title })
        val newest = viewModel.state.value.snapshots.first().id
        viewModel.viewSnapshot(newest)
        assertEquals(AppRoute.SnapshotViewer(noteId, newest), (viewModel.effects.first { it is PresentationEffect.Navigate && it.route == AppRoute.SnapshotViewer(noteId, newest) } as PresentationEffect.Navigate).route)
        val oldest = viewModel.state.value.snapshots.last().id
        viewModel.restore(oldest)
        assertEquals(PromptKind.RestoreSnapshot, viewModel.state.value.prompt?.kind)
        viewModel.confirmRestore()
        assertEquals("One", fixture.notes.read(noteId)?.content)
        assertEquals(AppRoute.Editor(noteId), (viewModel.effects.first { it is PresentationEffect.Navigate && it.route == AppRoute.Editor(noteId) } as PresentationEffect.Navigate).route)
    }

    @Test
    fun editorSnapshotModeCanExportShareAndRestore() = runTest {
        val fixture = Fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "Saved")
        val snapshotId = fixture.lifecycle.saveNote(noteId).createdSnapshot?.id ?: error("Snapshot expected")
        val viewModel = fixture.snapshotEditorViewModel(noteId, snapshotId)

        assertEquals(com.nofuzznotes.domain.service.EditorMode.ViewSnapshot, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.canRestore)
        assertFalse(viewModel.state.value.canDelete)
        viewModel.exportDisplayed()
        assertTrue(viewModel.effects.first { it is PresentationEffect.ExportText } is PresentationEffect.ExportText)
        viewModel.shareDisplayed()
        assertTrue(viewModel.effects.first { it is PresentationEffect.ShareText } is PresentationEffect.ShareText)
        viewModel.restoreSnapshot()
        assertEquals(PromptKind.RestoreSnapshot, viewModel.state.value.prompt?.kind)
        viewModel.confirmRestoreSnapshot()
        assertEquals(AppRoute.Editor(noteId), (viewModel.effects.first { it is PresentationEffect.Navigate } as PresentationEffect.Navigate).route)
    }

    @Test
    fun searchSupportsOptionsAndNavigatesToDraftOrSnapshot() = runTest {
        val fixture = Fixture()
        val noteId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(noteId, "Old needle")
        fixture.lifecycle.saveNote(noteId)
        fixture.lifecycle.editDraft(noteId, "Current hay")
        val draftId = fixture.lifecycle.createNote().note.id
        fixture.lifecycle.editDraft(draftId, "needle now")
        val viewModel = fixture.searchViewModel()

        viewModel.queryChanged("needle")
        viewModel.openResult(0)
        assertEquals(AppRoute.Editor(draftId), (viewModel.effects.first { it is PresentationEffect.Navigate } as PresentationEffect.Navigate).route)
        viewModel.depthChanged(FullTextSearchDepth.FullHistory)
        viewModel.scopeChanged(FullTextSearchScope.Everything)
        val oldIndex = viewModel.state.value.results.indexOfFirst { it.isOld }
        viewModel.openResult(oldIndex)
        val route = (viewModel.effects.first { it is PresentationEffect.Navigate && it.route is AppRoute.SnapshotViewer } as PresentationEffect.Navigate).route
        assertTrue(route is AppRoute.SnapshotViewer)
    }

    @Test
    fun recoveryExposesExportResetAndCloseActions() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.recoveryViewModel()

        viewModel.exportDatabase()
        assertTrue(viewModel.effects.first { it is PresentationEffect.ExportDatabase } is PresentationEffect.ExportDatabase)
        viewModel.reset()
        assertEquals(PromptKind.ResetDatabase, viewModel.state.value.prompt?.kind)
        assertTrue(viewModel.state.value.prompt?.isSafe == true)
        viewModel.confirmReset()
        assertTrue(fixture.recoverable.resetCalled)
        assertEquals(AppRoute.NoteList, (viewModel.effects.first { it is PresentationEffect.Navigate } as PresentationEffect.Navigate).route)
        viewModel.close()
        assertEquals(PresentationEffect.CloseApp, viewModel.effects.first { it == PresentationEffect.CloseApp })
    }

    private class Fixture {
        val clock = TestClock(java.time.Instant.parse("2026-06-29T00:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val snapshots = FakeSnapshotRepository(clock)
        val undoRedo = FakeUndoRedoRepository(clock)
        val lifecycle = NoteLifecycleService(notes, snapshots, undoRedo)
        val trash = TrashService(notes, snapshots, undoRedo)
        private val titleSearch = TitleSearchService(notes)
        private val fullTextSearch = FullTextSearchService(notes, snapshots)
        private val undoRedoService = UndoRedoService(notes, undoRedo)
        private val history = HistoryService(notes, snapshots, undoRedo)
        private val export = ExportService(LocalDateProvider { LocalDate.of(2026, 6, 29) })
        val recoverable = FakeRecoverableDatabase()
        private val recovery = RecoveryService(recoverable)

        // Create a note-list ViewModel with shared fake services because tests inspect service effects.
        fun noteListViewModel() = NoteListViewModel(notes, lifecycle, titleSearch, trash, export)

        // Create a trash ViewModel with shared fake services because tests inspect repository mutations.
        fun trashListViewModel() = TrashListViewModel(notes, titleSearch, trash)

        // Create an editor ViewModel with shared fake services because tests inspect editor behavior end-to-end.
        fun editorViewModel(noteId: Long) = EditorViewModel(noteId = noteId, lifecycle = lifecycle, undoRedo = undoRedoService, trash = trash, history = history, export = export)

        // Create a snapshot editor ViewModel because snapshot viewing shares the editor presentation surface.
        fun snapshotEditorViewModel(noteId: Long, snapshotId: Long) = EditorViewModel(noteId = noteId, snapshotId = snapshotId, lifecycle = lifecycle, undoRedo = undoRedoService, trash = trash, history = history, export = export)

        // Create a history ViewModel with shared fake services because tests inspect snapshot mutations.
        fun historyViewModel(noteId: Long) = HistoryViewModel(noteId, history)

        // Create a search ViewModel with shared fake services because tests inspect navigation targets.
        fun searchViewModel() = SearchViewModel(fullTextSearch)

        // Create a recovery ViewModel with a fake database because tests must not touch platform storage.
        fun recoveryViewModel() = RecoveryViewModel(recovery, export)
    }

    private class FakeRecoverableDatabase : RecoverableDatabase {
        var resetCalled = false

        // Report usable storage because reset should navigate back to the note list.
        override fun openFresh(): Boolean = true

        // Return the requested destination because export copying is covered below the presentation layer.
        override fun exportTo(destination: File): File = destination

        // Track reset calls because the ViewModel test verifies confirmation gates the destructive action.
        override fun resetToFresh() { resetCalled = true }
    }
}
