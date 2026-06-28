Notepad MVP Requirements Specification

1. Brief Summary

Notepad is a simple Android plaintext note-taking application.

The app is intentionally minimal. A note is plain text. The first line of the text is the note title. Notes are stored in a single SQLite database. The current editable text of a note is called the draft. Explicit saves create immutable snapshots in the note history.

The app prioritizes:

- fast plaintext editing
- persistent drafts
- explicit saved versions
- undo/redo that survives app restarts
- recoverable deletion through trash
- simple export/share behavior
- no Markdown
- no smart behavior
- no unnecessary settings in the MVP

2. Core Concepts

2.1 Note

A note is a database row containing the current draft text.

A note:

- has a unique database ID
- always has draft text
- may be empty
- may have zero snapshots
- may be trashed
- may be permanently destroyed

The first line of the draft text is the note title.

2.2 Draft

The draft is the current content of the note.

The draft is stored in "Notes.content".

The draft is what the user sees when opening a normal note.

The draft is always persisted immediately when edited.

2.3 Snapshot

A snapshot is an immutable saved version of a note.

Snapshots are created only by pressing save, and only if the current draft differs from the latest snapshot.

Snapshots are stored in history.

Snapshots:

- have no user-visible name
- store full note text
- have an immutable save timestamp
- use the first line of their own content as their title
- are view-only

2.4 Pending Changes

A note has pending changes when:

Notes.content != latest snapshot content

If a note has no snapshots, the latest snapshot is "null".

Therefore, a note with no snapshots always has pending changes, including if its draft is empty.

2.5 Title

The title is derived from the first line of the relevant content.

For notes, this is the first line of "Notes.content".

For snapshots, this is the first line of the snapshot content.

Titles:

- are not required to be unique
- may be empty
- may consist only of whitespace
- are displayed as-is
- occupy one visual line in lists
- are ellipsized if too long

Note lists show only titles.

3. Architecture Overview

3.1 MVP Storage Model

The MVP uses a single SQLite database per app installation.

The database is the complete notebook.

The database contains all app data required to restore the notebook state, including:

- notes
- current drafts
- history snapshots
- trash state
- undo/redo stacks

The database can be exported as a ".db3" file.

Import is not part of the MVP.

3.2 Conceptual Database Tables

The conceptual database model is:

Notes(
    id ROWID,
    content TEXT NOT NULL,
    created DATETIME NOT NULL,
    edited DATETIME NOT NULL,
    deleted DATETIME NULL
)

History(
    note FK(Notes.id),
    content TEXT NOT NULL,
    created DATETIME NOT NULL
)

UndoRedo(
    note FK(Notes.id),
    direction,
    operation_kind,
    position,
    text_before,
    text_after,
    cursor_before,
    cursor_after,
    created DATETIME NOT NULL
)

The exact schema may differ during implementation, but the persisted information must support these semantics.

3.3 Timestamp Rules

All persisted timestamps are stored as ISO UTC strings with second precision.

"Notes.created" means when the note row was created.

"Notes.edited" means the newer of:

- the last content-affecting action
- the last time the save button was clicked

"Notes.deleted" means:

- "NULL": note is not trashed
- non-null timestamp: note is trashed, and the timestamp is when it was trashed

Snapshot timestamps are immutable save timestamps.

If multiple human edits happen within the same second, identical timestamps are acceptable.

"Notes.edited" should update when "Notes.content" is written, ideally via database trigger.

Trashing and untrashing only touch "Notes.deleted".

3.4 Main Functional Components

The app consists of these major functional areas:

- note list
- trash list
- full-text search
- note editor/viewer
- history list
- snapshot viewer
- recovery screen

The snapshot viewer is the same screen/component as the note editor/viewer, but with different actions enabled and disabled.

4. Navigation Map

Application launch:

<app launch> -> [ note list, recovery screen ]

Main navigation:

note list -> [ trash list, full text search, note editor ]

note editor -> [ note list, history list ]

history list -> [ note editor, snapshot viewer ]

snapshot viewer -> [ history list, note editor ]

recovery screen -> [ note list, <app exit> ]

full text search -> [ note editor, note list, snapshot viewer ]

Only one main view is visible at a time.

