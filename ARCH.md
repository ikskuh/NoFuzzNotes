Notepad Architecture and Software Stack

1. Purpose

This document describes the architecture and implementation plan for the Notepad Android application.

The app is a local-first plaintext note-taking application. A note is stored as durable draft text in a SQLite database. Explicit saves create immutable snapshots. The app supports persistent undo/redo, trash, full-text search, database backup, and simple export/share.

The implementation should begin with the complete core logic and storage behavior before any UI is attached. The UI must be a thin layer over tested application logic.

2. Architectural Overview

2.1 Goals

The architecture shall optimize for:

- fast startup
- maintainability
- testability
- local-first behavior
- simple, predictable data flow
- durable persistence of all user work
- minimal UI coupling
- no unnecessary abstractions

The app shall be structured so that almost all behavior can be tested without Android UI tests.

2.2 High-Level Architecture

The app uses a layered architecture:

UI Layer
  Jetpack Compose screens
  Android text editor surface
  Dialogs, menus, navigation

Presentation Layer
  ViewModels
  screen state
  UI events
  navigation effects

Application Layer
  use cases / services
  note lifecycle operations
  save/cancel/restore/trash/search/export orchestration

Domain Layer
  pure Kotlin domain models
  text normalization
  title extraction
  pending-change rules
  undo/redo engine
  fuzzy search ranking

Data Layer
  repository interfaces
  Room DAOs
  SQLite database
  database export
  recovery handling

Dependencies flow downward:

UI -> Presentation -> Application -> Domain
                         |
                         v
                       Data interfaces
                         |
                         v
                       Room/SQLite implementation

The domain layer must not depend on Android, Room, Compose, ViewModel, or platform APIs.

2.3 Core Architectural Rule

The core app behavior must be usable without UI.

The first implementation increments shall provide a tested functional notebook engine that can:

- create notes
- edit drafts
- save snapshots
- cancel edits
- restore snapshots
- track pending changes
- persist undo/redo
- trash/untrash/destroy notes
- search notes and snapshots
- export note content
- export the full database

Only after this is functional should Compose screens be attached.

3. Software Stack Choices

3.1 Language

Use Kotlin.

Reasons:

- native Android-first language
- concise data modeling
- good coroutine support
- strong null-safety
- good testability
- first-class support in Android tooling

3.2 UI

Use Jetpack Compose for the app shell.

Compose should be used for:

- note list
- trash list
- history list
- full-text search
- recovery screen
- toolbar/menu layout
- dialogs
- simple read-only views
- navigation shell

The editor itself should be isolated behind an editor component boundary because text editing is the most behaviorally complex part of the app.

3.3 Editor Component

The editor shall be represented internally as a replaceable component.

Preferred starting point:

- Compose state-based text input if it can support the required interception and state control cleanly.

Fallback:

- Android "EditText"/"TextView" hosted inside Compose through "AndroidView".

The rest of the app must not depend on which editor implementation is used.

The editor component must expose logical text-edit operations to the application layer.

3.4 Storage

Use Room over SQLite.

Room is used for:

- schema definition
- DAOs
- queries
- transactions
- database creation
- SQLite access safety

SQLite remains the conceptual source of truth.

The database is the notebook.

3.5 Async and State

Use:

- Kotlin coroutines
- Flow
- StateFlow

Repositories may expose "Flow" for observable lists and note state.

ViewModels expose "StateFlow<ScreenState>" to the UI.

3.6 Presentation

Use:

- AndroidX ViewModel
- immutable screen state objects
- explicit UI events
- one-shot effects for navigation and external actions

The UI sends events to ViewModels.

ViewModels call application services.

Application services call repositories.

3.7 Dependency Management

Use manual dependency wiring for the MVP.

Do not introduce heavy dependency injection frameworks initially.

A small application-level composition root is enough.

3.8 Navigation

Use a simple typed route model.

Compose Navigation may be used if it stays lightweight.

Navigation decisions should be driven by ViewModel effects, not by business logic hidden inside Composables.

3.9 Performance

Use:

- R8 for release builds
- Baseline Profiles
- Startup Profiles
- Macrobenchmark for startup and scrolling
- lightweight app startup path

Startup must avoid eager work.

The app should not perform expensive database or search operations during launch unless required.

3.10 Testing

Use:

