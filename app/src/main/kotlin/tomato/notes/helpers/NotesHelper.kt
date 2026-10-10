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
import java.io.File
import java.io.OutputStream

class NotesHelper(val context: Context) {
    fun getNotes(callback: (notes: List<Note>) -> Unit) {
        ensureBackgroundThread {
            val notes = context.notesDB.getNotes().toMutableList()
            val notesToDelete = mutableListOf<Note>()
            notes.forEach {
                if (it.path.isNotEmpty()) {
                    if (!it.path.startsWith("content://") && !File(it.path).exists()) {
                        context.notesDB.deleteNote(it)
                        notesToDelete.add(it)
                    }
                }
            }

            notes.removeAll(notesToDelete)
            cleanupPlaceholderNotesSync()
            val remaining = context.notesDB.getNotes()

            Handler(Looper.getMainLooper()).post {
                callback(remaining)
            }
        }
    }

    fun getNotesInNotebook(notebookId: Long, callback: (notes: List<Note>) -> Unit) {
        ensureBackgroundThread {
            cleanupPlaceholderNotesSync()
            val notes = context.notesDB.getNotesInNotebook(notebookId)
            Handler(Looper.getMainLooper()).post {
                callback(notes)
            }
        }
    }

    fun cleanupPlaceholderNotes() {
        ensureBackgroundThread {
            cleanupPlaceholderNotesSync()
        }
    }

    fun cleanupPlaceholderNotesSync() {
        val generalNote = context.resources.getString(R.string.general_note)
        context.notesDB.getNotes()
            .filter { note ->
                note.title == generalNote &&
                    note.value.isEmpty() &&
                    note.path.isEmpty() &&
                    note.type == NoteType.TYPE_TEXT &&
                    !note.isLocked()
            }
            .forEach { context.notesDB.deleteNote(it) }
    }

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

    fun importNotes(
        activity: BaseSimpleActivity,
        notes: List<Note>,
        notebooks: List<Notebook> = emptyList(),
        checklistSorting: List<Int?> = emptyList(),
        callback: (ImportResult) -> Unit
    ) {
        ensureBackgroundThread {
            // Update or create notebooks from backup to restore pinned status and sort order
            if (notebooks.isNotEmpty()) {
                val existingNotebooks = activity.notebooksDB.getNotebooks().associateBy { it.title }
                notebooks.forEach { importedNotebook ->
                    if (existingNotebooks.containsKey(importedNotebook.title)) {
                        val existing = existingNotebooks[importedNotebook.title]!!
                        // Update properties if they differ
                        if (existing.pinned != importedNotebook.pinned || existing.sortOrder != importedNotebook.sortOrder) {
                            existing.pinned = importedNotebook.pinned
                            existing.sortOrder = importedNotebook.sortOrder
                            activity.notebooksDB.insertOrUpdate(existing)
                        }
                    } else {
                        // Create new notebook with imported properties
                        val newNotebook = importedNotebook.copy(id = null)
                        activity.notebooksDB.insertOrUpdate(newNotebook)
                    }
                }
            }

            val currentNotebooks = activity.notebooksDB.getNotebooks().associateBy { it.title }
            val notebooksMap = currentNotebooks.toMutableMap()

            var imported = 0
            var skipped = 0

            notes.forEachIndexed { index, note ->
                val sorting = checklistSorting.getOrNull(index)
                // we need to reset the ID to avoid overwriting existing notes with the same ID
                note.id = null
                if (note.sortOrder <= 0) {
                    note.sortOrder = (activity.notesDB.getMaxSortOrder() ?: 0) + 1
                }

                // Determine target notebook
                var targetNotebookId: Long
                val notebookTitle = note.notebookTitle
                if (notebookTitle != null) {
                    if (notebooksMap.containsKey(notebookTitle)) {
                        targetNotebookId = notebooksMap[notebookTitle]!!.id!!
                    } else {
                        val newNotebook = Notebook(null, notebookTitle, PROTECTION_NONE, "")
                        targetNotebookId = activity.notebooksDB.insertOrUpdate(newNotebook)
                        notebooksMap[notebookTitle] = newNotebook.copy(id = targetNotebookId)
                    }
                } else {
                    targetNotebookId = activity.notebooksDB.getNotebooks().firstOrNull()?.id
                        ?: activity.notebooksDB.insertOrUpdate(
                            Notebook(null, activity.getString(R.string.app_launcher_name), PROTECTION_NONE, "")
                        )
                }

                note.notebookId = targetNotebookId
                note.deletedTs = 0L

                val existingNoteId = activity.notesDB.getNoteIdWithTitleInNotebook(note.title, targetNotebookId)
                if (existingNoteId != null) {
                    val existingNote = activity.notesDB.getNoteWithId(existingNoteId)
                    if (existingNote != null && existingNote.value == note.value) {
                        saveImportedChecklistSorting(activity, existingNoteId, note, sorting)
                        skipped++
                    } else {
                        // Duplicate title but different content -> Rename and import
                        var newTitle = note.title
                        var i = 1
                        while (activity.notesDB.getNoteIdWithTitleInNotebook(newTitle, targetNotebookId) != null) {
                            newTitle = "${note.title} ($i)"
                            i++
                        }
                        note.title = newTitle
                        val newId = activity.notesDB.insertOrUpdate(note)
                        saveImportedChecklistSorting(activity, newId, note, sorting)
                        imported++
                    }
                } else {
                    val newId = activity.notesDB.insertOrUpdate(note)
                    saveImportedChecklistSorting(activity, newId, note, sorting)
                    imported++
                }
            }

            val result = when {
                imported == 0 && skipped > 0 -> ImportResult.IMPORT_NOTHING_NEW
                imported > 0 -> ImportResult.IMPORT_OK
                else -> ImportResult.IMPORT_FAIL
            }
            callback(result)
        }
    }