The note list and note editor are never visible simultaneously.

5. View Modes

5.1 Note Draft View Mode

Draft view mode displays the current draft read-only.

The text remains selectable and copyable.

Draft view mode is used when the draft equals the latest snapshot.

5.2 Note Edit Mode

Edit mode displays the current draft as editable plaintext.

Edit mode is used when:

- a note has pending changes
- a new note is created
- the user explicitly enters edit mode
- a snapshot is restored
- cancel editing is undone

5.3 Snapshot View Mode

Snapshot view mode displays snapshot content read-only.

Snapshot view mode uses the same editor/viewer component.

Snapshot content cannot be edited.

Snapshot content can be shared or exported.

Snapshot restore is available from this mode.

6. Note Lifecycle Requirements

6.1 Creating Notes

Creating a new note immediately creates a new row in "Notes".

A new note has:

- empty draft text
- no initial snapshot
- "created" set to the creation timestamp
- "edited" set to the creation timestamp
- "deleted = NULL"

A newly created note opens in edit mode.

If the user creates a new note, types nothing, and leaves, the empty note remains.

Empty notes are valid notes.

6.2 Opening Notes

When opening a note, the app compares the current draft to the latest snapshot.

If:

draft == latest snapshot

the note opens in view mode.

If:

draft != latest snapshot

the note opens in edit mode.

If there is no latest snapshot, the note opens in edit mode.

The previously used edit/view mode is not persisted.

Android back leaves the note editor and returns to the view that opened it.

Back navigation does not prompt and does not modify the draft.

6.3 Editing Notes

Editing always changes the draft.

Every edit immediately writes the new draft to the database.

There is no autosave debounce for persistence.

Debouncing is only used for detecting undo boundaries during human typing.

6.4 Saving Notes

Pressing save always updates "Notes.edited".

If the draft differs from the latest snapshot, pressing save creates a new snapshot.

If the draft equals the latest snapshot, pressing save creates no snapshot.

Pressing save always switches to view mode.

Pressing save clears undo/redo history for that note.

Saving never prompts.

For a note with no snapshots, pressing save always creates the first snapshot, because the latest snapshot is "null".

This includes empty notes.

6.5 Cancel Editing

Cancel editing resets the draft to the latest snapshot content.

Cancel editing is only available if a latest snapshot exists.

Cancel editing is unavailable for notes with no snapshots.

Cancel editing requires confirmation.

Cancel editing is an edit operation.

Cancel editing is undoable.

Cancel editing switches to view mode.

Undoing a cancel-edit operation switches from view mode back to edit mode.

Redoing a cancel-edit operation switches back to view mode.

6.6 Restoring Snapshots / Rollback

Snapshot restore is only available when there are no pending draft changes.

History can only be opened from view mode, which implies no pending changes.

Restoring a snapshot requires confirmation.

Restoring a snapshot replaces the current draft with the snapshot content.

Restoring a snapshot:

- does not delete any snapshots
- does not create a new snapshot
- updates "Notes.edited"
- enters edit mode
- is stored as one undoable text replacement operation
- clears redo like a regular new edit operation

Restoring the latest snapshot is allowed.

After restore, undo restores the previous draft.

After undoing the restore, redo restores the snapshot content again.

7. History Requirements

The history list shows snapshots only.

The history list is newest-first.

The history list shows:

- snapshot save timestamp
- snapshot title

Snapshot titles use the same title logic as notes.

If the snapshot title is empty, the title area is empty.

Long snapshot titles are ellipsized.

Snapshot timestamps are stored in UTC but displayed in local time.

For same-day timestamps, display time as:

HH:MM

For other dates, use the locale’s short date format.

If a note has zero snapshots, the history view cannot be opened.

Snapshots can be opened for viewing.

Snapshots are view-only.

Snapshots can be exported and shared.

Snapshot export/share must use the same code path as draft export/share, operating on the currently displayed content.

MVP history view does not show diffs.

Diff display is a stretch goal.

8. Undo/Redo Requirements

Undo/redo is required in edit mode.

Undo/redo history must persist:

- when leaving and re-entering the editor
- across app restarts
- until the next save
- until the note is permanently destroyed

Undo/redo is stored in the database.

