package com.nofuzznotes.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTextEventMapperTest {
    @Test
    fun mapsTextChangesToSanitizedEditEvents() {
        // Verify raw platform input is normalized before the ViewModel can persist it.
        val event = EditorTextEventMapper.map(TextFieldValue(""), TextFieldValue("A\r\nB\u0000", TextRange(4)))

        assertEquals("", event.before)
        assertEquals("A\nB�", event.after)
        assertEquals(0, event.selectionBefore.start)
        assertEquals(4, event.selectionAfter.start)
        assertEquals(4, event.selectionAfter.end)
    }

    @Test
    fun clampsSelectionAfterSanitization() {
        // Verify editor invariants survive platform selection values that no longer fit sanitized text.
        val event = EditorTextEventMapper.map(TextFieldValue("old"), TextFieldValue("new", TextRange(99, 100)))

        assertEquals("old", event.before)
        assertEquals("new", event.after)
        assertEquals(3, event.selectionAfter.start)
        assertEquals(3, event.selectionAfter.end)
    }
}
