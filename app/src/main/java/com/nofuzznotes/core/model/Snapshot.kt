package com.nofuzznotes.core.model

import com.nofuzznotes.core.text.CoreTextRules
import java.time.Instant

data class Snapshot(
    val id: Long,
    val noteId: Long,
    val content: String,
    val created: Instant,
) {
    init {
        assert(id > 0L)
        assert(noteId > 0L)
    }

    val title: String
        // Derive title from saved content because snapshots are immutable copies of note text.
        get() = CoreTextRules.extractTitle(content)
}
