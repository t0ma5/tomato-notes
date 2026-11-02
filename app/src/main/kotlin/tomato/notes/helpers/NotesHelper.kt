package tomato.notes.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.helpers.ExportResult
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import tomato.notes.R
import tomato.notes.databases.NotesDatabase
import tomato.notes.extensions.config
import tomato.notes.extensions.notesDB
import tomato.notes.extensions.notebooksDB
import tomato.notes.models.ChecklistItem
import tomato.notes.models.Note
import tomato.notes.models.Notebook
import tomato.notes.models.NoteType
import tomato.notes.models.BackupData
import tomato.notes.models.BackupSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.util.concurrent.Callable

class NotesHelper(val context: Context) {
    // Notes with a missing file path are kept: the file may only be unreachable for now (SD card, storage access)
    // and the note still has its stored value
    fun getNotes(callback: (notes: List<Note>) -> Unit) {
        ensureBackgroundThread {
            val notes = getNotesSync()
            Handler(Looper.getMainLooper()).post {
                callback(notes)
            }
        }
    }

    // Blocking variant for background threads
    fun getNotesSync(): List<Note> = context.notesDB.getNotes()

    fun getNotesInNotebook(notebookId: Long, callback: (notes: List<Note>) -> Unit) {
        ensureBackgroundThread {
            val notes = context.notesDB.getNotesInNotebook(notebookId)
            Handler(Looper.getMainLooper()).post {
                callback(notes)
            }
        }
    }

    // No-ops: the empty "General note" could be a note the user made, so it is never deleted automatically.
    // First install no longer creates a placeholder note.
    @Deprecated("Placeholder notes are no longer deleted automatically")
    fun cleanupPlaceholderNotes() {}

    @Deprecated("Placeholder notes are no longer deleted automatically")
    fun cleanupPlaceholderNotesSync() {}

    fun ensureAtLeastOneNotebook(callback: (notebookId: Long) -> Unit) {
        ensureBackgroundThread {
            val existing = context.notebooksDB.getNotebooks()
            val notebookId = if (existing.isNotEmpty()) {
                existing.first().id!!
            } else {
                val title = context.resources.getString(R.string.app_launcher_name)
                context.notebooksDB.insertOrUpdate(
                    Notebook(id = null, title = title, protectionType = PROTECTION_NONE, protectionHash = "")
                )
            }
            Handler(Looper.getMainLooper()).post {
                callback(notebookId)
            }
        }
    }

    fun getNoteWithId(id: Long, callback: (note: Note?) -> Unit) {
        ensureBackgroundThread {
            val note = context.notesDB.getNoteWithId(id)?.takeUnless { it.isDeleted() }
            Handler(Looper.getMainLooper()).post {
                callback(note)
            }
        }
    }

    fun getNoteIdWithPath(path: String, callback: (id: Long?) -> Unit) {
        ensureBackgroundThread {
            val id = context.notesDB.getNoteIdWithPath(path)
            Handler(Looper.getMainLooper()).post {
                callback(id)
            }
        }
    }

    fun insertOrUpdateNote(note: Note, callback: ((newNoteId: Long) -> Unit)? = null) {
        ensureBackgroundThread {
            if (note.id == null && note.sortOrder <= 0) {
                note.sortOrder = (context.notesDB.getMaxSortOrder() ?: 0) + 1
            }
            val noteId = context.notesDB.insertOrUpdate(note)
            Handler(Looper.getMainLooper()).post {
                callback?.invoke(noteId)
            }
        }
    }

    fun insertOrUpdateNotes(notes: List<Note>, callback: ((newNoteIds: List<Long>) -> Unit)? = null) {
        ensureBackgroundThread {
            val noteIds = context.notesDB.insertOrUpdate(notes)
            Handler(Looper.getMainLooper()).post {
                callback?.invoke(noteIds)
            }
        }
    }

    // The callback runs on a background thread
    fun importNotes(
        activity: BaseSimpleActivity,
        notes: List<Note>,
        notebooks: List<Notebook> = emptyList(),
        checklistSorting: List<Int?> = emptyList(),
        checklistItemOrder: List<List<Int>?> = emptyList(),
        callback: (ImportResult) -> Unit
    ) {
        ensureBackgroundThread {
            val result = try {
                val sortingsToSave = mutableListOf<Pair<Long, Int>>()
                // All or nothing: a failure halfway rolls back every notebook and note written so far
                val counts = NotesDatabase.getInstance(activity).runInTransaction(Callable {
                    importNotesSync(activity, notes, notebooks, checklistSorting, checklistItemOrder, sortingsToSave)
                })
                // Preferences are not part of the transaction, so write them only after it committed
                sortingsToSave.forEach { (noteId, sorting) ->
                    activity.config.saveChecklistSorting(noteId, sorting)
                }

                val imported = counts.first
                val skipped = counts.second
                when {
                    imported == 0 && skipped > 0 -> ImportResult.IMPORT_NOTHING_NEW
                    imported > 0 -> ImportResult.IMPORT_OK
                    else -> ImportResult.IMPORT_FAIL
                }
            } catch (e: Exception) {
                ImportResult.IMPORT_FAIL
            }
            callback(result)
        }
    }

