package com.nofuzznotes.domain.service

import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.repository.fake.FakeNoteRepository
import com.nofuzznotes.domain.repository.fake.FakeSnapshotRepository
import com.nofuzznotes.domain.repository.fake.FakeUndoRedoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FullTextSearchServiceTest {
    // Verify notes scope ignores trash because full-text scope must mirror the selected source.
    @Test
    fun notesScopeSearchesNonTrashedNotes() {
        val fixture = fixture()
        val active = fixture.note("Active\nneedle")
        val trashed = fixture.note("Trash\nneedle")
        fixture.notes.markDeleted(trashed)

        val results = fixture.search.search("needle", FullTextSearchScope.Notes).map { it.note.id }

        assertEquals(listOf(active), results)
    }

    // Verify trash scope ignores active notes because trashed text is only included on request.
    @Test
    fun trashScopeSearchesTrashedNotes() {
        val fixture = fixture()
        fixture.note("Active\nneedle")
        val trashed = fixture.note("Trash\nneedle")
        fixture.notes.markDeleted(trashed)

        val results = fixture.search.search("needle", FullTextSearchScope.Trash).map { it.note.id }

        assertEquals(listOf(trashed), results)
    }

    // Verify everything combines active and trashed notes because global full-text search must cover both states.
    @Test
    fun everythingScopeSearchesBothNormalAndTrash() {
        val fixture = fixture()
        val active = fixture.note("Active\nneedle")
        val trashed = fixture.note("Trash\nneedle")
        fixture.notes.markDeleted(trashed)

        val results = fixture.search.search("needle", FullTextSearchScope.Everything).map { it.note.id }.toSet()

        assertEquals(setOf(active, trashed), results)
    }

    // Verify latest depth ignores saved history because drafts are the default search depth.
    @Test
    fun latestDepthSearchesDraftsOnly() {
        val fixture = fixture()
        val note = fixture.note("old needle")
        fixture.save(note)
        fixture.edit(note, "current text")

        val results = fixture.search.search("needle", FullTextSearchScope.Notes, FullTextSearchDepth.Latest)

        assertEquals(emptyList<FullTextSearchResult>(), results)
    }

    // Verify full-history depth includes snapshots because the user explicitly opted into old content.
    @Test
    fun fullHistoryDepthSearchesDraftsAndSnapshots() {
        val fixture = fixture()
        val note = fixture.note("old needle")
        val snapshot = fixture.save(note)
        fixture.edit(note, "current text")

        val result = fixture.search.search("needle", FullTextSearchScope.Notes, FullTextSearchDepth.FullHistory).single()

        assertEquals(note, result.note.id)
        assertEquals(snapshot.id, result.snapshot?.id)
    }

    // Verify draft matches open drafts because current content is always preferred over history.
    @Test
    fun draftMatchProducesDraftResult() {
        val fixture = fixture()
        val note = fixture.note("current needle")

        val result = fixture.search.search("needle", FullTextSearchScope.Notes).single()

        assertEquals(FullTextSearchTarget.Draft, result.target)
        assertNull(result.snapshot)
        assertFalse(result.isOld)
        assertEquals(note, result.note.id)
    }

    // Verify matching snapshots are hidden when draft matches because result lists show only one row per note.
    @Test
    fun draftMatchSuppressesMatchingSnapshots() {
        val fixture = fixture()
        val note = fixture.note("old needle")
        fixture.save(note)
        fixture.edit(note, "current needle")

        val result = fixture.search.search("needle", FullTextSearchScope.Notes, FullTextSearchDepth.FullHistory).single()

        assertEquals(FullTextSearchTarget.Draft, result.target)
        assertNull(result.snapshot)
    }

    // Verify snapshot-only matches open snapshots and carry the old marker for UI presentation.
    @Test
    fun snapshotOnlyMatchProducesOldSnapshotResult() {
        val fixture = fixture()
        val note = fixture.note("old needle")
        val snapshot = fixture.save(note)
        fixture.edit(note, "current text")

        val result = fixture.search.search("needle", FullTextSearchScope.Notes, FullTextSearchDepth.FullHistory).single()

        assertEquals(FullTextSearchTarget.Snapshot, result.target)
        assertEquals(snapshot.id, result.snapshot?.id)
        assertTrue(result.isOld)
    }

    // Verify multiple matching snapshots collapse to the latest match because old-only results open one saved version.
    @Test
    fun multipleMatchingSnapshotsProduceLatestMatchingSnapshotResult() {
        val fixture = fixture()
        val note = fixture.note("first needle")
        val first = fixture.save(note)
        fixture.clock.set(Instant.parse("2026-06-28T10:01:00Z"))
        fixture.edit(note, "second needle")
        val second = fixture.save(note)
        fixture.edit(note, "current text")

        val result = fixture.search.search("needle", FullTextSearchScope.Notes, FullTextSearchDepth.FullHistory).single()

        assertEquals(second.id, result.snapshot?.id)
        assertFalse(first.id == result.snapshot?.id)
    }

    // Verify full-text search ignores case because the spec requires case-insensitive matching beyond title search.
    @Test
    fun fullTextSearchIsCaseInsensitive() {
        val fixture = fixture()
        val note = fixture.note("Mixed Case Body")

        val results = fixture.search.search("case", FullTextSearchScope.Notes).map { it.note.id }

        assertEquals(listOf(note), results)
    }

    // Verify fuzzy full-text recovery because Increment 9 requires typo-tolerant body search, not only title search.
    @Test
    fun fullTextSearchSupportsFuzzyTypoSkippedDuplicateAndFlipMatches() {
        val fixture = fixture()
        val typo = fixture.note("Grocery")
        val skipped = fixture.note("Notebook")
        val duplicated = fixture.note("Plaintext")
        val flipped = fixture.note("Archive")

        val typoResults = fixture.search.search("Grocert", FullTextSearchScope.Notes).map { it.note.id }
        val skippedResults = fixture.search.search("Notebok", FullTextSearchScope.Notes).map { it.note.id }
        val duplicatedResults = fixture.search.search("Plainntext", FullTextSearchScope.Notes).map { it.note.id }
        val flippedResults = fixture.search.search("Arhcive", FullTextSearchScope.Notes).map { it.note.id }

        assertEquals(listOf(typo), typoResults)
        assertEquals(listOf(skipped), skippedResults)
        assertEquals(listOf(duplicated), duplicatedResults)
        assertEquals(listOf(flipped), flippedResults)
    }

    // Verify exact text matches rank above fuzzy recovery because literal matches better represent user intent.
    @Test
    fun exactMatchesRankAboveFuzzyMatches() {
        val fixture = fixture()
        val fuzzy = fixture.note("Title")
        val exact = fixture.note("Ttle")

        val results = fixture.search.search("Ttle", FullTextSearchScope.Notes).map { it.note.id }

        assertEquals(listOf(exact, fuzzy), results)
    }

    private data class Fixture(
        val clock: TestClock,
        val notes: FakeNoteRepository,
        val lifecycle: NoteLifecycleService,
        val search: FullTextSearchService,
    ) {
        // Create persisted draft text because full-text search reads current repository content.
        fun note(content: String): Long {
            val note = lifecycle.createNote().note
            lifecycle.editDraft(note.id, content)
            return note.id
        }

        // Save through lifecycle because snapshots must follow the same rules as app saves.
        fun save(noteId: Long) = lifecycle.saveNote(noteId).createdSnapshot!!

        // Edit through lifecycle because draft updates must normalize and update repository state.
        fun edit(noteId: Long, content: String) {
            lifecycle.editDraft(noteId, content)
        }
    }

    // Build isolated dependencies because search ranking and scope tests must be deterministic.
    private fun fixture(): Fixture {
        val clock = TestClock(Instant.parse("2026-06-28T10:00:00Z"))
        val notes = FakeNoteRepository(clock)
        val snapshots = FakeSnapshotRepository(clock)
        val undoRedo = FakeUndoRedoRepository(clock)
        val lifecycle = NoteLifecycleService(notes, snapshots, undoRedo)
        return Fixture(
            clock = clock,
            notes = notes,
            lifecycle = lifecycle,
            search = FullTextSearchService(notes, snapshots),
        )
    }
}
