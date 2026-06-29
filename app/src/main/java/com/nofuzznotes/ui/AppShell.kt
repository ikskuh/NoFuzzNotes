package com.nofuzznotes.ui

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import com.nofuzznotes.data.room.RoomNoteRepository
import com.nofuzznotes.data.room.RoomSnapshotRepository
import com.nofuzznotes.data.room.RoomUndoRedoRepository
import com.nofuzznotes.domain.recovery.RecoveryService
import com.nofuzznotes.domain.service.ExportService
import com.nofuzznotes.domain.service.FullTextSearchService
import com.nofuzznotes.domain.service.HistoryService
import com.nofuzznotes.domain.service.LocalDateProvider
import com.nofuzznotes.domain.service.NoteLifecycleService
import com.nofuzznotes.domain.service.TitleSearchService
import com.nofuzznotes.domain.service.TrashService
import com.nofuzznotes.domain.service.UndoRedoService
import com.nofuzznotes.presentation.common.AppRoute
import com.nofuzznotes.presentation.common.PresentationEffect
import com.nofuzznotes.presentation.editor.EditorViewModel
import com.nofuzznotes.presentation.history.HistoryViewModel
import com.nofuzznotes.presentation.list.NoteListViewModel
import com.nofuzznotes.presentation.recovery.RecoveryViewModel
import com.nofuzznotes.presentation.search.SearchViewModel
import com.nofuzznotes.presentation.trash.TrashListViewModel
import java.time.LocalDate

class AppDependencies(
    val notes: RoomNoteRepository,
    val snapshots: RoomSnapshotRepository,
    val undoRedoRepository: RoomUndoRedoRepository,
    val lifecycle: NoteLifecycleService,
    val trash: TrashService,
    val titleSearch: TitleSearchService,
    val fullTextSearch: FullTextSearchService,
    val history: HistoryService,
    val undoRedo: UndoRedoService,
    val export: ExportService,
    val recovery: RecoveryService? = null,
)

@Composable
fun NoFuzzNotesApp(dependencies: AppDependencies, startInRecovery: Boolean = false, onClose: () -> Unit = {}) {
    // Hold route locally because increment 14 navigation effects are single-screen shell transitions.
    var route: AppRoute by remember { mutableStateOf(if (startInRecovery) AppRoute.Recovery else AppRoute.NoteList) }
    NoFuzzNotesTheme {
        when (val current = route) {
            AppRoute.NoteList -> NoteListRoute(dependencies, { route = it }, { route = AppRoute.TrashList })
            AppRoute.TrashList -> TrashListRoute(dependencies, { route = it })
            AppRoute.Search -> SearchRoute(dependencies, { route = it })
            is AppRoute.Editor -> EditorRoute(dependencies, current.noteId, null, { route = it })
            is AppRoute.History -> HistoryRoute(dependencies, current.noteId, { route = it })
            is AppRoute.SnapshotViewer -> EditorRoute(dependencies, current.noteId, current.snapshotId, { route = it })
            AppRoute.Recovery -> RecoveryRoute(dependencies, { route = it }, onClose)
        }
    }
}

