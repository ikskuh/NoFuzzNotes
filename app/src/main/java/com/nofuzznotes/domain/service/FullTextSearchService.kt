package com.nofuzznotes.domain.service

import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.Snapshot
import com.nofuzznotes.core.search.TitleSearchRanker
import com.nofuzznotes.domain.repository.NoteRepository
import com.nofuzznotes.domain.repository.SnapshotRepository

enum class FullTextSearchScope {
    Notes,
    Trash,
    Everything,
}

enum class FullTextSearchDepth {
    Latest,
    FullHistory,
}

enum class FullTextSearchTarget {
    Draft,
    Snapshot,
}

data class FullTextSearchResult(
    val note: Note,
    val target: FullTextSearchTarget,
    val snapshot: Snapshot?,
    val rank: Int,
    val isOld: Boolean,
) {
    init {
        assert(rank >= 0)
        assert((target == FullTextSearchTarget.Draft) == (snapshot == null))
        assert(isOld == (target == FullTextSearchTarget.Snapshot))
        snapshot?.let { assert(it.noteId == note.id) }
    }
}

class FullTextSearchService(
    private val notes: NoteRepository,
    private val snapshots: SnapshotRepository,
) {
    // Search text across the requested notebook scope because full-text search is separate from title-only list filtering.
    fun search(
        query: String,
        scope: FullTextSearchScope,
        depth: FullTextSearchDepth = FullTextSearchDepth.Latest,
    ): List<FullTextSearchResult> {
        return scopedNotes(scope).mapNotNull { note -> resultForNote(note, query, depth) }
            .sortedWith(compareBy<FullTextSearchResult> { it.rank }.thenBy { it.note.id }.thenBy { it.snapshot?.id ?: 0L })
    }

    // Select candidate notes explicitly because normal notes and trash must never leak into each other's scoped searches.
    private fun scopedNotes(scope: FullTextSearchScope): List<Note> = when (scope) {
        FullTextSearchScope.Notes -> notes.listNormal()
        FullTextSearchScope.Trash -> notes.listTrash()
        FullTextSearchScope.Everything -> notes.listNormal() + notes.listTrash()
    }

    // Collapse matches to one row per note because draft matches hide older matching snapshots in the MVP result list.
    private fun resultForNote(note: Note, query: String, depth: FullTextSearchDepth): FullTextSearchResult? {
        val draftRank = TitleSearchRanker.rank(query, note.content)
        if (draftRank != null) {
            return FullTextSearchResult(
                note = note,
                target = FullTextSearchTarget.Draft,
                snapshot = null,
                rank = draftRank,
                isOld = false,
            )
        }
        if (depth == FullTextSearchDepth.Latest) return null
        return latestMatchingSnapshot(note, query)?.let { match ->
            FullTextSearchResult(
                note = note,
                target = FullTextSearchTarget.Snapshot,
                snapshot = match.snapshot,
                rank = match.rank,
                isOld = true,
            )
        }
    }

    // Prefer the newest matching snapshot because opening one old-only result must show the latest saved match.
    private fun latestMatchingSnapshot(note: Note, query: String): SnapshotMatch? {
        return snapshots.listForNote(note.id).asReversed().firstNotNullOfOrNull { snapshot ->
            TitleSearchRanker.rank(query, snapshot.content)?.let { rank -> SnapshotMatch(snapshot, rank) }
        }
    }

    private data class SnapshotMatch(val snapshot: Snapshot, val rank: Int) {
        init {
            assert(rank >= 0)
        }
    }
}
