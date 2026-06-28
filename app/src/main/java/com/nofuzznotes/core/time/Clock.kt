package com.nofuzznotes.core.time

import java.time.Instant

interface Clock {
    // Allow services to receive deterministic time without depending on platform clocks.
    fun now(): Instant
}
