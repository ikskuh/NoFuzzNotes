package com.nofuzznotes.presentation.search

import androidx.lifecycle.ViewModel
import com.nofuzznotes.domain.service.FullTextSearchDepth
import com.nofuzznotes.domain.service.FullTextSearchResult
import com.nofuzznotes.domain.service.FullTextSearchScope
import com.nofuzznotes.domain.service.FullTextSearchService
import com.nofuzznotes.domain.service.FullTextSearchTarget
import com.nofuzznotes.presentation.common.AppRoute
import com.nofuzznotes.presentation.common.EffectBuffer
import com.nofuzznotes.presentation.common.PresentationEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class SearchState(
    val query: String = "",
    val scope: FullTextSearchScope = FullTextSearchScope.Notes,
    val depth: FullTextSearchDepth = FullTextSearchDepth.Latest,
    val results: List<FullTextSearchResult> = emptyList(),
)

class SearchViewModel(private val search: FullTextSearchService) : ViewModel() {
    private val effectBuffer = EffectBuffer()
    private val mutableState = MutableStateFlow(SearchState())

    val state: StateFlow<SearchState> = mutableState
    val effects = effectBuffer.effects

    // Update query and search immediately because the state is the entire full-text screen model.
    fun queryChanged(query: String) { mutableState.value = mutableState.value.copy(query = query).searched() }

    // Update scope and search immediately because scope changes which notebooks are visible.
    fun scopeChanged(scope: FullTextSearchScope) { mutableState.value = mutableState.value.copy(scope = scope).searched() }

    // Update depth and search immediately because old snapshots are optional in MVP search.
    fun depthChanged(depth: FullTextSearchDepth) { mutableState.value = mutableState.value.copy(depth = depth).searched() }

    // Navigate to the matching target because draft and old snapshot results open different surfaces.
    fun openResult(index: Int) {
        val result = mutableState.value.results[index]
        val route = when (result.target) {
            FullTextSearchTarget.Draft -> AppRoute.Editor(result.note.id)
            FullTextSearchTarget.Snapshot -> AppRoute.SnapshotViewer(result.note.id, result.snapshot?.id ?: error("Snapshot result requires snapshot"))
        }
        effectBuffer.emit(PresentationEffect.Navigate(route))
    }

    // Execute full-text search from immutable options because UI rendering must not duplicate search rules.
    private fun SearchState.searched(): SearchState = copy(results = if (query.isBlank()) emptyList() else search.search(query, scope, depth))
}
