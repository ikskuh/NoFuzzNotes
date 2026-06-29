package com.nofuzznotes.presentation.recovery

import androidx.lifecycle.ViewModel
import com.nofuzznotes.domain.recovery.RecoveryEffect
import com.nofuzznotes.domain.recovery.RecoveryService
import com.nofuzznotes.domain.service.ExportService
import com.nofuzznotes.presentation.common.AppRoute
import com.nofuzznotes.presentation.common.EffectBuffer
import com.nofuzznotes.presentation.common.PresentationEffect
import com.nofuzznotes.presentation.common.PromptKind
import com.nofuzznotes.presentation.common.PromptMessages
import com.nofuzznotes.presentation.common.PromptState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class RecoveryState(val prompt: PromptState? = null)

class RecoveryViewModel(private val recovery: RecoveryService, private val export: ExportService) : ViewModel() {
    private val effectBuffer = EffectBuffer()
    private val mutableState = MutableStateFlow(RecoveryState())

    val state: StateFlow<RecoveryState> = mutableState
    val effects = effectBuffer.effects

    // Emit database export because recovery file copying is handled outside Compose.
    fun exportDatabase() { effectBuffer.emit(PresentationEffect.ExportDatabase(export.exportDatabase())) }

    // Ask for a safe prompt because reset permanently replaces storage.
    fun reset() { mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.ResetDatabase, PromptMessages.RESET_DATABASE, isSafe = true)) }

    // Reset after confirmation and navigate back to the normal notebook route.
    fun confirmReset() {
        val prompt = mutableState.value.prompt?.takeIf { it.kind == PromptKind.ResetDatabase } ?: error("Reset prompt is required")
        assert(prompt.isSafe)
        recovery.resetDatabase(confirmedSafePrompt = true)
        mutableState.value = mutableState.value.copy(prompt = null)
        effectBuffer.emit(PresentationEffect.Navigate(AppRoute.NoteList))
    }

    // Emit close because tests should verify exit intent without closing the process.
    fun close() {
        when (recovery.closeApp()) {
            RecoveryEffect.ExitApp -> effectBuffer.emit(PresentationEffect.CloseApp)
        }
    }

    // Clear prompts because dismissal must not reset storage.
    fun dismissPrompt() { mutableState.value = mutableState.value.copy(prompt = null) }
}
