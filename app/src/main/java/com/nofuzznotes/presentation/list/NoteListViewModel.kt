package com.nofuzznotes.presentation.list

import androidx.lifecycle.ViewModel
import com.nofuzznotes.core.model.Note
import com.nofuzznotes.domain.repository.NoteRepository
import com.nofuzznotes.domain.service.DisplayedContent
import com.nofuzznotes.domain.service.ExportService
import com.nofuzznotes.domain.service.NoteLifecycleService
import com.nofuzznotes.domain.service.TitleSearchService
import com.nofuzznotes.domain.service.TrashService
import com.nofuzznotes.presentation.common.AppRoute
import com.nofuzznotes.presentation.common.EffectBuffer
import com.nofuzznotes.presentation.common.NoteSortMode
import com.nofuzznotes.presentation.common.PresentationEffect
import com.nofuzznotes.presentation.common.PromptKind
import com.nofuzznotes.presentation.common.PromptMessages
import com.nofuzznotes.presentation.common.PromptState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class NoteListItem(val id: Long, val title: String)

data class NoteListState(
    val query: String = "",
    val sortMode: NoteSortMode = NoteSortMode.EditedDescending,
    val notes: List<NoteListItem> = emptyList(),
    val selectedNoteId: Long? = null,
    val canDelete: Boolean = false,
    val canExport: Boolean = false,
    val canShare: Boolean = false,
    val prompt: PromptState? = null,
)

class NoteListViewModel(
    private val notes: NoteRepository,
    private val lifecycle: NoteLifecycleService,
    private val titleSearch: TitleSearchService,
    private val trash: TrashService,
    private val export: ExportService,
) : ViewModel() {
    private val effectBuffer = EffectBuffer()
    private val mutableState = MutableStateFlow(NoteListState())

    val state: StateFlow<NoteListState> = mutableState
    val effects = effectBuffer.effects

    init { refresh() }

    // Reload list state because repository rows are the presentation source of truth for this increment.
    fun refresh() {
        val current = mutableState.value
        val source = if (current.query.isBlank()) notes.listNormal() else titleSearch.searchNormal(current.query).map { it.note }
        val sorted = if (current.query.isBlank()) source.sortedByMode(current.sortMode) else source
        mutableState.value = current.copy(notes = sorted.mapNoteItems())
    }

    // Filter by note title because list search is intentionally narrower than full-text search.
    fun search(query: String) {
        mutableState.value = mutableState.value.copy(query = query, selectedNoteId = null).withSelection(null)
        refresh()
    }

    // Apply supported note-list sort modes because the list toolbar must not render hidden timestamps to sort.
    fun sort(mode: NoteSortMode) {
        mutableState.value = mutableState.value.copy(sortMode = mode)
        refresh()
    }

    // Create and navigate immediately because new empty notes are durable drafts.
    fun newNote() {
        val result = lifecycle.createNote()
        refresh()
        effectBuffer.emit(PresentationEffect.Navigate(AppRoute.Editor(result.note.id)))
    }

    // Navigate to full-text search because list menu actions are presentation effects.
    fun openFullTextSearch() { effectBuffer.emit(PresentationEffect.Navigate(AppRoute.Search)) }

    // Emit database backup because platform file handling is outside the ViewModel.
    fun backupDatabase() { effectBuffer.emit(PresentationEffect.ExportDatabase(export.exportDatabase())) }

    // Track the selected row because toolbar actions require an explicit note target.
    fun selectNote(noteId: Long?) {
        assert(noteId == null || noteId > 0L)
        mutableState.value = mutableState.value.withSelection(noteId)
    }

    // Ask before trashing because delete is destructive from the normal list.
    fun deleteSelected() {
        val id = requireSelected()
        mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.DeleteNote, PromptMessages.DELETE_NOTE, id))
    }

    // Trash the selected note only after confirmation because presentation owns prompts.
    fun confirmDelete() {
        val id = requirePrompt(PromptKind.DeleteNote)
        trash.trashNote(id)
        mutableState.value = mutableState.value.copy(prompt = null, selectedNoteId = null).withSelection(null)
        refresh()
    }

    // Emit visible draft export because platform file handling is outside the ViewModel.
    fun exportSelected() {
        val note = requireNote(requireSelected())
        effectBuffer.emit(PresentationEffect.ExportText(export.exportDisplayedContent(DisplayedContent.draft(note.content))))
    }

    // Emit visible draft share because platform share handling is outside the ViewModel.
    fun shareSelected() {
        val note = requireNote(requireSelected())
        effectBuffer.emit(PresentationEffect.ShareText(export.shareDisplayedContent(DisplayedContent.draft(note.content))))
    }

    // Clear prompts because cancellation must not mutate notebook state.
    fun dismissPrompt() { mutableState.value = mutableState.value.copy(prompt = null) }

    // Fail fast for missing selection because action enablement should prevent this path.
    private fun requireSelected(): Long = mutableState.value.selectedNoteId ?: error("A selected note is required")

    // Fail fast for stale rows because presentation should only address repository notes.
    private fun requireNote(noteId: Long): Note = notes.read(noteId) ?: error("Missing note: $noteId")

    // Fail fast for wrong confirmation because prompt/action mismatches are implementation bugs.
    private fun requirePrompt(kind: PromptKind): Long = mutableState.value.prompt?.takeIf { it.kind == kind }?.targetId ?: error("Prompt $kind is required")
}

// Convert domain notes into immutable title-only rows because note lists must not expose timestamps or previews.
private fun List<Note>.mapNoteItems(): List<NoteListItem> = map { NoteListItem(it.id, it.title) }

// Apply SPEC sort modes in presentation because repositories expose default order only.
private fun List<Note>.sortedByMode(mode: NoteSortMode): List<Note> = when (mode) {
    NoteSortMode.CreatedAscending -> sortedBy { it.created }
    NoteSortMode.CreatedDescending -> sortedByDescending { it.created }
    NoteSortMode.EditedAscending -> sortedBy { it.edited }
    NoteSortMode.EditedDescending -> sortedByDescending { it.edited }
    NoteSortMode.TitleAscending -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    NoteSortMode.TitleDescending -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
}

// Derive action flags together because selection controls all row actions consistently.
private fun NoteListState.withSelection(noteId: Long?): NoteListState = copy(
    selectedNoteId = noteId,
    canDelete = noteId != null,
    canExport = noteId != null,
    canShare = noteId != null,
)
