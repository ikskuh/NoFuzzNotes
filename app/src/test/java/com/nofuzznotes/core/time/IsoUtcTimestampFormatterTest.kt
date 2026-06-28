package com.nofuzznotes.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class IsoUtcTimestampFormatterTest {
    // Verify persisted timestamps ignore sub-second noise because the spec requires second precision.
    @Test
    fun formatsUtcWithSecondPrecision() {
        val instant = Instant.parse("2026-06-28T10:15:30.987Z")

        assertEquals("2026-06-28T10:15:30Z", IsoUtcTimestampFormatter.format(instant))
    }
}
