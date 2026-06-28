package com.nofuzznotes.core.time

import java.time.Instant

class SystemClock : Clock {
    // Read the host clock at the boundary so domain code can stay deterministic in tests.
    override fun now(): Instant = Instant.now()
}
