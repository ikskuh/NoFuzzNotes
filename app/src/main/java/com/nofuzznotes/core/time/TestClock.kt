package com.nofuzznotes.core.time

import java.time.Instant

class TestClock(initialInstant: Instant) : Clock {
    private var currentInstant: Instant = initialInstant

    // Return the configured instant so tests can assert exact timestamps.
    override fun now(): Instant = currentInstant

    // Move time explicitly because tests should never wait for wall-clock time.
    fun set(instant: Instant) {
        currentInstant = instant
    }
}
