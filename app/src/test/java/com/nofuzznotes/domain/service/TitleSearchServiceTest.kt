package com.nofuzznotes.domain.service

import com.nofuzznotes.core.time.TestClock
import com.nofuzznotes.domain.repository.fake.FakeNoteRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TitleSearchServiceTest {
    // Verify normal search is title-only and active-only because list search is scoped to the displayed note list.
    @Test
    fun noteListSearchOnlySearchesNonTrashedNoteTitles() {
        val fixture = fixture()
        val activeTitle = fixture.note("Alpha\nbody contains hidden")
        fixture.note("Other\nAlpha body only")
        val trashed = fixture.note("Alpha trash")
        fixture.notes.markDeleted(trashed)

        val results = fixture.search.searchNormal("Alpha").map { it.note.id }

        assertEquals(listOf(activeTitle), results)
    }

    // Verify trash search is title-only and trashed-only because trash has its own displayed list scope.
    @Test
    fun trashListSearchOnlySearchesTrashedNoteTitles() {
        val fixture = fixture()
        fixture.note("Alpha active")
        val trashedTitle = fixture.note("Alpha trash")
        fixture.notes.markDeleted(trashedTitle)
        val trashedBody = fixture.note("Other\nAlpha body only")
        fixture.notes.markDeleted(trashedBody)

        val results = fixture.search.searchTrash("Alpha").map { it.note.id }

        assertEquals(listOf(trashedTitle), results)
    }

    // Verify literal matching ignores case because the spec requires case-insensitive search.
    @Test
    fun searchIsCaseInsensitiveAndExactSubstringMatchesWork() {
        val fixture = fixture()
        val noteId = fixture.note("Mixed Case Title")

        val results = fixture.search.searchNormal("case").map { it.note.id }

        assertEquals(listOf(noteId), results)
    }

    // Verify fuzzy typo recovery because title search must tolerate user mistakes.
    @Test
    fun fuzzyTypoMatchesWork() {
        val fixture = fixture()
        val noteId = fixture.note("Grocery")

        val results = fixture.search.searchNormal("Grocert").map { it.note.id }

        assertEquals(listOf(noteId), results)
    }

    // Verify skipped characters match because fuzzy search must tolerate omissions.
    @Test
    fun skippedCharactersMatchFuzzily() {
        val fixture = fixture()
        val noteId = fixture.note("Title")

        val results = fixture.search.searchNormal("Ttle").map { it.note.id }

        assertEquals(listOf(noteId), results)
    }

    // Verify duplicated characters match because fuzzy search must tolerate accidental repeats.
    @Test
    fun duplicateCharactersMatchFuzzily() {
        val fixture = fixture()
        val noteId = fixture.note("Title")

        val results = fixture.search.searchNormal("Tittle").map { it.note.id }

        assertEquals(listOf(noteId), results)
    }

    // Verify adjacent character flips match because transposition is a required fuzzy behavior.
    @Test
    fun characterFlipsMatchFuzzily() {
        val fixture = fixture()
        val noteId = fixture.note("Title")

        val results = fixture.search.searchNormal("Ttile").map { it.note.id }

        assertEquals(listOf(noteId), results)
    }

    // Verify acronym matching is not accidentally introduced because the MVP does not require smart matching.
    @Test
    fun acronymMatchingIsNotRequired() {
        val fixture = fixture()
        fixture.note("No Fuzz Notes")

        val results = fixture.search.searchNormal("nfn")

        assertEquals(emptyList<TitleSearchResult>(), results)
    }

    // Verify exact results sort before fuzzy ones because literal title matches are better than recovered ones.
    @Test
    fun exactMatchesRankAboveFuzzyMatches() {
        val fixture = fixture()
        val fuzzy = fixture.note("Title")
        val exact = fixture.note("Ttle")

        val results = fixture.search.searchNormal("Ttle").map { it.note.id }

        assertEquals(listOf(exact, fuzzy), results)
    }

    // Verify whitespace is searchable because whitespace-only titles are displayed as real titles.
    @Test
    fun whitespaceTitlesAreSearchableByWhitespace() {
        val fixture = fixture()
        val noteId = fixture.note("   \t  \nbody")

        val results = fixture.search.searchNormal("\t").map { it.note.id }

        assertEquals(listOf(noteId), results)
    }

    private data class Fixture(val notes: FakeNoteRepository, val search: TitleSearchService) {
        // Create a persisted note with content because search must reflect repository draft state.
        fun note(content: String): Long {
            val note = notes.createEmpty()
            notes.updateContent(note.id, content)
            return note.id
        }
    }

    // Build isolated search dependencies because result ordering must not leak across tests.
    private fun fixture(): Fixture {
        val notes = FakeNoteRepository(TestClock(Instant.parse("2026-06-28T10:00:00Z")))
        return Fixture(notes = notes, search = TitleSearchService(notes))
    }
}
