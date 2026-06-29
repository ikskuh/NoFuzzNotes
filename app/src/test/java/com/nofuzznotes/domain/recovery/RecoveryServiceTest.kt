package com.nofuzznotes.domain.recovery

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryServiceTest {
    // Verify a usable database routes normally because startup must open the note list on success.
    @Test
    fun validDatabaseOpensNoteList() {
        val database = FakeRecoverableDatabase(openSucceeds = true)

        val destination = RecoveryService(database).decideStartup()

        assertSame(StartupDestination.NoteList, destination)
    }

    // Verify unusable storage routes to recovery because corrupt databases must not crash startup.
    @Test
    fun corruptDatabaseOpensRecovery() {
        val database = FakeRecoverableDatabase(openSucceeds = false)

        val destination = RecoveryService(database).decideStartup()

        assertTrue(destination is StartupDestination.Recovery)
    }

    // Verify export is available before reset because users need a chance to keep the corrupt file.
    @Test
    fun exportPathCanBeRequestedBeforeReset() {
        val database = FakeRecoverableDatabase(openSucceeds = false)
        val destination = File("requested.db3")

        val exported = RecoveryService(database).exportDatabase(destination)

        assertEquals(destination, exported)
        assertEquals(destination, database.exportedTo)
        assertEquals(0, database.resetCount)
    }

    // Verify reset asks for confirmation at the service boundary because destructive actions need a safe prompt.
    @Test(expected = IllegalStateException::class)
    fun resetRequiresSafePrompt() {
        RecoveryService(FakeRecoverableDatabase(openSucceeds = true)).resetDatabase(confirmedSafePrompt = false)
    }

    // Verify reset returns to the normal route because fresh storage should continue to the note list.
    @Test
    fun confirmedResetCreatesFreshUsableDatabase() {
        val database = FakeRecoverableDatabase(openSucceeds = false)
        val service = RecoveryService(database)

        val destination = service.resetDatabase(confirmedSafePrompt = true)

        assertEquals(1, database.resetCount)
        assertSame(StartupDestination.NoteList, destination)
    }

    // Verify close emits an effect because app exit is a presentation action, not storage mutation.
    @Test
    fun closeAppProducesExitEffect() {
        val database = FakeRecoverableDatabase(openSucceeds = false)

        val effect = RecoveryService(database).closeApp()

        assertSame(RecoveryEffect.ExitApp, effect)
        assertEquals(0, database.resetCount)
    }

    private class FakeRecoverableDatabase(private var openSucceeds: Boolean) : RecoverableDatabase {
        var exportedTo: File? = null
        var resetCount: Int = 0

        // Return configured health because recovery service tests should not depend on SQLite.
        override fun openFresh(): Boolean = openSucceeds

        // Record export destination because recovery only coordinates the request.
        override fun exportTo(destination: File): File {
            exportedTo = destination
            return destination
        }

        // Flip the database healthy because reset creates a fresh usable database.
        override fun resetToFresh() {
            resetCount += 1
            openSucceeds = true
        }
    }
}
