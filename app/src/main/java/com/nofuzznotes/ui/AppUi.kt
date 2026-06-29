package com.nofuzznotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofuzznotes.domain.service.FullTextSearchDepth
import com.nofuzznotes.domain.service.FullTextSearchResult
import com.nofuzznotes.domain.service.FullTextSearchScope
import com.nofuzznotes.domain.service.FullTextSearchTarget
import com.nofuzznotes.presentation.common.NoteSortMode
import com.nofuzznotes.presentation.common.PromptState
import com.nofuzznotes.presentation.history.HistoryState
import com.nofuzznotes.presentation.list.NoteListState
import com.nofuzznotes.presentation.recovery.RecoveryState
import com.nofuzznotes.presentation.search.SearchState
import com.nofuzznotes.presentation.trash.TrashListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    state: NoteListState,
    onNew: () -> Unit,
    onTrash: () -> Unit,
    onSearch: () -> Unit,
    onBackup: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSort: (NoteSortMode) -> Unit,
    onOpen: (Long) -> Unit,
    onSelect: (Long?) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onConfirmPrompt: () -> Unit,
    onDismissPrompt: () -> Unit,
) {
    // Keep the list as title-only rows because previews would violate the note-list contract.
    Scaffold(topBar = {
        TopAppBar(title = { Text("Notes") }, actions = {
            if (state.selectedNoteId == null) {
                TextButton(onClick = { onSort(NoteSortMode.EditedDescending) }) { Text("Sort") }
                TextButton(onClick = onNew) { Text("New") }
                TextButton(onClick = onTrash) { Text("Trash") }
                TextButton(onClick = onSearch) { Text("Full Search") }
                TextButton(onClick = onBackup) { Text("Backup") }
            } else {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onExport) { Text("Export") }
                TextButton(onClick = onShare) { Text("Share") }
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(value = state.query, onValueChange = onQueryChanged, label = { Text("List Search") })
            LazyColumn(Modifier.testTag("note-list")) {
                items(state.notes, key = { it.id }) { note ->
                    Row(Modifier.fillMaxWidth().padding(16.dp).testTag("note-row-${note.id}")) {
                        Text(note.title, Modifier.weight(1f).clickable { onOpen(note.id) }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                        TextButton(onClick = { onSelect(note.id) }) { Text("Select") }
                    }
                }
            }
        }
    }
    PromptDialog(state.prompt, onConfirmPrompt, onDismissPrompt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashListScreen(
    state: TrashListState,
    onSelect: (Long?) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSort: (NoteSortMode) -> Unit,
    onOpen: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
    onDestroy: (Long) -> Unit,
    onUntrash: (Long) -> Unit,
    onConfirmPrompt: () -> Unit,
    onDismissPrompt: () -> Unit,
) {
    // Render only trash actions because normal-note actions must not be available for deleted rows.
    Scaffold(topBar = {
        TopAppBar(title = { Text("Trash") }, actions = {
            if (state.selectedNoteId == null) {
                TextButton(onClick = { onSort(NoteSortMode.EditedDescending) }) { Text("Sort") }
                TextButton(onClick = onEmptyTrash) { Text("Empty Trash") }
            } else {
                TextButton(onClick = { onDestroy(state.selectedNoteId ?: error("Destroy requires selection")) }) { Text("Destroy") }
                TextButton(onClick = { onUntrash(state.selectedNoteId ?: error("Untrash requires selection")) }) { Text("Restore") }
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(value = state.query, onValueChange = onQueryChanged, label = { Text("List Search") })
            LazyColumn(Modifier.testTag("trash-list")) {
                items(state.notes, key = { it.id }) { note ->
                    Row(Modifier.fillMaxWidth().padding(16.dp).testTag("trash-row-${note.id}")) {
                        Text(note.title, Modifier.weight(1f).clickable { onOpen(note.id) }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                        TextButton(onClick = { onSelect(note.id) }) { Text("Select") }
                    }
                }
            }
        }
    }
    PromptDialog(state.prompt, onConfirmPrompt, onDismissPrompt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(state: SearchState, onQueryChanged: (String) -> Unit, onScopeChanged: (FullTextSearchScope) -> Unit, onDepthChanged: (FullTextSearchDepth) -> Unit, onOpenResult: (Int) -> Unit) {
    // Delegate result meaning to ViewModel state because UI should only label old snapshot matches.
    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(value = state.query, onValueChange = onQueryChanged, label = { Text("Search") })
            Row {
                AssistChip(onClick = { onScopeChanged(FullTextSearchScope.Notes) }, label = { Text("Notes") })
                AssistChip(onClick = { onScopeChanged(FullTextSearchScope.Trash) }, label = { Text("Trash") })
                AssistChip(onClick = { onScopeChanged(FullTextSearchScope.Everything) }, label = { Text("Everything") })
            }
            Row {
                AssistChip(onClick = { onDepthChanged(FullTextSearchDepth.Latest) }, label = { Text("Latest") })
                AssistChip(onClick = { onDepthChanged(FullTextSearchDepth.FullHistory) }, label = { Text("Full History") })
            }
            LazyColumn(Modifier.testTag("search-results")) {
                items(state.results.indices.toList(), key = { it }) { index ->
                    SearchResultRow(state.results[index], index, onOpenResult)
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: FullTextSearchResult, index: Int, onOpenResult: (Int) -> Unit) {
    // Mark old rows explicitly because snapshot matches open a different surface than drafts.
    Row(Modifier.fillMaxWidth().clickable { onOpenResult(index) }.padding(16.dp).testTag("search-row-$index")) {
        Text(result.note.title, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (result.target == FullTextSearchTarget.Snapshot || result.isOld) {
            Spacer(Modifier.width(8.dp))
            Text("Old", Modifier.testTag("search-old-$index"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: HistoryState, onViewSnapshot: (Long) -> Unit, onRestore: (Long) -> Unit, onConfirmPrompt: () -> Unit, onDismissPrompt: () -> Unit) {
    // Show timestamps beside titles because history rows are saved versions rather than live drafts.
    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).testTag("history-list")) {
            items(state.snapshots, key = { it.id }) { snapshot ->
                Row(Modifier.fillMaxWidth().clickable { onViewSnapshot(snapshot.id) }.padding(16.dp).testTag("history-row-${snapshot.id}")) {
                    Text(com.nofuzznotes.core.text.CoreTextRules.displayTimestamp(snapshot.created, java.time.LocalDate.now(), java.time.ZoneId.systemDefault(), java.util.Locale.getDefault()))
                    Spacer(Modifier.width(8.dp))
                    Text(snapshot.title, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onRestore(snapshot.id) }) { Text("Restore") }
                }
            }
        }
    }
    PromptDialog(state.prompt, onConfirmPrompt, onDismissPrompt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(state: RecoveryState, onExport: () -> Unit, onReset: () -> Unit, onClose: () -> Unit, onConfirmPrompt: () -> Unit, onDismissPrompt: () -> Unit) {
    // Expose only recovery exits because normal notebook navigation depends on fixing storage first.
    Scaffold(topBar = { TopAppBar(title = { Text("Recovery") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).testTag("recovery-screen")) {
            Text("Database recovery")
            Button(onClick = onExport) { Text("Export Database") }
            Button(onClick = onReset) { Text("Reset Database") }
            Button(onClick = onClose) { Text("Close") }
        }
    }
    PromptDialog(state.prompt, onConfirmPrompt, onDismissPrompt)
}

@Composable
fun PromptDialog(prompt: PromptState?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    // Centralize confirmations because safe prompts must have one consistent irreversible-action gate.
    if (prompt == null) return
    var checked by remember(prompt) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm") },
        text = {
            Column {
                Text(prompt.message)
                if (prompt.isSafe) {
                    Row(Modifier.testTag("safe-check-row")) {
                        Checkbox(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.testTag("safe-check"))
                        Text("I'm sure")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !prompt.isSafe || checked, modifier = Modifier.testTag("prompt-ok")) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NoFuzzNotesTheme(content: @Composable () -> Unit) {
    // Keep theming thin because increment 14 is about shell wiring, not visual design.
    MaterialTheme(typography = Typography().run { copy(bodyLarge = bodyLarge.copy(fontFamily = FontFamily.Monospace), bodyMedium = bodyMedium.copy(fontFamily = FontFamily.Monospace), bodySmall = bodySmall.copy(fontFamily = FontFamily.Monospace)) }, content = content)
}
