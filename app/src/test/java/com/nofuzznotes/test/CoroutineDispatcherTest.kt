package com.nofuzznotes.test

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineDispatcherTest {
    // Prove coroutine tests can advance virtual work because services will use dispatchers later.
    @Test
    fun standardTestDispatcherRunsServiceStyleWork() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val result = async(dispatcher) { "done" }

        testScheduler.runCurrent()

        assertEquals("done", result.await())
    }
}
