package com.nofuzznotes.presentation.common

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class EffectBuffer {
    private val mutableEffects = Channel<PresentationEffect>(capacity = Channel.UNLIMITED)

    val effects: Flow<PresentationEffect> = mutableEffects.receiveAsFlow()

    // Buffer effects because tests and UI collect one-shot actions independently from state.
    fun emit(effect: PresentationEffect) {
        val accepted = mutableEffects.trySend(effect).isSuccess
        assert(accepted)
    }
}
