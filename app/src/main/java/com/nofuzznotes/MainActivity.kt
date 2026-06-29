package com.nofuzznotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.nofuzznotes.core.time.SystemClock
import com.nofuzznotes.data.room.NoFuzzNotesDatabase
import com.nofuzznotes.data.room.RoomNoteRepository
import com.nofuzznotes.data.room.recovery.RoomRecoverableDatabase
import com.nofuzznotes.data.room.RoomSnapshotRepository
import com.nofuzznotes.data.room.RoomUndoRedoRepository
import com.nofuzznotes.domain.recovery.RecoveryService
import com.nofuzznotes.domain.service.FullTextSearchService
import com.nofuzznotes.domain.service.HistoryService
import com.nofuzznotes.domain.service.NoteLifecycleService
import com.nofuzznotes.domain.service.TitleSearchService
import com.nofuzznotes.domain.service.TrashService
import com.nofuzznotes.domain.service.UndoRedoService
import com.nofuzznotes.ui.AppDependencies
import com.nofuzznotes.ui.DebugExceptionReporter
import com.nofuzznotes.ui.NoFuzzNotesApp
import com.nofuzznotes.ui.buildExportService

class MainActivity : ComponentActivity() {
    // Start the Compose shell with Room-backed services because UI must consume presentation state only.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugExceptionReporter.install(this)
        val recovery = RoomRecoverableDatabase(applicationContext, DatabaseName)
        val startInRecovery = !recovery.openFresh()
        val dependencies = buildDependencies(recovery)
        setContent { NoFuzzNotesApp(dependencies = dependencies, startInRecovery = startInRecovery, onClose = ::finish) }
    }

    // Build dependencies explicitly because the MVP has no DI framework and needs obvious wiring.
    private fun buildDependencies(recovery: RoomRecoverableDatabase): AppDependencies {
        val database = Room.databaseBuilder(applicationContext, NoFuzzNotesDatabase::class.java, DatabaseName).allowMainThreadQueries().build()
        val clock = SystemClock()
        val notes = RoomNoteRepository(database, clock)
        val snapshots = RoomSnapshotRepository(database, clock)
        val undoRedoRepository = RoomUndoRedoRepository(database, clock)
        val lifecycle = NoteLifecycleService(notes, snapshots, undoRedoRepository)
        val trash = TrashService(notes, snapshots, undoRedoRepository)
        val titleSearch = TitleSearchService(notes)
        val fullTextSearch = FullTextSearchService(notes, snapshots)
        val history = HistoryService(notes, snapshots, undoRedoRepository)
        val undoRedo = UndoRedoService(notes, undoRedoRepository)
        return AppDependencies(notes, snapshots, undoRedoRepository, lifecycle, trash, titleSearch, fullTextSearch, history, undoRedo, buildExportService(), RecoveryService(recovery))
    }

    companion object {
        private const val DatabaseName = "nofuzznotes.db3"
    }
}
