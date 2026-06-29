package com.nofuzznotes.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.nofuzznotes.domain.service.EditorMode
import com.nofuzznotes.ui.editor.EditorTextSurface
import com.nofuzznotes.ui.editor.TextEditEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class EditorTextSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun viewModeShowsSelectableTextWithoutEditingCallback() {
        // Verify saved drafts expose text but do not persist accidental keyboard input.
        var event: TextEditEvent? = null
        compose.setContent { NoFuzzNotesTheme { EditorTextSurface("Read only", EditorMode.View, { event = it }) } }

        compose.onNodeWithText("Read only").assertIsDisplayed()
        compose.onNodeWithTag("editor-text").assert(!hasSetTextAction())
        compose.runOnIdle { assertNull(event) }
    }

    @Test
    fun editModeAcceptsInputAndReportsSelection() {
        // Verify editable drafts forward the platform cursor to undo persistence.
        var event: TextEditEvent? = null
        compose.setContent { NoFuzzNotesTheme { EditorTextSurface("", EditorMode.Edit, { event = it }) } }

        compose.onNodeWithTag("editor-text").assert(hasSetTextAction())
        compose.onNodeWithTag("editor-text").performTextInput("abc")

        compose.runOnIdle {
            assertEquals("abc", event?.after)
            assertEquals(3, event?.selectionAfter?.end)
        }
    }

    @Test
    fun snapshotModeShowsTextWithoutEditingCallback() {
        // Verify immutable history uses the same protected surface as draft view mode.
        var event: TextEditEvent? = null
        compose.setContent { NoFuzzNotesTheme { EditorTextSurface("Snapshot", EditorMode.ViewSnapshot, { event = it }) } }

        compose.onNodeWithText("Snapshot").assertIsDisplayed()
        compose.onNodeWithTag("editor-text").assert(!hasSetTextAction())
        compose.runOnIdle { assertNull(event) }
    }

    @Test
    fun inputSanitizationWorksThroughComposeSurface() {
        // Verify actual UI input is cleaned before it reaches the application layer.
        var event: TextEditEvent? = null
        compose.setContent { NoFuzzNotesTheme { EditorTextSurface("", EditorMode.Edit, { event = it }) } }

        compose.onNodeWithTag("editor-text").performTextInput("A\r\nB\u0000")

        compose.runOnIdle { assertEquals("A\nB�", event?.after) }
    }
}