Undo/redo stacks are per note.

Undo/redo is tied to the editor and note, not to how the editor was opened.

Undo/redo is unlimited in the MVP.

Save clears undo/redo history for the note.

Permanent destruction deletes undo/redo history for the note.

Trash and untrash do not change undo/redo history.

8.1 Undoable Operations

Undo/redo must work for all text editing actions, including:

- typing
- deletion
- paste
- cut
- autocorrect
- replacement
- overwrite
- clear
- cancel edit
- snapshot restore

The source of the editing event does not matter.

8.2 Undo Boundaries

Undo boundaries are introduced:

- after a notable pause in human typing
- at word boundaries
- for paste/cut/replacement-like operations

A notable pause is approximately 500 ms and should be adjustable during implementation tuning.

Word boundaries use Unicode word-character semantics.

Undo grouping should operate on grapheme clusters rather than raw code units.

Emoji graphemes are treated as single words.

For example, an emoji with a skin tone modifier is treated as one unit.

8.3 Cursor and Selection

Undo/redo must restore:

- text content
- cursor position
- selection range

Cursor and selection positions should be based on semantic text positions, not naive byte positions.

8.4 Undo/Redo Availability

Undo/redo normally exists only in edit mode.

Exception:

- if the top undo operation is a cancel-edit action, undo is available even though the note is currently in view mode

Undoing that cancel-edit action returns the editor to edit mode.

Redoing it returns the editor to view mode.

9. Trash and Deletion Requirements

9.1 Trashing Notes

Trashing a note sets "Notes.deleted" to the current timestamp.

Trashing requires confirmation.

Trashed notes disappear from the regular note list.

Trashed notes appear in the trash list.

Trashing does not delete:

- draft content
- snapshots
- undo/redo history

Trashing only changes "Notes.deleted".

9.2 Trash List

The trash list is functionally similar to the regular note list but shows trashed notes and different actions.

Trash list rows show only note titles.

Trash list sorting and title search behave like the regular note list.

9.3 Trashed Notes

Trashed notes can be viewed.

Trashed notes cannot be edited.

History is not visible for trashed notes.

Trashed notes can be exported and shared.

9.4 Untrash / Restore from Trash

Untrashing requires confirmation.

Untrashing only changes the trash state.

Untrashing sets "Notes.deleted" back to "NULL".

Untrashing does not change:

- content
- snapshots
- undo/redo history
- "Notes.edited"

9.5 Permanent Delete

Permanent delete is available only from the trash list.

Permanent delete requires a safe prompt.

Permanent delete cannot be undone.

Permanent delete erases the note data from the database completely, including:

- note row
- snapshots
- undo/redo history

Permanent delete prompt text:

Do you want to permanently delete this note?

9.6 Empty Trash

Empty trash is available from the trash list.

Empty trash requires a safe prompt.

Empty trash permanently destroys all notes currently in trash.

The prompt text is:

Do you really want to delete ${count} notes forever?

The prompt does not list all notes.

The trash list already shows the affected notes.

10. Prompt Requirements

Operations that destroy user work require prompts.

Non-destructive operations do not prompt.

10.1 Basic Prompt

A basic prompt is a popup with:

- message
- OK
- Cancel

10.2 Safe Prompt

A safe prompt is a popup with:

- message
- checkbox labeled "I'm sure"
- OK
- Cancel

The OK button is disabled until the checkbox is checked.

10.3 Prompt Matrix

The following operations require prompts:

Operation| Prompt Type
Cancel edit| Basic
Restore snapshot| Basic
Trash note| Basic
Untrash note| Basic
Permanent delete| Safe
Empty trash| Safe
Corrupt database reset| Safe

The following operations do not prompt:

Operation| Prompt Type
Open note| None
Enter edit mode| None
Back navigation| None
Save| None
Export note| Filename/location dialog only
Share note| None
Database export| Filename/location dialog only

Trash-note prompt text:

Do you want to delete this note?

Corrupt database reset prompt text:

If you reset the database, all your notes are gone without recovery. Are you really sure you want to delete the file?

11. Note List Requirements

The note list shows non-trashed notes.

The note list only shows titles.

The note list does not show timestamps, previews, status markers, or snapshot information.

