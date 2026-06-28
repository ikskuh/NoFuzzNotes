package com.nofuzznotes.core.undo

import com.nofuzznotes.core.model.UndoEntry
import com.nofuzznotes.core.model.UndoOperationKind
import com.nofuzznotes.domain.service.TextEdit
import java.text.BreakIterator
import java.time.Duration
import java.util.Locale

class UndoBoundaryDetector(
    private val pauseThreshold: Duration = Duration.ofMillis(500),
    private val locale: Locale = Locale.ROOT,
) {
    init {
        assert(!pauseThreshold.isNegative)
    }

    // Decide whether to append to the previous operation because only human typing should coalesce.
    fun shouldGroup(previous: UndoEntry?, next: TextEdit): Boolean {
        previous ?: return false
        if (previous.operationKind != next.operationKind) return false
        if (isAlwaysBoundary(next.operationKind)) return false
        if (next.timestamp == null) return false
        if (Duration.between(previous.created, next.timestamp) > pauseThreshold) return false
        return when (next.operationKind) {
            UndoOperationKind.Typing -> shouldGroupTyping(previous.textAfter, next.textAfter)
            UndoOperationKind.Deletion -> shouldGroupDeletion(previous.textAfter, next.textAfter)
            else -> false
        }
    }

    // Count removed graphemes because deletion grouping must not pretend every deletion removed zero text.
    fun removedGraphemeCount(before: String, after: String): Int = changedGraphemes(before, after).removed.size

    // Count inserted graphemes because typing grouping must only coalesce single visible characters.
    fun insertedGraphemeCount(before: String, after: String): Int = changedGraphemes(before, after).inserted.size

    // Treat word characters with combining marks as semantic text because accents must not split groups.
    fun isWordGrapheme(grapheme: String): Boolean {
        assert(grapheme.isNotEmpty())
        if (containsEmojiCodePoint(grapheme)) return true
        return grapheme.codePoints().anyMatch { Character.isLetterOrDigit(it) || it == '_'.code }
    }

    // Split with the platform Unicode character iterator because grouping must be UI-toolkit independent.
    fun graphemes(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(locale)
        iterator.setText(text)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return mergeZwJSequences(result)
    }

    // Keep typing together until semantic word/non-word class changes because word boundaries are undo boundaries.
    private fun shouldGroupTyping(previousAfter: String, nextAfter: String): Boolean {
        val added = changedGraphemes(previousAfter, nextAfter).inserted
        if (added.size != 1) return false
        val previous = graphemes(previousAfter).lastOrNull() ?: return true
        return isWordGrapheme(previous) == isWordGrapheme(added.single())
    }

    // Keep adjacent deletions together because repeated backspace/delete should undo as a small run.
    private fun shouldGroupDeletion(previousAfter: String, nextAfter: String): Boolean {
        val change = changedGraphemes(previousAfter, nextAfter)
        return change.removed.size == 1 && change.inserted.isEmpty()
    }

    // Force structural operations to stand alone because paste-like edits are intentional chunks.
    private fun isAlwaysBoundary(kind: UndoOperationKind): Boolean = when (kind) {
        UndoOperationKind.Paste,
        UndoOperationKind.Cut,
        UndoOperationKind.Replacement,
        UndoOperationKind.Clear,
        UndoOperationKind.Autocorrect,
        UndoOperationKind.CancelEdit,
        UndoOperationKind.SnapshotRestore -> true
        UndoOperationKind.Typing,
        UndoOperationKind.Deletion -> false
    }

    // Return the changed grapheme spans because grouping rules reason about inserted and removed visible units separately.
    private fun changedGraphemes(before: String, after: String): GraphemeChange {
        val prefix = commonPrefixGraphemeEnd(before, after)
        val suffix = commonSuffixGraphemeStarts(before, after, prefix)
        return GraphemeChange(
            removed = graphemes(before.substring(prefix, suffix.beforeStart)),
            inserted = graphemes(after.substring(prefix, suffix.afterStart)),
        )
    }

    // Align prefixes on grapheme boundaries because raw UTF-16 offsets can split emoji and combining marks.
    private fun commonPrefixGraphemeEnd(left: String, right: String): Int {
        var offset = 0
        val leftGraphemes = graphemes(left)
        val rightGraphemes = graphemes(right)
        for (index in 0 until minOf(leftGraphemes.size, rightGraphemes.size)) {
            if (leftGraphemes[index] != rightGraphemes[index]) break
            offset += leftGraphemes[index].length
        }
        return offset
    }

    // Align suffixes on grapheme boundaries because replacement spans must preserve semantic positions.
    private fun commonSuffixGraphemeStarts(left: String, right: String, prefixEnd: Int): SuffixStarts {
        val leftTail = graphemes(left.substring(prefixEnd))
        val rightTail = graphemes(right.substring(prefixEnd))
        var leftMatchedLength = 0
        var rightMatchedLength = 0
        var leftIndex = leftTail.lastIndex
        var rightIndex = rightTail.lastIndex
        while (leftIndex >= 0 && rightIndex >= 0 && leftTail[leftIndex] == rightTail[rightIndex]) {
            leftMatchedLength += leftTail[leftIndex].length
            rightMatchedLength += rightTail[rightIndex].length
            leftIndex -= 1
            rightIndex -= 1
        }
        return SuffixStarts(
            beforeStart = left.length - leftMatchedLength,
            afterStart = right.length - rightMatchedLength,
        )
    }

    // Merge ZWJ emoji chains when the runtime iterator exposes their pieces because user-visible emoji should be atomic.
    private fun mergeZwJSequences(parts: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < parts.size) {
            val builder = StringBuilder(parts[index])
            while (builder.endsWith("‍") && index + 1 < parts.size) {
                index += 1
                builder.append(parts[index])
            }
            result.add(builder.toString())
            index += 1
        }
        return result
    }

    // Recognize broad emoji ranges because emoji graphemes behave as one semantic word for grouping.
    private fun containsEmojiCodePoint(text: String): Boolean = text.codePoints().anyMatch {
        it in 0x1F000..0x1FAFF || it in 0x2600..0x27BF
    }

    private data class GraphemeChange(
        val removed: List<String>,
        val inserted: List<String>,
    )

    private data class SuffixStarts(
        val beforeStart: Int,
        val afterStart: Int,
    )
}
