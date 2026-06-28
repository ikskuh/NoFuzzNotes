package com.nofuzznotes.core.time

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object IsoUtcTimestampFormatter {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    // Truncate to seconds because persisted timestamps must be stable across storage layers.
    fun format(instant: Instant): String {
        return formatter.format(instant.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC))
    }
}
