package com.nofuzznotes.domain.recovery

import java.io.File

sealed interface StartupDestination {
    data object NoteList : StartupDestination
    data class Recovery(val reason: String) : StartupDestination
}

sealed interface RecoveryEffect {
    data object ExitApp : RecoveryEffect
}

interface RecoverableDatabase {
    // Check usability before normal navigation because corrupt storage must not crash startup.
    fun openFresh(): Boolean

    // Copy the current database before destructive reset because recovery must preserve user data when requested.
    fun exportTo(destination: File): File

    // Replace storage with an empty usable database because reset is the recovery path back to normal startup.
    fun resetToFresh()
}

class RecoveryService(private val database: RecoverableDatabase) {
    // Decide startup route in one place because UI navigation must wait for recovery checks.
    fun decideStartup(): StartupDestination {
        return if (database.openFresh()) StartupDestination.NoteList else StartupDestination.Recovery("Database could not be opened")
    }

    // Export the database through the storage adapter because presentation only chooses the destination.
    fun exportDatabase(destination: File): File {
        check(destination.name.isNotBlank())
        return database.exportTo(destination)
    }

    // Reset storage only after confirmation because reset permanently removes all notes.
    fun resetDatabase(confirmedSafePrompt: Boolean): StartupDestination {
        check(confirmedSafePrompt)
        database.resetToFresh()
        return decideStartup()
    }

    // Return an explicit effect because tests should verify app exit without closing a process.
    fun closeApp(): RecoveryEffect = RecoveryEffect.ExitApp
}
