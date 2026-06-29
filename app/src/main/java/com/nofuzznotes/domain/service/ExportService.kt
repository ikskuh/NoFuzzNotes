package com.nofuzznotes.domain.service

import com.nofuzznotes.core.text.CoreTextRules
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TextExportRequest(
    val suggestedFileName: String,
    val content: String,
    val bytes: ByteArray,
) {
    init {
        assert(suggestedFileName.isNotEmpty())
        assert(content == CoreTextRules.normalizeLf(content))
        assert(bytes.contentEquals(content.toByteArray(StandardCharsets.UTF_8)))
    }

    // Compare byte arrays by value because export bytes are the durable payload under test.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextExportRequest) return false
        return suggestedFileName == other.suggestedFileName && content == other.content && bytes.contentEquals(other.bytes)
    }

    // Hash byte arrays by value because equals treats the UTF-8 payload as request data.
    override fun hashCode(): Int {
        var result = suggestedFileName.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class ShareRequest(
    val content: String,
)

data class DatabaseExportRequest(
    val suggestedFileName: String,
    val includesFullDatabase: Boolean,
) {
    init {
        assert(suggestedFileName.endsWith(".db3"))
        assert(includesFullDatabase)
    }
}

fun interface LocalDateProvider {
    // Provide the user's current date because database backup names are date-stamped without platform UI.
    fun today(): LocalDate
}

class ExportService(
    private val dateProvider: LocalDateProvider,
) {
    // Build a text export effect request because Android file picking is attached later outside domain logic.
    fun exportDisplayedContent(displayedContent: DisplayedContent): TextExportRequest {
        val content = CoreTextRules.normalizeLf(displayedContent.exportText())
        return TextExportRequest(
            suggestedFileName = CoreTextRules.exportFilename(content),
            content = content,
            bytes = content.toByteArray(StandardCharsets.UTF_8),
        )
    }

    // Build a share effect request from visible text because sharing must not reload or mutate notebook state.
    fun shareDisplayedContent(displayedContent: DisplayedContent): ShareRequest {
        return ShareRequest(content = displayedContent.shareText())
    }

    // Build a backup effect request because the future platform handler will copy the complete database file.
    fun exportDatabase(): DatabaseExportRequest {
        val date = DateTimeFormatter.ISO_LOCAL_DATE.format(dateProvider.today())
        return DatabaseExportRequest(suggestedFileName = "notes-$date.db3", includesFullDatabase = true)
    }
}