Default sort order is:

Notes.edited DESC

Supported sort modes:

- creation time ascending
- creation time descending
- last edit time ascending
- last edit time descending
- title ascending
- title descending

Title sorting is case-insensitive.

Empty titles are not treated specially.

Whitespace-only titles are not treated specially.

Titles occupy one line and are ellipsized if too long.

The note list reflects the current draft content stored in "Notes.content".

Because note list and editor are never visible simultaneously, live updating while editing is irrelevant.

When the list becomes visible, it shows the current persisted database state.

12. Search Requirements

12.1 List Search

List search is available in note list and trash list.

List search searches titles only.

List search searches only the currently displayed list.

In normal note list, it searches non-trashed notes.

In trash list, it searches trashed notes.

List search is case-insensitive.

List search supports:

- exact substring matching
- fuzzy matching

Whitespace titles are searchable by whitespace.

Search results are sorted best-to-worst match.

Exact matches rank above fuzzy matches.

12.2 Fuzzy Search Semantics

Fuzzy search should tolerate:

- typos
- character flips
- skipped characters
- duplicated characters

Fuzzy search does not need smart acronym matching.

12.3 Full-Text Search

Full-text search is a separate mode/view.

Full-text search is accessible from the list-view menu.

Full-text search is case-insensitive.

Full-text search supports exact and fuzzy matching.

Full-text search options:

search in: [ notes, trash, everything ]
depth:     [ latest, full history ]

"latest" searches current drafts only.

"full history" searches drafts and snapshots.

Full-text search searches drafts by default unless the user explicitly chooses full history.

12.4 Full-Text Search Result Behavior

If the draft matches, the result opens the note draft.

If the draft matches and old snapshots also match, only the draft result is shown.

If the draft does not match but one or more snapshots match, the latest matching snapshot is shown.

If a result only matches an old snapshot, the result is visibly marked as old.

Opening an old-result match opens the matching snapshot in view-only snapshot mode.

Draft and snapshot matches are ranked together in full-history search.

Because full-history search is opt-in, mixed ranking is acceptable for MVP.

Exact matches rank above fuzzy matches.

The exact ranking formula is not critical for MVP.

13. Editor / Viewer Requirements

13.1 Draft View Mode

Draft view mode is read-only.

Text remains selectable and copyable.

The toolbar shows:

- edit
- delete

The menu shows:

- export
- share
- history

History is only available when no pending changes exist.

13.2 Edit Mode

Edit mode allows changing draft text.

The toolbar shows:

- save
- cancel edit
- undo
- redo

The menu shows:

- export
- share

Save is available even if the draft is unchanged.

If save is pressed while unchanged, no snapshot is created, but the editor still returns to view mode and "Notes.edited" is updated.

Cancel edit is available only if a latest snapshot exists.

13.3 Snapshot View Mode

Snapshot view mode is read-only.

The toolbar shows:

- restore

Snapshot export/share is available and behaves like draft export/share.

Restoring a snapshot goes to edit mode.

13.4 Editor Title

There is no separate editor title visible on screen.

The note title is already part of the note content.

14. Toolbar Placement Requirements

14.1 Note List

No selection:

- sort
- list search
- new note

With selection:

- delete
- export
- share

New note opens note editor in edit mode.

Delete from normal note list means trash note.

14.2 Trash List

No selection:

- sort
- list search
- empty trash

With selection:

- destroy
- untrash

Destroy means permanent delete.

14.3 Note View, Draft Content

Toolbar:

- edit
- delete

Delete trashes the note and returns to note list.

14.4 Note Edit

Toolbar:

- save
- cancel edit
- undo
- redo

Save returns to note view.

Cancel edit returns to note view.

14.5 Note View, Snapshot Content

Toolbar:

- restore

Restore returns to note edit mode.

14.6 History List

Available actions:

- view
- restore

View opens snapshot view.

Restore opens note edit mode.

15. Menu Placement Requirements

15.1 List View Menu

The list-view menu contains:

- full-text search
- backup

Full-text search opens the full-text search view.

Backup exports the database.

15.2 Note View Menu

The note-view menu contains:

- export
- share
- history

History opens the history list.

15.3 Note Edit Menu

The note-edit menu contains:

- export
- share

16. Export and Share Requirements

16.1 Note Export/Share

Export and share operate on the currently displayed content.

For draft view/edit mode, this is the current draft.

For snapshot view mode, this is the snapshot content.

Export/share does not create a snapshot.

Export/share does not modify the note.

Export/share does not prompt, except for the system filename/location dialog during export.

Export/share is available from:

- note list with one selected note
- note view
- note edit
- snapshot view
- trash note view

Multi-select is not supported.

Multi-note export/share is out of scope.

16.2 Export Filename

The default exported note filename is:

${firstLine}.txt

The first line is used verbatim.

If the first line is empty, the default filename is:

.txt

Example:

First line:  C:\Windows  
Filename:    C:\Windows  .txt

The app does not sanitize the filename.

The user/system file picker is responsible for filename correction.

16.3 Export Encoding

Exported note text uses:

- UTF-8 encoding
- LF line endings

16.4 Database Export

The whole database can be exported as a ".db3" file.

Default filename:

notes-${YYYY-MM-DD}.db3

Database export is available from:

- main note-list menu
- corrupt database recovery screen

Database export includes everything in the notebook database, including:

- notes
- drafts
- snapshots
- trash state
- undo/redo history

Database export uses the system filename/location dialog as the implicit prompt.

Database import is not part of the MVP.

17. Text Handling Requirements

The app is a verbatim plaintext editor.

What the user entered is truth.

The app must not parse Markdown.

The app must not apply smart formatting.

The app must not infer structure or meaning from note content except for deriving the first-line title.

17.1 Encoding and Line Endings

Internal text is UTF-8.

Line endings are normalized to LF.

17.2 Paste Sanitization

On paste:

- CRLF is normalized to LF
- stray CR is replaced with LF
- TAB is retained
- LF is retained
- CR is normalized as above
- control characters other than CR, LF, and TAB are replaced with the Unicode replacement character
- NUL is replaced with the Unicode replacement character

Each invalid character is replaced individually.

Runs of invalid characters are not collapsed.

This preserves approximate length information.

17.3 Size Limits

Note size is unlimited in the MVP.

There is no warning on paste.

Performance issues may be addressed later if they occur.

17.4 Keyboard Behavior

Autocorrect and spellcheck follow system settings and keyboard behavior.

Smart quotes and smart punctuation are disabled.

The app itself performs no automatic formatting.

18. Visual / Theme Requirements

The MVP has no settings screen.

The app hardcodes:

- Android system color theme
- monospaced font
- standard Android font size

System color theme means following Android’s current light/dark/system theme behavior.

The app should use Berkeley Mono if available.

Otherwise, it should use any available monospace font.

Standard Android font size should respect Android accessibility font scaling.

19. Recovery Requirements

On app launch, if the database is usable, the app opens the note list.

If the database is corrupt or unusable, the app opens the recovery screen.

The recovery screen offers:

- export database file
- reset database
- close app

Exporting the corrupt database file lets the user keep a copy before destructive action.

Reset requires a safe prompt.

Reset means:

- delete the existing database file
- create a fresh database
- continue to the note list

Closing the app exits without modifying the database.

20. Explicit Exclusions

The following are explicitly excluded from the MVP:

- plain ".txt" file-backed note storage
- folder-backed note storage
- external file watching
- importing ".txt" files
- database import
- sync
- one-way backup sync
- bidirectional sync
- Markdown rendering
- Markdown parsing
- smart behavior
- smart note naming
- smart cleanup
- automatic deletion of empty notes
- AI features
- settings screen
- custom themes
- custom font selection
- custom font size selection
- encryption
- password protection
- schema migration
- multi-select
- multi-note export/share
- editing trashed notes
- viewing history of trashed notes
- snapshot diffs
- undo/redo size limits
- warnings for large notes or large paste operations

Neither Markdown rendering nor smart behavior will ever be part of this app.

21. Normative Requirement List

Storage

REQ-STO-001: The app shall use one SQLite database per app installation.

REQ-STO-002: The database shall contain all notebook data.

REQ-STO-003: The database shall contain notes, drafts, snapshots, trash state, and undo/redo history.

REQ-STO-004: The app shall support exporting the whole database as a ".db3" file.

