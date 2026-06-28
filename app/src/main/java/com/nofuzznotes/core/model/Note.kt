package com.nofuzznotes.core.model

import com.nofuzznotes.core.text.CoreTextRules
import java.time.Instant

data class Note(
    val id: Long,
    val content: String,
    val created: Instant,
    val edited: Instant,
    val deleted: Instant?,
) {
    init {
        assert(id > 0L)
        assert(!edited.isBefore(created))
    }

    val title: String
        // Derive title on demand because title is content, not independently persisted metadata.
        get() = CoreTextRules.extractTitle(content)

    // Keep trash checks on the model because every caller must use the same deleted-null rule.
    fun isTrashed(): Boolean = deleted != null
}