- JVM unit tests for domain logic
- repository tests with in-memory/fake implementations
- Room integration tests
- ViewModel tests with coroutine test dispatchers
- Android instrumentation tests only where platform behavior is required
- Macrobenchmark tests for startup and list scrolling

4. Module / Package Structure

For the MVP, use one Android Gradle module with clear package boundaries.

Avoid premature multi-module setup.

Suggested package layout:

app/
  core/
    model/
    text/
    undo/
    search/
    time/

  domain/
    service/
    repository/
    usecase/

  data/
    db/
    dao/
    entity/
    mapper/
    repository/
    export/

  presentation/
    common/
    list/
    trash/
    editor/
    history/
    search/
    recovery/

  ui/
    common/
    list/
    trash/
    editor/
    history/
    search/
    recovery/
    navigation/

  app/
    AppCompositionRoot
    MainActivity

4.1 Core Package

Contains pure Kotlin logic.

Examples:

- "TitleExtractor"
- "TextNormalizer"
- "PendingChangeDetector"
- "UndoBoundaryDetector"
- "UndoEngine"
- "FuzzyMatcher"
- "TimestampFormatter"

4.2 Domain Package

Contains app-level business operations.

Examples:

- "CreateNote"
- "OpenNote"
- "EditNote"
- "SaveNote"
- "CancelEdit"
- "RestoreSnapshot"
- "TrashNote"
- "UntrashNote"
- "DestroyNote"
- "SearchNotes"
- "ExportDisplayedContent"

4.3 Data Package

Contains persistence implementation.

Examples:

- Room database
- DAOs
- entities
- repository implementations
- database export
- corrupt database handling

4.4 Presentation Package

Contains ViewModels and UI state models.

Examples:

- "NoteListViewModel"
- "TrashListViewModel"
- "EditorViewModel"
- "HistoryViewModel"
- "SearchViewModel"
- "RecoveryViewModel"

4.5 UI Package

Contains Compose screens and platform UI glue.

UI code must not contain domain rules.

5. Core Domain Model

5.1 Domain Types

value class NoteId(val value: Long)
value class SnapshotId(val value: Long)
value class UndoEntryId(val value: Long)

data class Note(
    val id: NoteId,
    val content: String,
    val created: Instant,
    val edited: Instant,
    val deleted: Instant?
)

data class Snapshot(
    val id: SnapshotId,
    val noteId: NoteId,
    val content: String,
    val created: Instant
)

data class TextSelection(
    val start: Int,
    val end: Int
)

data class EditorCursorState(
    val selection: TextSelection
)

enum class EditorMode {
    ViewDraft,
    EditDraft,
    ViewSnapshot
}

Internally, timestamps are persisted as ISO UTC strings with second precision.

Domain code may use "Instant" or a small wrapper, but persistence conversion must be explicit.

5.2 Title Extraction

The title is always the first line of the relevant content.

Rules:

- the title includes leading spaces
- the title excludes trailing newline
- trailing spaces before the newline are not part of the title display rule only if explicitly trimmed by first-line extraction rules
- empty first line means empty title
- whitespace-only title is valid
- no title uniqueness requirement exists

A single title extraction function must be used everywhere.

interface TitleExtractor {
    fun titleOf(content: String): String
}

5.3 Pending Changes

interface PendingChangeDetector {
    fun hasPendingChanges(
        draft: String,
        latestSnapshotContent: String?
    ): Boolean
}

Rule:

draft != latestSnapshotContent

If latest snapshot is "null", the note always has pending changes.

6. Storage Model

6.1 Conceptual Tables

CREATE TABLE Notes (
    id INTEGER PRIMARY KEY,
    content TEXT NOT NULL,
    created TEXT NOT NULL,
    edited TEXT NOT NULL,
    deleted TEXT NULL
);

