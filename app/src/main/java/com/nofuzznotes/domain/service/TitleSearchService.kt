package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.search.TitleSearchRanker
import com.nofuzznotes.domain.repository.NoteRepository

data class TitleSearchResult(
    val note: Note,
    val rank: Int,
) {
    init {
        assert(rank >= 0)
    }
}

class TitleSearchService(private val notes: NoteRepository) {
    // Search active titles only because normal list search must not leak trashed notes.
    fun searchNormal(query: String): List<TitleSearchResult> = search(notes.listNormal(), query)

    // Search trashed titles only because trash list search must mirror the current list scope.
    fun searchTrash(query: String): List<TitleSearchResult> = search(notes.listTrash(), query)

    // Rank matching titles deterministically because stable best-to-worst results are required for tests and UI.
    private fun search(candidates: List<Note>, query: String): List<TitleSearchResult> {
        return candidates.mapNotNull { note ->
            TitleSearchRanker.rank(query, note.title)?.let { rank -> TitleSearchResult(note, rank) }
        }.sortedWith(compareBy<TitleSearchResult> { it.rank }.thenBy { it.note.id })
    }
}