    // Returns imported and skipped counts. Must run inside a transaction.
    private fun importNotesSync(
        activity: BaseSimpleActivity,
        notes: List<Note>,
        notebooks: List<Notebook>,
        checklistSorting: List<Int?>,
        checklistItemOrder: List<List<Int>?>,
        sortingsToSave: MutableList<Pair<Long, Int>>
    ): Pair<Int, Int> {
        val notebooksMap = activity.notebooksDB.getNotebooks().associateBy { it.title }.toMutableMap()

        // Update or create notebooks from backup to restore pinned status and sort order
        notebooks.forEach { importedNotebook ->
            val existing = notebooksMap[importedNotebook.title]
            if (existing != null) {
                // Update properties if they differ
                if (existing.pinned != importedNotebook.pinned || existing.sortOrder != importedNotebook.sortOrder) {
                    existing.pinned = importedNotebook.pinned
                    existing.sortOrder = importedNotebook.sortOrder
                    activity.notebooksDB.insertOrUpdate(existing)
                }
            } else {
                // Create new notebook with imported properties
                val newNotebook = importedNotebook.copy(id = null)
                val newId = activity.notebooksDB.insertOrUpdate(newNotebook)
                notebooksMap[importedNotebook.title] = newNotebook.copy(id = newId)
            }
        }

        var defaultNotebookId: Long? = null
        var maxSortOrder = activity.notesDB.getMaxSortOrder() ?: 0
        var imported = 0
        var skipped = 0

        notes.forEachIndexed { index, note ->
            val sorting = checklistSorting.getOrNull(index)
            if (note.type == NoteType.TYPE_CHECKLIST) {
                note.value = checklistValueInStoredOrder(note.value, checklistItemOrder.getOrNull(index))
            }
            // we need to reset the ID to avoid overwriting existing notes with the same ID
            note.id = null
            if (note.sortOrder <= 0) {
                note.sortOrder = maxSortOrder + 1
            }

            // Determine target notebook
            val notebookTitle = note.notebookTitle
            val targetNotebookId = if (notebookTitle != null) {
                notebooksMap[notebookTitle]?.id ?: run {
                    val newNotebook = Notebook(null, notebookTitle, PROTECTION_NONE, "")
                    val newId = activity.notebooksDB.insertOrUpdate(newNotebook)
                    notebooksMap[notebookTitle] = newNotebook.copy(id = newId)
                    newId
                }
            } else {
                defaultNotebookId ?: (
                    activity.notebooksDB.getNotebooks().firstOrNull()?.id
                        ?: activity.notebooksDB.insertOrUpdate(
                            Notebook(null, activity.getString(R.string.app_launcher_name), PROTECTION_NONE, "")
                        )
                    ).also { defaultNotebookId = it }
            }

            note.notebookId = targetNotebookId
            note.deletedTs = 0L

            val existingNoteId = activity.notesDB.getNoteIdWithTitleInNotebook(note.title, targetNotebookId)
            val existingNote = existingNoteId?.let { activity.notesDB.getNoteWithId(it) }
            if (existingNoteId != null && existingNote != null && existingNote.value == note.value) {
                rememberChecklistSorting(sortingsToSave, existingNoteId, note, sorting)
                skipped++
            } else {
                if (existingNoteId != null) {
                    // Duplicate title but different content -> Rename and import
                    var newTitle = note.title
                    var i = 1
                    while (activity.notesDB.getNoteIdWithTitleInNotebook(newTitle, targetNotebookId) != null) {
                        newTitle = "${note.title} ($i)"
                        i++
                    }
                    note.title = newTitle
                }
                val newId = activity.notesDB.insertOrUpdate(note)
                maxSortOrder = maxOf(maxSortOrder, note.sortOrder)
                rememberChecklistSorting(sortingsToSave, newId, note, sorting)
                imported++
            }
        }

        return Pair(imported, skipped)
    }