    fun exportNotes(notesToBackup: List<Note>, outputStream: OutputStream, password: String? = null): ExportResult {
        return try {
            val notebooks = context.notebooksDB.getNotebooks()
            val notebooksById = notebooks.associateBy { it.id }
            val config = context.config
            val checklistSorting = ArrayList<Int?>(notesToBackup.size)
            val exportNotes = notesToBackup.map { note ->
                val sorting = if (note.type == NoteType.TYPE_CHECKLIST) config.getChecklistSorting(note.id) else null
                checklistSorting.add(sorting)
                note.copy(protectionType = PROTECTION_NONE, protectionHash = "").also { copy ->
                    copy.notebookTitle = notebooksById[note.notebookId]?.title
                    if (sorting != null) {
                        copy.value = checklistValueInDisplayOrder(note.value, sorting, config.moveDoneChecklistItems)
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
            val backupData = BackupData(exportNotes, exportNotebooks, settings, checklistSorting)

            val jsonString = Json.encodeToString(backupData)
            val payload = if (!password.isNullOrEmpty()) {
                NotesEncryptionHelper.encrypt(jsonString, password)
            } else {
                jsonString
            }
            outputStream.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            }
            ExportResult.EXPORT_OK
        } catch (e: Exception) {
            ExportResult.EXPORT_FAIL
        }
    }

    private fun saveImportedChecklistSorting(activity: BaseSimpleActivity, noteId: Long, note: Note, sorting: Int?) {
        if (note.type == NoteType.TYPE_CHECKLIST && sorting != null) {
            activity.config.saveChecklistSorting(noteId, sorting)
        }
    }

    private fun checklistValueInDisplayOrder(raw: String, sorting: Int, moveDoneToBottom: Boolean): String {
        return try {
            val checklistItemType = object : TypeToken<List<ChecklistItem>>() {}.type
            val items = Gson().fromJson<List<ChecklistItem>>(raw, checklistItemType) ?: return raw
            val ordered = ChecklistItem.displayOrder(items, sorting, moveDoneToBottom)
            if (items.map { it.id } == ordered.map { it.id }) {
                raw
            } else {
                Gson().toJson(ordered)
            }
        } catch (e: Exception) {
            raw
        }
    }

    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL, IMPORT_NOTHING_NEW
    }
}
