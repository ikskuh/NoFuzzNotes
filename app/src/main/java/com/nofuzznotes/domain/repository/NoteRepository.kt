package com.nofuzznotes.domain.repository

import com.nofuzznotes.core.model.Note

interface NoteRepository {
    // Create an empty durable row because a new note must exist before it has user content.
    fun createEmpty(): Note

    // Read by id so services can reload a note aggregate without database dependencies.
    fun read(id: Long): Note?

    // Replace draft content and edited time because draft persistence happens at the repository boundary.
    fun updateContent(id: Long, content: String): Note

    // Update edited without content changes because pressing save is itself a persisted note event.
    fun touchEdited(id: Long): Note
}