CREATE TABLE History (
    id INTEGER PRIMARY KEY,
    note_id INTEGER NOT NULL REFERENCES Notes(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created TEXT NOT NULL
);

CREATE TABLE UndoRedo (
    id INTEGER PRIMARY KEY,
    note_id INTEGER NOT NULL REFERENCES Notes(id) ON DELETE CASCADE,
    stack TEXT NOT NULL,
    operation_kind TEXT NOT NULL,
    position INTEGER NOT NULL,
    text_before TEXT NOT NULL,
    text_after TEXT NOT NULL,
    cursor_before_start INTEGER NOT NULL,
    cursor_before_end INTEGER NOT NULL,
    cursor_after_start INTEGER NOT NULL,
    cursor_after_end INTEGER NOT NULL,
    created TEXT NOT NULL
);

"UndoRedo.stack" identifies whether the entry belongs to the undo stack or redo stack.

The exact schema can evolve, but it must support:

- operation kind
- position of edit
- text before
- text after
- cursor before
- cursor after
- per-note stack ordering

6.2 Timestamp Trigger

"Notes.edited" should update when "Notes.content" changes.

This can be implemented through a database trigger or repository transaction.

Preferred:

- use trigger for content writes
- explicitly update "edited" on save even if content does not change

Trashing/untrashing must only update "Notes.deleted".

7. Repository Interfaces

The application layer depends on interfaces, not Room.

7.1 Note Repository

interface NoteRepository {
    fun observeNotes(sort: NoteSort): Flow<List<NoteListItem>>
    fun observeTrash(sort: NoteSort): Flow<List<NoteListItem>>

    suspend fun createNote(now: Instant): NoteId

    suspend fun getNote(noteId: NoteId): Note?
    suspend fun observeNote(noteId: NoteId): Flow<Note?>

    suspend fun updateDraft(
        noteId: NoteId,
        content: String,
        now: Instant
    )

    suspend fun touchEdited(
        noteId: NoteId,
        now: Instant
    )

    suspend fun trashNote(
        noteId: NoteId,
        deletedAt: Instant
    )

    suspend fun untrashNote(noteId: NoteId)

    suspend fun destroyNote(noteId: NoteId)
}

7.2 Snapshot Repository

interface SnapshotRepository {
    suspend fun latestSnapshot(noteId: NoteId): Snapshot?

    fun observeSnapshotsNewestFirst(noteId: NoteId): Flow<List<SnapshotListItem>>

    suspend fun getSnapshot(snapshotId: SnapshotId): Snapshot?

    suspend fun createSnapshot(
        noteId: NoteId,
        content: String,
        created: Instant
    ): SnapshotId

    suspend fun hasSnapshots(noteId: NoteId): Boolean
}

7.3 Undo/Redo Repository

interface UndoRedoRepository {
    suspend fun pushUndo(entry: UndoEntry)
    suspend fun popUndo(noteId: NoteId): UndoEntry?
    suspend fun pushRedo(entry: UndoEntry)
    suspend fun popRedo(noteId: NoteId): UndoEntry?

    suspend fun peekUndo(noteId: NoteId): UndoEntry?
    suspend fun clearRedo(noteId: NoteId)
    suspend fun clearAll(noteId: NoteId)

    fun observeAvailability(noteId: NoteId): Flow<UndoRedoAvailability>
}

7.4 Search Repository

interface SearchRepository {
    suspend fun searchTitles(
        scope: ListScope,
        query: String
    ): List<NoteListItem>

    suspend fun searchFullText(
        query: String,
        searchIn: FullTextSearchScope,
        depth: FullTextSearchDepth
    ): List<FullTextSearchResult>
}

7.5 Export Repository

interface ContentExporter {
    suspend fun exportText(
        suggestedFileName: String,
        content: String
    )

    suspend fun shareText(content: String)
}

interface DatabaseExporter {
    suspend fun exportDatabase(
        suggestedFileName: String
    )
}

For unit tests, these interfaces are replaced with fakes.

8. Application Services

Application services implement business rules.

They must be testable without UI.

8.1 Note Service

Responsibilities:

- create note
- open note
- calculate editor mode
- edit draft
- save note
- cancel edit
- trash/untrash/destroy

class NoteService(
    private val notes: NoteRepository,
    private val snapshots: SnapshotRepository,
    private val undoRedo: UndoRedoRepository,
    private val clock: Clock,
    private val pending: PendingChangeDetector
)

8.2 Editor Service

Responsibilities:

- apply text changes
- normalize pasted text
- create undo boundaries
- persist undo/redo operations
- execute undo/redo
- track cursor/selection state

class EditorService(
    private val notes: NoteRepository,
    private val undoRedo: UndoRedoRepository,
    private val normalizer: TextNormalizer,
    private val undoEngine: UndoEngine,
    private val clock: Clock
)

8.3 History Service

Responsibilities:

- list snapshots
- open snapshot
- restore snapshot
- enforce no-pending-change precondition

class HistoryService(
    private val notes: NoteRepository,
    private val snapshots: SnapshotRepository,
    private val undoRedo: UndoRedoRepository,
    private val pending: PendingChangeDetector,
    private val clock: Clock
)

8.4 Trash Service

Responsibilities:

- trash note
- untrash note
- destroy note
- empty trash

class TrashService(
    private val notes: NoteRepository,
    private val clock: Clock
)

8.5 Search Service

Responsibilities:

- title search
- full-text search
- fuzzy matching
- ranking
- history search result selection

class SearchService(
    private val searchRepository: SearchRepository,
    private val matcher: FuzzyMatcher
)

8.6 Export Service

Responsibilities:

- build default note export filename
- export currently displayed content
- share currently displayed content
- export database

class ExportService(
    private val contentExporter: ContentExporter,
    private val databaseExporter: DatabaseExporter,
    private val titleExtractor: TitleExtractor,
    private val dateProvider: LocalDateProvider
)

9. Interface Between UI and Logic

9.1 UI Must Not Call Repositories Directly

Compose screens shall talk only to ViewModels.

ViewModels shall call application services.

Application services shall call repositories.

Composable -> ViewModel -> Application Service -> Repository Interface -> Room Implementation

9.2 UI Events

Each screen exposes explicit events.

Example for note editor:

sealed interface EditorEvent {
    data object EditClicked : EditorEvent
    data object SaveClicked : EditorEvent
    data object CancelEditClicked : EditorEvent
    data object CancelEditConfirmed : EditorEvent
    data object UndoClicked : EditorEvent
    data object RedoClicked : EditorEvent
    data object DeleteClicked : EditorEvent
    data object DeleteConfirmed : EditorEvent
    data object HistoryClicked : EditorEvent
    data object ExportClicked : EditorEvent
    data object ShareClicked : EditorEvent
    data class TextChanged(
        val newText: String,
        val cursor: EditorCursorState,
        val editSource: EditSource
    ) : EditorEvent
}

9.3 Screen State

Each ViewModel exposes immutable state.

Example editor state:

data class EditorScreenState(
    val noteId: NoteId,
    val mode: EditorMode,
    val content: String,
    val cursor: EditorCursorState,
    val canSave: Boolean,
    val canCancelEdit: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val canOpenHistory: Boolean,
    val isTrashed: Boolean,
    val pendingPrompt: EditorPrompt?
)

9.4 One-Shot Effects

Navigation, export, share, and app exit are one-shot effects.

sealed interface EditorEffect {
    data object NavigateToNoteList : EditorEffect
    data object NavigateToHistory : EditorEffect
    data class LaunchExport(val suggestedFileName: String, val content: String) : EditorEffect
    data class LaunchShare(val content: String) : EditorEffect
}

The UI observes effects and performs platform actions.

9.5 Editor Component Boundary

The editor UI component must not decide business behavior.

It reports text operations upward.

interface TextEditorAdapter {
    val content: String
    val selection: TextSelection

    fun setContent(content: String, selection: TextSelection)
    fun setReadOnly(readOnly: Boolean)
}

The editor surface emits:

data class TextEditEvent(
    val before: String,
    val after: String,
    val cursorBefore: EditorCursorState,
    val cursorAfter: EditorCursorState,
    val source: EditSource,
    val timestamp: Instant
)

The application/editor service decides how to persist the edit and undo entry.

10. Implementation Increments

The implementation should proceed in increments. Early increments must be completely UI-free.

Each increment must include required tests before moving on.

Increment 0: Project Skeleton and Test Harness

Scope

Create the Android project and package structure.

Add:

- Kotlin
- Compose
- Room
- coroutines
- ViewModel
- test dependencies
- coroutine test utilities
- in-memory fake repositories
- deterministic test clock

No UI behavior is implemented yet.

Required Tests

- test clock returns deterministic timestamps
- ISO UTC timestamp formatter persists second precision
- test coroutine dispatcher works for service tests
- fake repositories can create/read/update/delete simple records

Acceptance Criteria

- unit test suite runs locally
- project builds in debug and release
- no business logic depends on Android UI classes

Increment 1: Core Text Rules

Scope

Implement pure Kotlin text utilities:

- title extraction
- LF normalization
- paste sanitization
- export filename derivation
- timestamp display formatting
- pending-change detection

Required Tests

Title extraction:

- empty content has empty title
- first line is title
- duplicate titles are allowed
- whitespace-only title is preserved
- long title is returned unchanged by domain logic

Pending changes:

- draft equals latest snapshot means no pending changes
- draft differs means pending changes
- latest snapshot "null" always means pending changes
- empty draft with latest snapshot "null" means pending changes

Paste sanitization:

- CRLF becomes LF
- stray CR becomes LF
- LF is preserved
- TAB is preserved
- NUL becomes replacement character
- invalid controls are replaced individually
- runs of invalid controls are not collapsed

Export filename:

- empty first line produces ".txt"
- full first line is used verbatim
- filename is not sanitized
- ".txt" is appended

Timestamp display:

- same-day timestamp renders "HH:MM"
- non-same-day timestamp renders locale short date

Acceptance Criteria

- all text behavior is pure Kotlin and UI-independent
- all normalization behavior is covered by unit tests

Increment 2: Domain Models and In-Memory Notebook

Scope

Create domain models and in-memory repositories.

Implement:

- "Note"
- "Snapshot"
- "UndoEntry"
- "NoteRepository" fake
- "SnapshotRepository" fake
- "UndoRedoRepository" fake

No Room yet.

Required Tests

- creating a note immediately creates a note row
- new note has empty content
- new note has no snapshots
- new note has "deleted = null"
- empty note remains after creation
- notes can be queried by normal list
- trashed notes are excluded from normal list
- trashed notes appear in trash list
- default list sort is "edited DESC"

Acceptance Criteria

- services can be tested against fake repositories
- no Android or Room dependency is required for core tests

Increment 3: Note Lifecycle Service

Scope

Implement note lifecycle behavior:

- create note
- open note
- determine editor mode
- update draft
- save note
- cancel edit

Required Tests

Opening:

- note with no snapshots opens in edit mode
- note whose draft equals latest snapshot opens in view mode
- note whose draft differs from latest snapshot opens in edit mode
- editor mode is recalculated and not stored

Editing:

- editing updates "Notes.content"
- editing updates "Notes.edited"
- editing persists immediately

Saving:

- save on note with no snapshot creates first snapshot
- save on empty note with no snapshot creates empty snapshot
- save when draft differs creates snapshot
- save when draft equals latest snapshot creates no snapshot
- save always updates "Notes.edited"
- save returns view mode
- save clears undo/redo
- save does not prompt at service level

Cancel edit:

- cancel unavailable without snapshot
- cancel resets draft to latest snapshot
- cancel updates "Notes.edited"
- cancel is represented as an undoable edit operation
- cancel returns view mode

Acceptance Criteria

- note lifecycle is fully usable without UI
- all save/cancel/open rules are test-covered

Increment 4: Undo/Redo Engine

Scope

Implement undo/redo domain logic.

Features:

- undo entries with before/after text
- cursor before/after
- operation kind
- edit position
- undo stack
- redo stack
- clear redo on new edit
- clear all on save
- persistent repository API

Required Tests

Basic behavior:

- typing creates undo operation
- undo restores previous text
- redo restores next text
- undo restores cursor and selection
- redo restores cursor and selection
- new edit clears redo stack
- save clears undo/redo

Operation coverage:

- deletion is undoable
- paste is undoable
- cut is undoable
- replacement is undoable
- clear is undoable
- autocorrect-like replacement is undoable
- cancel edit is undoable
- snapshot restore is undoable

Persistence behavior with fake repository:

- undo/redo survives service recreation
- undo/redo survives editor leave/re-enter
- undo/redo is per note
- trash does not clear undo/redo
- untrash does not clear undo/redo
- destroy deletes undo/redo

Mode behavior:

- undoing cancel edit switches to edit mode
- redoing cancel edit switches to view mode
- undo/redo normally available only in edit mode
- undo is available in view mode if top operation is cancel edit

Acceptance Criteria

- undo/redo behavior is complete before UI work starts
- mode transitions caused by undo/redo are test-covered

Increment 5: Undo Boundary Detection

Scope

Implement edit grouping rules.

Features:

- pause-based boundaries
- approximate 500 ms threshold
- Unicode word-boundary detection
- grapheme cluster handling
- emoji grapheme handling
- paste/cut/replacement boundaries

Required Tests

Pause behavior:

- continuous typing within threshold groups into one operation
- typing after threshold creates new operation
- threshold is configurable for tests

Word boundaries:

- non-word after word creates boundary
- Unicode word characters are handled
- combining marks do not break semantic grouping incorrectly
- emoji with skin tone modifier is one grapheme
- ZWJ emoji sequences are treated as one grapheme where supported

Operation boundaries:

- paste creates boundary
- cut creates boundary
- replacement creates boundary
- clear creates boundary
- deletion groups sensibly

Acceptance Criteria

- undo grouping is deterministic and unit-tested
- grouping logic is independent from UI toolkit

Increment 6: History and Snapshot Restore

Scope

Implement history behavior:

- list snapshots newest-first
- open snapshot
- restore snapshot
- enforce no-pending-change precondition
- snapshot export/share content selection

Required Tests

History list:

- snapshots listed newest-first
- snapshot title comes from snapshot content
- empty snapshot title remains empty
- zero snapshots means history unavailable

Restore:

- restore requires no pending changes
- restore replaces draft content
- restore updates "Notes.edited"
- restore creates one undo operation
- restore enters edit mode
- restore does not create a snapshot
- restore does not delete snapshots
- restoring latest snapshot is allowed
- undo after restore returns previous draft
- redo after undo reapplies restore

Snapshot view:

- snapshot content is view-only
- snapshot export uses currently displayed snapshot content
- snapshot share uses currently displayed snapshot content

Acceptance Criteria

- history and restore behavior is fully functional without UI

Increment 7: Trash and Destruction

Scope

Implement trash behavior:

- trash note
- untrash note
- permanent delete
- empty trash
- trashed note view restrictions

Required Tests

Trash:

- trash sets "Notes.deleted"
- trash does not change "Notes.edited"
- trash removes note from normal list
- trash adds note to trash list
- trash preserves draft
- trash preserves snapshots
- trash preserves undo/redo

Untrash:

- untrash clears "Notes.deleted"
- untrash does not change "Notes.edited"
- untrash preserves all note data

Destroyed notes:

- permanent delete removes note
- permanent delete removes snapshots
- permanent delete removes undo/redo
- permanent delete only operates on trashed notes
- empty trash destroys all trashed notes
- empty trash does not affect normal notes

Trashed restrictions:

- trashed notes are viewable
- trashed notes are not editable
- trashed note history is unavailable
- trashed notes can be exported/shared

Acceptance Criteria

- deletion lifecycle is fully tested without UI

Increment 8: Title Search

Scope

Implement list search.

Features:

- title-only search
- normal list scope
- trash list scope
- case-insensitive exact matching
- fuzzy matching
- best-to-worst ranking
- exact above fuzzy

Required Tests

- note list search only searches non-trashed note titles
- trash list search only searches trashed note titles
- search is case-insensitive
- exact substring matches work
- fuzzy typo matches work
- skipped characters match fuzzily
- duplicate characters match fuzzily
- character flips match fuzzily
- acronym matching is not required
- exact matches rank above fuzzy matches
- whitespace titles are searchable by whitespace

Acceptance Criteria

- title search is implemented without UI
- ranking is deterministic enough for stable tests

Increment 9: Full-Text Search

Scope

Implement full-text search.

Features:

- search in notes
- search in trash
- search in everything
- latest-only depth
- full-history depth
- draft result selection
- latest matching snapshot result selection
- old-result marker

Required Tests

Scope:

- "notes" searches non-trashed notes
- "trash" searches trashed notes
- "everything" searches both

Depth:

- "latest" searches drafts only
- "full history" searches drafts and snapshots

Result selection:

- if draft matches, result opens draft
- if draft matches and snapshots also match, only draft result is shown
- if only snapshots match, latest matching snapshot is shown
- old snapshot-only result is marked old
- multiple matching snapshots produce only latest matching snapshot result
- exact matches rank above fuzzy matches

Acceptance Criteria

- full-text search behavior is complete before UI implementation

Increment 10: Export and Backup Core

Scope

Implement export/share selection logic and database backup logic.

No Android file picker UI yet.

Features:

- currently displayed content abstraction
- note export filename generation
- snapshot export behavior
- database export filename generation
- database export request object

Required Tests

Note export:

- draft view exports draft content
- edit mode exports current draft content
- snapshot view exports snapshot content
- export does not create snapshot
- export does not modify note
- export filename uses first line verbatim
- empty title exports as ".txt"
- output is UTF-8
- output uses LF

Share:

- share uses currently displayed content
- share does not modify note

Database export:

- suggested name is "notes-${YYYY-MM-DD}.db3"
- database export request includes full DB
- database export does not modify DB

Acceptance Criteria

- export/share logic is testable without Android intents
- Android file picker can be attached later as an effect handler

Increment 11: Room Database Implementation

Scope

Replace fake repositories with Room-backed implementations.

Features:

- entities
- DAOs
- database class
- transactions
- triggers or explicit timestamp update handling
- cascade deletion
- indices
- repository implementations

Required Tests

Room integration tests:

- notes persist across database reopen
- snapshots persist across database reopen
- undo/redo persists across database reopen
- default sort uses "edited DESC"
- content update updates "edited"
- save updates "edited" even without content change
- trash/untrash only changes "deleted"
- permanent delete cascades to history and undo/redo
- timestamp format is ISO UTC second precision
- full-text queries return expected rows
- export can copy database file

Repository compatibility:

- all service tests pass against Room repositories
- fake and Room implementations behave equivalently

Acceptance Criteria

- the core app works end-to-end against SQLite without UI

Increment 12: Recovery Core

Scope

Implement database open/recovery logic.

Features:

- successful open goes to note list
- corrupt open goes to recovery state
- recovery can export database file
- recovery can reset database
- recovery can close app
- reset deletes DB and creates fresh DB

Required Tests

- valid database opens successfully
- corrupt database produces recovery state
- reset deletes old database
- reset creates fresh usable database
- export path can be requested before reset
- close app produces exit effect
- reset requires safe prompt at presentation level

Acceptance Criteria

- app startup decision is testable
- corrupt DB behavior does not require Compose tests

Increment 13: Presentation ViewModels Without Compose

Scope

Implement ViewModels and state/effect interfaces.

No Compose UI yet.

ViewModels:

- note list
- trash list
- editor
- history
- full-text search
- recovery

Required Tests

Note list ViewModel:

- exposes sorted notes
- search event filters titles
- new note event creates note and emits navigation
- selected note enables delete/export/share
- delete event asks for prompt
- confirmed delete trashes note

Trash ViewModel:

- exposes trashed notes
- empty trash asks for safe prompt
- destroy asks for safe prompt
- untrash asks for prompt
- confirmed actions call service

Editor ViewModel:

- opens correct mode
- text change calls edit service
- save returns view mode
- save emits no prompt
- cancel asks for prompt
- confirmed cancel returns view mode
- undo/redo update content and mode
- delete asks for prompt
- confirmed delete returns note list
- export/share emit effects
- history available only in view mode

History ViewModel:

- lists snapshots newest-first
- view opens snapshot viewer
- restore asks for prompt
- confirmed restore enters edit mode

Search ViewModel:

- supports scope and depth options
- emits draft navigation for draft result
- emits snapshot navigation for old result

Recovery ViewModel:

- exposes export/reset/close actions
- reset asks for safe prompt
- confirmed reset creates fresh DB and navigates to note list

Acceptance Criteria

- all app behavior can be driven through ViewModels in tests
- Compose can be added as rendering only

Increment 14: Compose UI Shell

Scope

Attach Compose screens to existing ViewModels.

Features:

- note list
- trash list
- full-text search
- history list
- recovery screen
- dialogs
- menus/toolbars
- navigation effects

The editor text surface can initially be simple if the editor abstraction is honored.

Required Tests

Compose/UI tests:

- note list renders titles only
- empty/whitespace titles are rendered as normal rows
- note list toolbar actions match selection state
- trash toolbar actions match selection state
- prompts display correct text
- safe prompts disable OK until "I'm sure" is checked
- history list shows timestamps and titles
- full-text old results are marked old
- recovery screen exposes export/reset/close

ViewModel tests remain the primary source of behavior coverage.

Acceptance Criteria

- UI does not reimplement business rules
- UI uses state and effects only

Increment 15: Final Editor Surface

Scope

Implement or finalize the text editor component.

Features:

- read-only selectable view mode
- editable draft mode
- snapshot view mode
- text change interception
- cursor/selection reporting
- paste sanitization integration
- platform keyboard compatibility
- disabled smart quotes/punctuation
- system autocorrect/spellcheck behavior
- no visible editor title

Required Tests

Where possible as unit tests:

- editor event mapping produces expected "TextEditEvent"
- paste events are sanitized before persistence
- cursor/selection state is passed to undo service

Instrumentation tests:

- text can be selected in view mode
- text cannot be edited in view mode
- text can be edited in edit mode
- snapshot text cannot be edited
- paste sanitization works through actual UI
- save button transitions to view mode
- cancel undo behavior transitions back to edit mode

Acceptance Criteria

- editor satisfies all interaction requirements
- editor implementation remains replaceable behind the adapter boundary

Increment 16: Backup, Export, and Share Platform Integration

Scope

Attach Android platform file/share behavior.

Features:

- note export file picker
- snapshot export file picker
- database export file picker
- share sheet
- corrupt database export

Required Tests

Unit/ViewModel tests:

- correct export effects are emitted
- suggested filenames are correct
- share effects contain correct content

Instrumentation/manual verification:

- note export writes UTF-8 LF text
- snapshot export writes UTF-8 LF text
- DB export writes ".db3"
- share sheet receives displayed content
- corrupt DB export is available before reset

Acceptance Criteria

- platform integration does not alter domain behavior

Increment 17: Startup and Performance

Scope

Optimize and measure startup.

Features:

- minimal "Application.onCreate"
- lazy initialization
- first screen drawn quickly
- Baseline Profile
- Startup Profile
- Macrobenchmark startup test
- scrolling benchmark for note list

Required Tests

Macrobenchmark:

- cold startup benchmark exists
- warm startup benchmark exists
- note list scrolling benchmark exists

Regression checks:

- startup does not eagerly run full-text search
- startup does not eagerly load all snapshots
- startup does not eagerly initialize editor internals
- startup opens recovery screen when DB is corrupt

Acceptance Criteria

- startup performance is measured
- release build uses R8 and profiles
- performance-sensitive behavior is protected by benchmarks

Increment 18: MVP Hardening

Scope

Final correctness pass.

Features:

- edge-case tests
- large note smoke tests
- database export smoke tests
- recovery smoke tests
- prompt text verification
- accessibility font scaling verification
- theme-following verification

Required Tests

- empty note full lifecycle
- note with no snapshots full lifecycle
- long title ellipsis UI behavior
- whitespace-only title behavior
- hundreds of trashed notes empty-trash prompt count
- unlimited undo smoke test
- app restart preserves draft
- app restart preserves undo/redo
- app restart preserves trash state
- app restart preserves snapshots

Acceptance Criteria

- MVP behavior matches requirements document
- no known destructive operation exists without required prompt
- no Markdown/smart behavior exists

11. UI/Logic Boundary Requirements

REQ-ARCH-001: Business rules shall live outside Compose UI.

REQ-ARCH-002: Compose screens shall render immutable state and send events.

REQ-ARCH-003: ViewModels shall expose "StateFlow<ScreenState>".

REQ-ARCH-004: Navigation, export, share, and app exit shall be emitted as one-shot effects.

REQ-ARCH-005: Application services shall be testable without Android UI.

REQ-ARCH-006: Domain logic shall be pure Kotlin where possible.

REQ-ARCH-007: Repositories shall be accessed through interfaces by application services.

REQ-ARCH-008: Room shall be hidden behind repository implementations.

REQ-ARCH-009: The editor component shall be replaceable behind a stable adapter boundary.

REQ-ARCH-010: Text edit events shall include before/after content and before/after cursor state.

REQ-ARCH-011: UI shall not decide save, pending-change, restore, trash, or undo semantics.

REQ-ARCH-012: Prompt requirements shall be represented in ViewModel state, not ad-hoc inside Composables.

REQ-ARCH-013: Export/share shall be represented as effects from ViewModels.

REQ-ARCH-014: Platform file pickers shall be effect handlers, not domain services.

REQ-ARCH-015: Startup routing shall be decided by recovery/startup logic before UI navigation.

12. MVP Definition of Done

The MVP is complete when:

- the database-backed core passes all domain and repository tests
- ViewModels expose all required screens as state/effect APIs
- Compose UI renders those states without business-rule duplication
- note lifecycle behavior matches requirements
- snapshots and history behavior match requirements
- undo/redo persists and clears correctly
- trash and permanent delete behavior matches requirements
- title search and full-text search work as specified
- export/share operate on currently displayed content
- database export works
- corrupt database recovery works
- prompts are shown for all destructive operations
- no settings screen exists
- no Markdown behavior exists
- no smart behavior exists
- startup is benchmarked
- release build uses R8 and startup/profile optimization