    // Blocking: call it off the main thread (an encrypted export runs PBKDF2). Always closes outputStream.
    fun exportNotes(notesToBackup: List<Note>, outputStream: OutputStream, password: String? = null): ExportResult {
        return try {
            outputStream.use { stream ->
                val notebooks = context.notebooksDB.getNotebooks()
                val notebooksById = notebooks.associateBy { it.id }
                val config = context.config
                val checklistSorting = ArrayList<Int?>(notesToBackup.size)
                val checklistItemOrder = ArrayList<List<Int>?>(notesToBackup.size)
                val exportNotes = notesToBackup.map { note ->
                    val sorting = if (note.type == NoteType.TYPE_CHECKLIST) config.getChecklistSorting(note.id) else null
                    checklistSorting.add(sorting)
                    // The file lists checklist items in on-screen order; the stored order goes alongside so import can restore it
                    val displayOrder = sorting?.let { checklistInDisplayOrder(note.value, it, config.moveDoneChecklistItems) }
                    checklistItemOrder.add(displayOrder?.second)
                    note.copy(protectionType = PROTECTION_NONE, protectionHash = "").also { copy ->
                        copy.notebookTitle = notebooksById[note.notebookId]?.title
                        if (displayOrder != null) {
                            copy.value = displayOrder.first
                        }
                    }
                }
                val exportNotebooks = notebooks.map { it.copy(protectionType = PROTECTION_NONE, protectionHash = "") }
                val settings = BackupSettings(
                    autosaveNotes = config.autosaveNotes,
                    displaySuccess = config.displaySuccess,
                    clickableLinks = config.clickableLinks,
                    monospacedFont = config.monospacedFont,
                    showKeyboard = config.showKeyboard,
                    showNotePicker = config.showNotePicker,
                    showWordCount = config.showWordCount,
                    gravity = config.gravity,
                    placeCursorToEnd = config.placeCursorToEnd,
                    enableLineWrap = config.enableLineWrap,
                    useIncognitoMode = config.useIncognitoMode,
                    lastCreatedNoteType = config.lastCreatedNoteType,
                    moveDoneChecklistItems = config.moveDoneChecklistItems,
                    fontSizePercentage = config.fontSizePercentage,
                    addNewChecklistItemsTop = config.addNewChecklistItemsTop,
                    notebookColumns = config.notebookColumns
                )
                val itemOrder = if (checklistItemOrder.any { it != null }) checklistItemOrder else emptyList()
                val backupData = BackupData(exportNotes, exportNotebooks, settings, checklistSorting, itemOrder)

                val jsonString = Json.encodeToString(backupData)
                val payload = if (!password.isNullOrEmpty()) {
                    NotesEncryptionHelper.encrypt(jsonString, password)
                } else {
                    jsonString
                }
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }
            ExportResult.EXPORT_OK
        } catch (e: Exception) {
            ExportResult.EXPORT_FAIL
        }
    }

    private fun rememberChecklistSorting(sortingsToSave: MutableList<Pair<Long, Int>>, noteId: Long, note: Note, sorting: Int?) {
        if (note.type == NoteType.TYPE_CHECKLIST && sorting != null) {
            sortingsToSave.add(Pair(noteId, sorting))
        }
    }

    // Returns the value in on-screen order plus the stored item id order, or null when the order is already the same
    private fun checklistInDisplayOrder(raw: String, sorting: Int, moveDoneToBottom: Boolean): Pair<String, List<Int>>? {
        return try {
            val items = Gson().fromJson<List<ChecklistItem>>(raw, checklistItemType()) ?: return null
            val ordered = ChecklistItem.displayOrder(items, sorting, moveDoneToBottom)
            if (items.map { it.id } == ordered.map { it.id }) {
                null
            } else {
                Pair(Gson().toJson(ordered), items.map { it.id })
            }
        } catch (e: Exception) {
            null
        }
    }

    // Puts exported checklist items back into their stored order, so a custom order with done items moved down survives
    private fun checklistValueInStoredOrder(value: String, storedOrder: List<Int>?): String {
        if (storedOrder.isNullOrEmpty()) {
            return value
        }
        return try {
            val items = Gson().fromJson<List<ChecklistItem>>(value, checklistItemType()) ?: return value
            val positions = HashMap<Int, Int>()
            storedOrder.forEachIndexed { position, id ->
                if (id !in positions) {
                    positions[id] = position
                }
            }
            val restored = items.sortedBy { positions[it.id] ?: Int.MAX_VALUE }
            if (restored.map { it.id } == items.map { it.id }) value else Gson().toJson(restored)
        } catch (e: Exception) {
            value
        }
    }

    private fun checklistItemType() = object : TypeToken<List<ChecklistItem>>() {}.type

    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL, IMPORT_NOTHING_NEW
    }
}
