package com.nofuzznotes.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TestClockTest {
    // Verify deterministic time because services must not rely on wall-clock behavior in tests.
    @Test
    fun returnsConfiguredInstant() {
        val first = Instant.parse("2026-06-28T10:15:30Z")
        val second = Instant.parse("2026-06-28T10:16:30Z")
        val clock = TestClock(first)

        assertEquals(first, clock.now())
        clock.set(second)
        assertEquals(second, clock.now())
    }
}
