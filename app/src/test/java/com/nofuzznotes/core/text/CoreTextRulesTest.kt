package com.nofuzznotes.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class CoreTextRulesTest {
    // Verify empty content stays a real empty title because empty notes are valid notes.
    @Test
    fun emptyContentHasEmptyTitle() {
        assertEquals("", CoreTextRules.extractTitle(""))
    }

    // Verify only the first line is used because note titles are derived from draft text.
    @Test
    fun firstLineIsTitle() {
        assertEquals("Title", CoreTextRules.extractTitle("Title\nBody"))
    }

    // Verify titles are not constrained because uniqueness is not a domain rule.
    @Test
    fun duplicateTitlesAreAllowed() {
        assertEquals(CoreTextRules.extractTitle("Same\nOne"), CoreTextRules.extractTitle("Same\nTwo"))
    }

    // Verify whitespace is preserved because titles are displayed as-authored.
    @Test
    fun whitespaceOnlyTitleIsPreserved() {
        assertEquals("   \t  ", CoreTextRules.extractTitle("   \t  \nbody"))
    }

    // Verify domain logic does not truncate because ellipsizing belongs to presentation.
    @Test
    fun longTitleIsReturnedUnchanged() {
        val longTitle = "a".repeat(300)
        assertEquals(longTitle, CoreTextRules.extractTitle("$longTitle\nbody"))
    }

    // Verify equal draft and latest snapshot disables pending state because no user work differs.
    @Test
    fun draftEqualsLatestSnapshotMeansNoPendingChanges() {
        assertFalse(CoreTextRules.hasPendingChanges("same", "same"))
    }

    // Verify content differences create pending state because snapshots are immutable save points.
    @Test
    fun draftDiffersMeansPendingChanges() {
        assertTrue(CoreTextRules.hasPendingChanges("draft", "snapshot"))
    }

    // Verify null history always creates pending state because no saved version exists.
    @Test
    fun latestSnapshotNullAlwaysMeansPendingChanges() {
        assertTrue(CoreTextRules.hasPendingChanges("draft", null))
    }

    // Verify empty unsaved notes are still pending because the first save must create a snapshot.
    @Test
    fun emptyDraftWithLatestSnapshotNullMeansPendingChanges() {
        assertTrue(CoreTextRules.hasPendingChanges("", null))
    }

    // Verify Windows newlines become app newlines because storage is LF-only.
    @Test
    fun crlfBecomesLf() {
        assertEquals("a\nb", CoreTextRules.sanitizePaste("a\r\nb"))
    }

    // Verify old Mac newlines become app newlines because pasted text can come from many sources.
    @Test
    fun strayCrBecomesLf() {
        assertEquals("a\nb", CoreTextRules.sanitizePaste("a\rb"))
    }

    // Verify LF remains untouched because it is the canonical newline.
    @Test
    fun lfIsPreserved() {
        assertEquals("a\nb", CoreTextRules.sanitizePaste("a\nb"))
    }

    // Verify tabs remain untouched because plaintext notes may intentionally align text.
    @Test
    fun tabIsPreserved() {
        assertEquals("a\tb", CoreTextRules.sanitizePaste("a\tb"))
    }

    // Verify NUL is visible after paste because hidden terminators are unsafe in note content.
    @Test
    fun nulBecomesReplacementCharacter() {
        assertEquals("a\uFFFDb", CoreTextRules.sanitizePaste("a\u0000b"))
    }

    // Verify every invalid control is represented because cleanup must not hide pasted bytes.
    @Test
    fun invalidControlsAreReplacedIndividually() {
        assertEquals("a\uFFFD\uFFFDb", CoreTextRules.sanitizePaste("a\u0001\u0002b"))
    }

    // Verify adjacent invalid controls are not collapsed because user input length should remain inspectable.
    @Test
    fun runsOfInvalidControlsAreNotCollapsed() {
        assertEquals("\uFFFD\uFFFD\uFFFD", CoreTextRules.sanitizePaste("\u0003\u0004\u0005"))
    }

    // Verify empty titles produce the required extension-only export name.
    @Test
    fun emptyFirstLineProducesTxt() {
        assertEquals(".txt", CoreTextRules.exportFilename(""))
    }

    // Verify the full first line is used because filename sanitization is intentionally not a core rule.
    @Test
    fun fullFirstLineIsUsedVerbatim() {
        assertEquals("bad/name?.txt", CoreTextRules.exportFilename("bad/name?\nbody"))
    }

    // Verify the extension is always appended because note exports are plaintext files.
    @Test
    fun txtIsAppended() {
        assertEquals("Title.txt", CoreTextRules.exportFilename("Title"))
    }

    // Verify same-day history labels use clock time because dates are redundant for today's saves.
    @Test
    fun sameDayTimestampRendersHourMinute() {
        val timestamp = Instant.parse("2026-06-28T09:05:00Z")
        assertEquals("09:05", CoreTextRules.displayTimestamp(timestamp, LocalDate.of(2026, 6, 28), ZoneId.of("UTC"), Locale.US))
    }

    // Verify older history labels use the locale date because time alone would be ambiguous.
    @Test
    fun nonSameDayTimestampRendersLocaleShortDate() {
        val timestamp = Instant.parse("2026-06-27T09:05:00Z")
        assertEquals("6/27/26", CoreTextRules.displayTimestamp(timestamp, LocalDate.of(2026, 6, 28), ZoneId.of("UTC"), Locale.US))
    }
}
