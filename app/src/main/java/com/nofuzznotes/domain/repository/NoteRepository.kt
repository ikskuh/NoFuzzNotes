package com.nofuzznotes.domain.repository

import com.nofuzznotes.core.model.Note

interface NoteRepository {
    // Create an empty durable row because a new note must exist before it has user content.
    fun createEmpty(): Note

    // Read by id so services can reload a note aggregate without database dependencies.
    fun read(id: Long): Note?

    // List active notes because trash state must separate normal notebook browsing.
    fun listNormal(): List<Note>

    // List trashed notes because recoverable deletion has its own browsing scope.
    fun listTrash(): List<Note>

    // Replace draft content and edited time because draft persistence happens at the repository boundary.
    fun updateContent(id: Long, content: String): Note

    // Update edited without content changes because pressing save is itself a persisted note event.
    fun touchEdited(id: Long): Note

    // Mark a note trashed because deletion must be recoverable before destruction.
    fun markDeleted(id: Long): Note

    // Clear trash state because untrash must preserve all note content and history.
    fun clearDeleted(id: Long): Note

    // Remove a note row because permanent destruction cannot be undone.
    fun destroy(id: Long)
}