REQ-STO-005: Database import shall not be part of the MVP.

REQ-STO-006: All timestamps shall be stored as ISO UTC strings with second precision.

Notes

REQ-NOT-001: A note shall be represented as a durable database row.

REQ-NOT-002: Creating a note shall immediately create a row.

REQ-NOT-003: A new note shall have empty draft text and no snapshots.

REQ-NOT-004: Empty notes shall be valid notes.

REQ-NOT-005: A note shall always have draft content stored in "Notes.content".

REQ-NOT-006: The title of a note shall be derived from the first line of its draft content.

REQ-NOT-007: Note titles shall not be required to be unique.

REQ-NOT-008: Notes shall be sorted by "Notes.edited DESC" by default.

Drafts and Saving

REQ-DRF-001: The draft shall be the current editable content of the note.

REQ-DRF-002: Draft edits shall be written to the database immediately.

REQ-DRF-003: Pressing save shall update "Notes.edited".

REQ-DRF-004: Pressing save shall create a snapshot only if the draft differs from the latest snapshot.

REQ-DRF-005: Pressing save shall create the first snapshot for a note with no snapshots.

REQ-DRF-006: Pressing save shall return the editor to view mode.

REQ-DRF-007: Pressing save shall clear undo/redo history for that note.

REQ-DRF-008: Pressing save shall not prompt.

Snapshots and History

REQ-HIS-001: Snapshots shall be immutable saved versions.

REQ-HIS-002: Snapshots shall store full note text.

REQ-HIS-003: Snapshot timestamps shall be immutable.

REQ-HIS-004: Snapshot titles shall be derived from the first line of snapshot content.

REQ-HIS-005: History shall be shown newest-first.

REQ-HIS-006: History shall only be available from view mode.

REQ-HIS-007: History shall not be available for notes with zero snapshots.

REQ-HIS-008: Snapshots shall be view-only.

REQ-HIS-009: Snapshots shall be exportable and shareable.

REQ-HIS-010: Snapshot restore shall require confirmation.

REQ-HIS-011: Snapshot restore shall replace the draft with snapshot content.

REQ-HIS-012: Snapshot restore shall enter edit mode.

REQ-HIS-013: Snapshot restore shall be undoable as one text replacement operation.

Editor

REQ-EDT-001: Notes shall open in view mode if draft equals latest snapshot.

REQ-EDT-002: Notes shall open in edit mode if draft differs from latest snapshot.

REQ-EDT-003: Notes with no snapshots shall open in edit mode.

REQ-EDT-004: View mode shall keep text selectable and copyable.

REQ-EDT-005: Edit mode shall allow plaintext editing.

REQ-EDT-006: The editor shall not show a separate title.

REQ-EDT-007: Android back shall leave the editor without prompting.

REQ-EDT-008: Editor mode shall be recalculated when opening a note and shall not be persisted as independent state.

Cancel Edit

REQ-CAN-001: Cancel edit shall be available only when a latest snapshot exists.

REQ-CAN-002: Cancel edit shall reset the draft to the latest snapshot.

REQ-CAN-003: Cancel edit shall require confirmation.

REQ-CAN-004: Cancel edit shall be undoable.

REQ-CAN-005: Cancel edit shall switch to view mode.

REQ-CAN-006: Undoing cancel edit shall switch back to edit mode.

REQ-CAN-007: Redoing cancel edit shall switch back to view mode.

Undo/Redo

REQ-UND-001: Undo/redo shall persist in the database.

REQ-UND-002: Undo/redo shall be per note.

REQ-UND-003: Undo/redo shall survive app restart.

REQ-UND-004: Undo/redo shall survive leaving and reopening the editor.

REQ-UND-005: Undo/redo shall be cleared on save.

REQ-UND-006: Undo/redo shall be deleted when the note is permanently destroyed.

REQ-UND-007: Undo/redo shall be unlimited in the MVP.

REQ-UND-008: Undo/redo shall support all text-editing operations.

REQ-UND-009: Undo/redo shall restore cursor and selection.

REQ-UND-010: Undo grouping shall use pause and Unicode word-boundary heuristics.

REQ-UND-011: Undo grouping shall operate on grapheme clusters.

