package com.nofuzznotes.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nofuzznotes.core.model.Note
import com.nofuzznotes.core.model.Snapshot
import com.nofuzznotes.domain.service.FullTextSearchResult
import com.nofuzznotes.domain.service.FullTextSearchTarget
import com.nofuzznotes.domain.service.SnapshotListItem
import com.nofuzznotes.presentation.common.PromptKind
import com.nofuzznotes.presentation.common.PromptMessages
import com.nofuzznotes.presentation.common.PromptState
import com.nofuzznotes.presentation.history.HistoryState
import com.nofuzznotes.presentation.list.NoteListItem
import com.nofuzznotes.presentation.list.NoteListState
import com.nofuzznotes.presentation.recovery.RecoveryState
import com.nofuzznotes.presentation.search.SearchState
import com.nofuzznotes.presentation.trash.TrashListItem
import com.nofuzznotes.presentation.trash.TrashListState
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ComposeShellTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun noteListRendersTitlesOnlyIncludingWhitespaceTitles() {
        // Verify title rows stay title-only because note bodies are not allowed in lists.
        compose.setContent { NoFuzzNotesTheme { NoteListScreen(noteListState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }

        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("   ").assertIsDisplayed()
        compose.onNodeWithText("body must not render").assertDoesNotExist()
    }

    @Test
    fun noteListToolbarActionsMatchSelectionState() {
        // Verify normal-list actions are controlled by selection state rather than row content.
        compose.setContent { NoFuzzNotesTheme { NoteListScreen(noteListState(canSelect = false), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Export").assertDoesNotExist()
        compose.onNodeWithText("Share").assertDoesNotExist()

        compose.setContent { NoFuzzNotesTheme { NoteListScreen(noteListState(canSelect = true), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Delete").assertIsEnabled()
        compose.onNodeWithText("Export").assertIsEnabled()
        compose.onNodeWithText("Share").assertIsEnabled()
    }

    @Test
    fun trashToolbarActionsMatchSelectionState() {
        // Verify trash actions stay unavailable until a trashed note is selected.
        compose.setContent { NoFuzzNotesTheme { TrashListScreen(trashState(false), {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Destroy").assertDoesNotExist()
        compose.onNodeWithText("Restore").assertDoesNotExist()

        compose.setContent { NoFuzzNotesTheme { TrashListScreen(trashState(true), {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Destroy").assertIsEnabled()
        compose.onNodeWithText("Restore").assertIsEnabled()
    }

    @Test
    fun promptsDisplayTextAndSafePromptsDisableOkUntilChecked() {
        // Verify safe prompts require an explicit irreversible-action acknowledgement.
        val prompt = PromptState(PromptKind.ResetDatabase, PromptMessages.RESET_DATABASE, isSafe = true)
        compose.setContent { NoFuzzNotesTheme { PromptDialog(prompt, {}, {}) } }

        compose.onNodeWithText(PromptMessages.RESET_DATABASE).assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNodeWithText("I'm sure").performClick()
        compose.onNodeWithText("OK").assertIsEnabled()
    }

    @Test
    fun historyListShowsTimestampsAndTitles() {
        // Verify history rows expose saved-version identity rather than draft-only labels.
        compose.setContent { NoFuzzNotesTheme { HistoryScreen(historyState(), {}, {}, {}, {}) } }

        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithText("Saved title").assertIsDisplayed()
    }

    @Test
    fun fullTextOldResultsAreMarkedOld() {
        // Verify old full-text matches are visibly distinct because they open snapshot view.
        compose.setContent { NoFuzzNotesTheme { SearchScreen(searchState(), {}, {}, {}, {}) } }

        compose.onNodeWithText("Archived").assertIsDisplayed()
        compose.onNodeWithText("Old").assertIsDisplayed()
    }

    @Test
    fun recoveryScreenExposesExportResetAndClose() {
        // Verify recovery exposes every exit path required before normal navigation.
        compose.setContent { NoFuzzNotesTheme { RecoveryScreen(RecoveryState(), {}, {}, {}, {}, {}) } }

        compose.onNodeWithText("Export Database").assertIsDisplayed()
        compose.onNodeWithText("Reset Database").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    // Build note-list state once because UI tests should not depend on repositories.
    private fun noteListState(canSelect: Boolean = false) = NoteListState(
        notes = listOf(NoteListItem(1, "Alpha"), NoteListItem(2, "   ")),
        selectedNoteId = if (canSelect) 1 else null,
        canDelete = canSelect,
        canExport = canSelect,
        canShare = canSelect,
    )

    // Build trash state once because toolbar enablement comes from state flags only.
    private fun trashState(canSelect: Boolean) = TrashListState(
        notes = listOf(TrashListItem(1, "Deleted")),
        selectedNoteId = if (canSelect) 1 else null,
        canDestroy = canSelect,
        canUntrash = canSelect,
    )

    // Build history state with stable text because timestamps are rendered directly.
    private fun historyState() = HistoryState(1, listOf(SnapshotListItem(1, 1, "Saved title", Instant.parse("2026-06-29T00:00:00Z"))))

    // Build search state with an old snapshot because the shell must label non-draft hits.
    private fun searchState(): SearchState {
        val note = Note(1, "Archived", Instant.parse("2026-06-29T00:00:00Z"), Instant.parse("2026-06-29T00:00:00Z"), null)
        val snapshot = Snapshot(1, 1, "Archived", Instant.parse("2026-06-29T00:00:00Z"))
        return SearchState(results = listOf(FullTextSearchResult(note, FullTextSearchTarget.Snapshot, snapshot, 0, true)))
    }
}
