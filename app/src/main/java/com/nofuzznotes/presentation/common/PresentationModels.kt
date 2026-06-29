package com.nofuzznotes.presentation.common

import com.nofuzznotes.domain.service.DatabaseExportRequest
import com.nofuzznotes.domain.service.ShareRequest
import com.nofuzznotes.domain.service.TextExportRequest

sealed interface AppRoute {
    data object NoteList : AppRoute
    data object TrashList : AppRoute
    data object Search : AppRoute
    data class Editor(val noteId: Long) : AppRoute
    data class History(val noteId: Long) : AppRoute
    data class SnapshotViewer(val noteId: Long, val snapshotId: Long) : AppRoute
    data object Recovery : AppRoute
}

sealed interface PresentationEffect {
    data class Navigate(val route: AppRoute) : PresentationEffect
    data class ExportText(val request: TextExportRequest) : PresentationEffect
    data class ShareText(val request: ShareRequest) : PresentationEffect
    data class ExportDatabase(val request: DatabaseExportRequest) : PresentationEffect
    data object CloseApp : PresentationEffect
}

data class PromptState(
    val kind: PromptKind,
    val message: String,
    val targetId: Long? = null,
    val isSafe: Boolean = false,
)

enum class PromptKind {
    DeleteNote,
    EmptyTrash,
    DestroyNote,
    UntrashNote,
    CancelEdit,
    RestoreSnapshot,
    ResetDatabase,
}

enum class NoteSortMode {
    CreatedAscending,
    CreatedDescending,
    EditedAscending,
    EditedDescending,
    TitleAscending,
    TitleDescending,
}

object PromptMessages {
    const val DELETE_NOTE = "Do you want to delete this note?"
    const val PERMANENT_DELETE_NOTE = "Do you want to permanently delete this note?"
    const val RESET_DATABASE = "If you reset the database, all your notes are gone without recovery. Are you really sure you want to delete the file?"

    // Include the affected count because empty trash prompts must identify the destructive scope without listing notes.
    fun emptyTrash(count: Int): String {
        assert(count >= 0)
        return "Do you really want to delete $count notes forever?"
    }

    // Keep non-specified prompt text centralized because Compose should render, not invent, dialog copy.
    fun basic(action: String): String {
        assert(action.isNotBlank())
        return action
    }
}