Trash

REQ-TRS-001: Trashing a note shall set "Notes.deleted".

REQ-TRS-002: Trashing shall require confirmation.

REQ-TRS-003: Trashed notes shall disappear from the normal note list.

REQ-TRS-004: Trashed notes shall appear in the trash list.

REQ-TRS-005: Trashed notes shall be viewable.

REQ-TRS-006: Trashed notes shall not be editable.

REQ-TRS-007: Trashed note history shall not be visible.

REQ-TRS-008: Trashed notes shall be exportable and shareable.

REQ-TRS-009: Untrashing shall require confirmation.

REQ-TRS-010: Untrashing shall only clear "Notes.deleted".

REQ-TRS-011: Permanent delete shall only be available from trash.

REQ-TRS-012: Permanent delete shall require a safe prompt.

REQ-TRS-013: Permanent delete shall erase note data completely.

REQ-TRS-014: Empty trash shall require a safe prompt.

Search

REQ-SRC-001: List search shall search titles only.

REQ-SRC-002: List search shall search only the currently displayed list.

REQ-SRC-003: Full-text search shall be a separate view.

REQ-SRC-004: Search shall be case-insensitive.

REQ-SRC-005: Search shall support exact substring matches.

REQ-SRC-006: Search shall support fuzzy matching.

REQ-SRC-007: Exact matches shall rank above fuzzy matches.

REQ-SRC-008: Full-text search shall support searching notes, trash, or everything.

REQ-SRC-009: Full-text search shall support latest-only and full-history depth.

REQ-SRC-010: Full-history search shall show the draft if the draft matches.

REQ-SRC-011: If only old snapshots match, full-history search shall show the latest matching snapshot.

REQ-SRC-012: Old snapshot results shall be marked as old.

Export and Share

REQ-EXP-001: Export/share shall operate on currently displayed content.

REQ-EXP-002: Export/share shall not create snapshots.

REQ-EXP-003: Export/share shall not modify note content.

REQ-EXP-004: Exported note text shall use UTF-8.

REQ-EXP-005: Exported note text shall use LF line endings.

REQ-EXP-006: Default note export filename shall be "${firstLine}.txt".

REQ-EXP-007: The first line shall be used verbatim for export filename.

REQ-EXP-008: The app shall not sanitize exported note filenames.

REQ-EXP-009: Multi-note export/share shall be out of scope.

REQ-EXP-010: Database export shall use "notes-${YYYY-MM-DD}.db3".

Text

REQ-TXT-001: The app shall be a plaintext editor.

REQ-TXT-002: The app shall not parse Markdown.

REQ-TXT-003: The app shall not render Markdown.

REQ-TXT-004: The app shall not apply smart formatting.

REQ-TXT-005: Internal text shall be UTF-8.

REQ-TXT-006: Line endings shall be normalized to LF.

REQ-TXT-007: Paste shall normalize CRLF to LF.

REQ-TXT-008: Paste shall replace invalid control characters individually with the Unicode replacement character.

REQ-TXT-009: TAB shall be preserved.

REQ-TXT-010: Note size shall be unlimited in the MVP.

REQ-TXT-011: The app shall not warn on large paste.

REQ-TXT-012: Autocorrect and spellcheck shall follow system settings.

REQ-TXT-013: Smart quotes and smart punctuation shall be disabled.

UI and Appearance

REQ-UI-001: The MVP shall have no settings screen.

REQ-UI-002: The app shall follow Android system color theme.

REQ-UI-003: The app shall use a monospace font.

REQ-UI-004: Berkeley Mono shall be used if available.

REQ-UI-005: Any available monospace font may be used as fallback.

REQ-UI-006: Font size shall follow standard Android font size and accessibility scaling.

Recovery

REQ-REC-001: If the database opens successfully, app launch shall go to note list.

REQ-REC-002: If the database is corrupt, app launch shall go to recovery screen.

REQ-REC-003: Recovery screen shall allow database export.

REQ-REC-004: Recovery screen shall allow database reset.

REQ-REC-005: Recovery screen shall allow app exit.

REQ-REC-006: Database reset shall require a safe prompt.

REQ-REC-007: Database reset shall delete the database file and create a fresh database.
