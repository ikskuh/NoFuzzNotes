package com.nofuzznotes.core.text

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object CoreTextRules {
    private const val ReplacementCharacter = '\uFFFD'

    // Preserve user-authored title text exactly because titles are content-derived, not metadata.
    fun extractTitle(content: String): String {
        return content.lineSequence().firstOrNull() ?: ""
    }

    // Normalize platform newline variants because persisted note text must use LF consistently.
    fun normalizeLf(text: String): String {
        return text.replace("\r\n", "\n").replace('\r', '\n')
    }

    // Replace unsupported controls one-by-one because paste cleanup must not silently merge user input.
    fun sanitizePaste(text: String): String {
        val normalized = normalizeLf(text)
        val sanitized = StringBuilder(normalized.length)
        for (character in normalized) {
            sanitized.append(if (isInvalidControl(character)) ReplacementCharacter else character)
        }
        return sanitized.toString()
    }

    // Derive filenames from content only because export must not invent or sanitize note metadata.
    fun exportFilename(content: String): String {
        return extractTitle(content) + ".txt"
    }

    // Compare against nullable history content because a missing snapshot always means unsaved work exists.
    fun hasPendingChanges(draft: String, latestSnapshotContent: String?): Boolean {
        return latestSnapshotContent == null || draft != latestSnapshotContent
    }

    // Render timestamps in the user's local calendar because history is stored UTC but displayed locally.
    fun displayTimestamp(timestamp: Instant, today: LocalDate, zoneId: ZoneId, locale: Locale): String {
        val localDateTime = timestamp.atZone(zoneId).toLocalDateTime()
        return if (localDateTime.toLocalDate() == today) {
            DateTimeFormatter.ofPattern("HH:mm", locale).format(localDateTime)
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(localDateTime)
        }
    }

    // Reject only controls that cannot be represented safely in plain note text.
    private fun isInvalidControl(character: Char): Boolean {
        return (character.code in 0x00..0x1F && character != '\n' && character != '\t') || character.code == 0x7F
    }
}
