package com.nofuzznotes.presentation.history

import androidx.lifecycle.ViewModel
import com.nofuzznotes.domain.service.HistoryService
import com.nofuzznotes.domain.service.SnapshotListItem
import com.nofuzznotes.presentation.common.AppRoute
import com.nofuzznotes.presentation.common.EffectBuffer
import com.nofuzznotes.presentation.common.PresentationEffect
import com.nofuzznotes.presentation.common.PromptKind
import com.nofuzznotes.presentation.common.PromptMessages
import com.nofuzznotes.presentation.common.PromptState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class HistoryState(val noteId: Long, val snapshots: List<SnapshotListItem> = emptyList(), val prompt: PromptState? = null)

class HistoryViewModel(private val noteId: Long, private val history: HistoryService) : ViewModel() {
    private val effectBuffer = EffectBuffer()
    private val mutableState = MutableStateFlow(HistoryState(noteId))

    val state: StateFlow<HistoryState> = mutableState
    val effects = effectBuffer.effects

    init { refresh() }

    // Reload snapshots newest-first because save history is immutable but restore navigation depends on current rows.
    fun refresh() { mutableState.value = mutableState.value.copy(snapshots = history.listSnapshotsNewestFirst(noteId)) }

    // Open snapshot viewer because snapshots are read-only editor surfaces.
    fun viewSnapshot(snapshotId: Long) { effectBuffer.emit(PresentationEffect.Navigate(AppRoute.SnapshotViewer(noteId, snapshotId))) }

    // Ask before restore because rollback rewrites the current draft.
    fun restore(snapshotId: Long) { mutableState.value = mutableState.value.copy(prompt = PromptState(PromptKind.RestoreSnapshot, PromptMessages.basic("Restore this snapshot?"), snapshotId)) }

    // Restore after confirmation and navigate to the draft editor because restored content is pending.
    fun confirmRestore() {
        val snapshotId = mutableState.value.prompt?.takeIf { it.kind == PromptKind.RestoreSnapshot }?.targetId ?: error("Restore prompt is required")
        history.restoreSnapshot(noteId, snapshotId)
        mutableState.value = mutableState.value.copy(prompt = null)
        effectBuffer.emit(PresentationEffect.Navigate(AppRoute.Editor(noteId)))
    }

    // Clear prompts because dismissal must not restore history.
    fun dismissPrompt() { mutableState.value = mutableState.value.copy(prompt = null) }
}
