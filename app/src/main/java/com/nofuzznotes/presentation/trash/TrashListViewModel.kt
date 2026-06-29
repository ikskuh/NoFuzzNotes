package com.nofuzznotes.presentation.trash

import androidx.lifecycle.ViewModel
import com.nofuzznotes.core.model.Note
import com.nofuzznotes.domain.repository.NoteRepository
import com.nofuzznotes.domain.service.TitleSearchService
import com.nofuzznotes.domain.service.TrashService
import com.nofuzznotes.presentation.common.NoteSortMode
import com.nofuzznotes.presentation.common.PromptKind
import com.nofuzznotes.presentation.common.PromptMessages
import com.nofuzznotes.presentation.common.PromptState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class TrashListItem(val id: Long, val title: String)

data class TrashListState(
    val query: String = "",
    val sortMode: NoteSortMode = NoteSortMode.EditedDescending,
    val notes: List<TrashListItem> = emptyList(),
    val selectedNoteId: Long? = null,
    val canDestroy: Boolean = false,
    val canUntrash: Boolean = false,
    val prompt: PromptState? = null,
)

class TrashListViewModel(
    private val notes: NoteRepository,
    private val titleSearch: TitleSearchService,
    private val trash: TrashService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TrashListState())
    val state: StateFlow<TrashListState> = mutableState

    init { refresh() }

    // Reload trash rows because permanent actions can remove multiple rows at once.
    fun refresh() {
        val current = mutableState.value
        val source = if (current.query.isBlank()) notes.listTrash() else titleSearch.searchTrash(current.query).map { it.note }
        val sorted = if (current.query.isBlank()) source.sortedByMode(current.sortMode) else source
        mutableState.value = current.copy(notes = sorted.mapTrashItems())
    }

    // Filter trashed titles only because list search must stay scoped to visible trash rows.
    fun search(query: String) {
        mutableState.value = mutableState.value.copy(query = query, selectedNoteId = null).withSelection(null)
        refresh()
    }

    // Apply supported trash-list sort modes because trash browsing mirrors normal list behavior.
    fun sort(mode: NoteSortMode) {
        mutableState.value = mutableState.value.copy(sortMode = mode)
        refresh()
    }

    // Track the selected row because trash toolbar actions require an explicit note target.
    fun selectNote(noteId: Long?) {
        assert(noteId == null || noteId > 0L)
        mutableState.value = mutableState.value.withSelection(noteId)
    }

    // Ask for a safe confirmation because empty trash is irreversible for every trashed note.
    fun emptyTrash() {
        val count = notes.listTrash().size
        mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.EmptyTrash, PromptMessages.emptyTrash(count), isSafe = true))
    }

    // Ask before permanent destruction because a single note loses all recovery data.
    fun destroy(noteId: Long) {
        assert(noteId > 0L)
        mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.DestroyNote, PromptMessages.PERMANENT_DELETE_NOTE, noteId, isSafe = true))
    }

    // Ask before untrash because it moves data back into the normal notebook scope.
    fun untrash(noteId: Long) {
        assert(noteId > 0L)
        mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.UntrashNote, PromptMessages.basic("Do you want to restore this note?"), noteId))
    }

    // Execute the prompted trash action because confirmation belongs to presentation state.
    fun confirmPrompt() {
        val prompt = mutableState.value.prompt ?: error("Prompt is required")
        when (prompt.kind) {
            PromptKind.EmptyTrash -> trash.emptyTrash()
            PromptKind.DestroyNote -> trash.destroyNote(prompt.targetId ?: error("Destroy target is required"))
            PromptKind.UntrashNote -> trash.untrashNote(prompt.targetId ?: error("Untrash target is required"))
            else -> error("Unsupported trash prompt: ${prompt.kind}")
        }
        mutableState.value = mutableState.value.copy(prompt = null, selectedNoteId = null).withSelection(null)
        refresh()
    }

    // Clear prompts because cancellation must not mutate trashed notes.
    fun dismissPrompt() { mutableState.value = mutableState.value.copy(prompt = null) }
}

// Convert domain notes into immutable title-only rows because trash lists must not expose timestamps or previews.
private fun List<Note>.mapTrashItems(): List<TrashListItem> = map { TrashListItem(it.id, it.title) }

// Apply SPEC sort modes in presentation because repositories expose default order only.
private fun List<Note>.sortedByMode(mode: NoteSortMode): List<Note> = when (mode) {
    NoteSortMode.CreatedAscending -> sortedBy { it.created }
    NoteSortMode.CreatedDescending -> sortedByDescending { it.created }
    NoteSortMode.EditedAscending -> sortedBy { it.edited }
    NoteSortMode.EditedDescending -> sortedByDescending { it.edited }
    NoteSortMode.TitleAscending -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    NoteSortMode.TitleDescending -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
}

// Derive trash action flags together because selected trashed rows expose destroy and untrash only.
private fun TrashListState.withSelection(noteId: Long?): TrashListState = copy(
    selectedNoteId = noteId,
    canDestroy = noteId != null,
    canUntrash = noteId != null,
)
