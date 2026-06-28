package com.nofuzznotes.core.undo

import com.nofuzznotes.core.model.TextSelection
import com.nofuzznotes.core.model.UndoDirection
import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.domain.service.TextEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class UndoBoundaryDetectorTest {
    private val baseTime: Instant = Instant.parse("2026-06-28T10:00:00Z")

    // Verify fast typing groups because normal words should undo as one human action.
    @Test
    fun continuousTypingWithinThresholdGroups() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))
        val previous = entry("", "a", baseTime)
        val next = edit("a", "ab", baseTime.plusMillis(499))

        assertTrue(detector.shouldGroup(previous, next))
    }

    // Verify slow typing starts a new group because a pause is the user's mental boundary.
    @Test
    fun typingAfterThresholdCreatesBoundary() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(250))
        val previous = entry("", "a", baseTime)
        val next = edit("a", "ab", baseTime.plusMillis(251))

        assertFalse(detector.shouldGroup(previous, next))
    }

    // Verify punctuation after a word splits because Unicode word boundaries should drive undo chunks.
    @Test
    fun nonWordAfterWordCreatesBoundary() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))
        val previous = entry("", "hello", baseTime)
        val next = edit("hello", "hello,", baseTime.plusMillis(100))

        assertFalse(detector.shouldGroup(previous, next))
    }

    // Verify non-ASCII letters group because note text is Unicode plaintext, not ASCII-only text.
    @Test
    fun unicodeWordCharactersAreHandled() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))
        val previous = entry("", "é", baseTime)
        val next = edit("é", "éc", baseTime.plusMillis(100))

        assertTrue(detector.shouldGroup(previous, next))
    }

    // Verify combining accents stay with their base character because grapheme boundaries must be semantic.
    @Test
    fun combiningMarksDoNotBreakSemanticGrouping() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))
        val text = "e\u0301"

        assertEquals(listOf(text), detector.graphemes(text))
        assertTrue(detector.isWordGrapheme(text))
    }

    // Verify skin tone emoji is atomic because visible emoji must not split into modifier code points.
    @Test
    fun emojiWithSkinToneModifierIsOneGrapheme() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))

        assertEquals(listOf("👍🏽"), detector.graphemes("👍🏽"))
    }

    // Verify ZWJ sequences are atomic when the runtime exposes sequence pieces because family emoji is one visible unit.
    @Test
    fun zwjEmojiSequencesAreTreatedAsOneGrapheme() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))

        assertEquals(listOf("👨‍👩‍👧‍👦"), detector.graphemes("👨‍👩‍👧‍👦"))
    }

    // Verify paste-like operations stand alone because large structural edits should undo as explicit actions.
    @Test
    fun pasteCutReplacementAndClearCreateBoundaries() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))
        val previous = entry("", "a", baseTime)
        val kinds = listOf(
            UndoOperationKind.Paste,
            UndoOperationKind.Cut,
            UndoOperationKind.Replacement,
            UndoOperationKind.Clear,
        )

        kinds.forEach { kind ->
            assertFalse(detector.shouldGroup(previous.copy(operationKind = kind), edit("a", "ab", baseTime.plusMillis(1), kind)))
        }
    }


    // Verify grapheme diff counts deletion text because deletion grouping depends on removed visible units.
    @Test
    fun deletionCountsRemovedGraphemes() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))

        assertEquals(1, detector.removedGraphemeCount("abc", "ab"))
        assertEquals(0, detector.insertedGraphemeCount("abc", "ab"))
    }

    // Verify repeated deletion groups because undoing every single backspace separately is noisy.
    @Test
    fun deletionGroupsSensibly() {
        val detector = UndoBoundaryDetector(Duration.ofMillis(500))
        val previous = entry("abcd", "abc", baseTime, UndoOperationKind.Deletion)
        val next = edit("abc", "ab", baseTime.plusMillis(100), UndoOperationKind.Deletion)

        assertTrue(detector.shouldGroup(previous, next))
    }

    // Build a persisted previous edit because grouping compares against the durable stack top.
    private fun entry(
        before: String,
        after: String,
        created: Instant,
        kind: UndoOperationKind = UndoOperationKind.Typing,
    ) = UndoEntry(
        id = 1L,
        noteId = 1L,
        direction = UndoDirection.Undo,
        operationKind = kind,
        position = 0,
        textBefore = before,
        textAfter = after,
        cursorBefore = before.length,
        cursorAfter = after.length,
        selectionBefore = TextSelection(before.length, before.length),
        selectionAfter = TextSelection(after.length, after.length),
        created = created,
    )

    // Build a new edit because tests vary only timing and operation kind.
    private fun edit(
        before: String,
        after: String,
        timestamp: Instant,
        kind: UndoOperationKind = UndoOperationKind.Typing,
    ) = TextEdit(
        operationKind = kind,
        position = 0,
        textBefore = before,
        textAfter = after,
        cursorBefore = before.length,
        cursorAfter = after.length,
        timestamp = timestamp,
    )
}