@Composable
private fun NoteListRoute(dependencies: AppDependencies, navigate: (AppRoute) -> Unit, openTrash: () -> Unit) {
    // Keep ViewModel creation at route boundaries because screens consume only state and callbacks.
    val viewModel = remember { NoteListViewModel(dependencies.notes, dependencies.lifecycle, dependencies.titleSearch, dependencies.trash, dependencies.export) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { collectNavigation(viewModel.effects, navigate) }
    NoteListScreen(
        state = state,
        onNew = viewModel::newNote,
        onTrash = openTrash,
        onSearch = viewModel::openFullTextSearch,
        onBackup = viewModel::backupDatabase,
        onQueryChanged = viewModel::search,
        onSort = viewModel::sort,
        onOpen = viewModel::openNote,
        onSelect = viewModel::selectNote,
        onDelete = viewModel::deleteSelected,
        onExport = viewModel::exportSelected,
        onShare = viewModel::shareSelected,
        onConfirmPrompt = viewModel::confirmDelete,
        onDismissPrompt = viewModel::dismissPrompt,
    )
}

@Composable
private fun TrashListRoute(dependencies: AppDependencies, navigate: (AppRoute) -> Unit) {
    // Keep trash shell state-driven because trash service owns permanent mutations.
    val viewModel = remember { TrashListViewModel(dependencies.notes, dependencies.titleSearch, dependencies.trash) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { collectNavigation(viewModel.effects, navigate) }
    TrashListScreen(state, viewModel::selectNote, viewModel::search, viewModel::sort, viewModel::openTrashedNote, viewModel::emptyTrash, viewModel::destroy, viewModel::untrash, viewModel::confirmPrompt, viewModel::dismissPrompt)
}

@Composable
private fun SearchRoute(dependencies: AppDependencies, navigate: (AppRoute) -> Unit) {
    // Collect search effects because result rows decide route through ViewModel targets.
    val viewModel = remember { SearchViewModel(dependencies.fullTextSearch) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { collectNavigation(viewModel.effects, navigate) }
    SearchScreen(state, viewModel::queryChanged, viewModel::scopeChanged, viewModel::depthChanged, viewModel::openResult)
}

@Composable
private fun HistoryRoute(dependencies: AppDependencies, noteId: Long, navigate: (AppRoute) -> Unit) {
    // Bind history to one note because snapshots are scoped immutable rows.
    val viewModel = remember(noteId) { HistoryViewModel(noteId, dependencies.history) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { collectNavigation(viewModel.effects, navigate) }
    HistoryScreen(state, viewModel::viewSnapshot, viewModel::restore, viewModel::confirmRestore, viewModel::dismissPrompt)
}

@Composable
private fun RecoveryRoute(dependencies: AppDependencies, navigate: (AppRoute) -> Unit, onClose: () -> Unit) {
    // Recovery is optional because normal startup skips it unless database opening fails.
    val recovery = dependencies.recovery ?: return
    val viewModel = remember { RecoveryViewModel(recovery, dependencies.export) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PresentationEffect.Navigate -> navigate(effect.route)
                PresentationEffect.CloseApp -> onClose()
                else -> Unit
            }
        }
    }
    RecoveryScreen(state, viewModel::exportDatabase, viewModel::reset, viewModel::close, viewModel::confirmReset, viewModel::dismissPrompt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorRoute(dependencies: AppDependencies, noteId: Long, snapshotId: Long?, navigate: (AppRoute) -> Unit) {
    // Provide a simple editor surface because final text editing ergonomics belong to the next increment.
    val viewModel = remember(noteId, snapshotId) { EditorViewModel(noteId, snapshotId, dependencies.lifecycle, dependencies.undoRedo, dependencies.trash, dependencies.history, dependencies.export) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { collectNavigation(viewModel.effects, navigate) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("") }, actions = {
            if (state.canEnterEdit) TextButton(onClick = viewModel::enterEditMode) { Text("Edit") }
            if (state.canDelete) TextButton(onClick = viewModel::delete) { Text("Delete") }
            if (state.canSave) TextButton(onClick = viewModel::save) { Text("Save") }
            if (state.canCancel) TextButton(onClick = viewModel::cancel) { Text("Cancel") }
            if (state.canUndo) TextButton(onClick = viewModel::undo) { Text("Undo") }
            if (state.canRedo) TextButton(onClick = viewModel::redo) { Text("Redo") }
            if (state.canRestore) TextButton(onClick = viewModel::restoreSnapshot) { Text("Restore") }
            if (state.canExport) TextButton(onClick = viewModel::exportDisplayed) { Text("Export") }
            if (state.canShare) TextButton(onClick = viewModel::shareDisplayed) { Text("Share") }
            if (state.canOpenHistory) TextButton(onClick = viewModel::openHistory) { Text("History") }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(value = state.content, onValueChange = viewModel::textChanged, enabled = state.canSave, modifier = Modifier.testTag("editor-text"))
        }
    }
    PromptDialog(state.prompt, {
        when (state.prompt?.kind) {
            com.nofuzznotes.presentation.common.PromptKind.CancelEdit -> viewModel.confirmCancel()
            com.nofuzznotes.presentation.common.PromptKind.DeleteNote -> viewModel.confirmDelete()
            com.nofuzznotes.presentation.common.PromptKind.RestoreSnapshot -> viewModel.confirmRestoreSnapshot()
            else -> error("Unsupported editor prompt")
        }
    }, viewModel::dismissPrompt)
}

private suspend fun collectNavigation(effects: kotlinx.coroutines.flow.Flow<PresentationEffect>, navigate: (AppRoute) -> Unit) {
    // Ignore platform effects in this increment because shell tests cover routing through presentation effects only.
    effects.collect { effect -> if (effect is PresentationEffect.Navigate) navigate(effect.route) }
}

fun buildExportService(): ExportService {
    // Use a date provider here because external document picking is platform integration for a later increment.
    return ExportService(object : LocalDateProvider {
        // Keep generated filenames deterministic to the current device day because export service requires a date provider.
        override fun today(): LocalDate = LocalDate.now()
    })
}
