package tomato.notes.helpers

import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import tomato.notes.R
import tomato.notes.extensions.config
import tomato.notes.extensions.notesDB
import tomato.notes.extensions.notebooksDB
import tomato.notes.extensions.updateWidgets
import tomato.notes.extensions.widgetsDB
import tomato.notes.models.Note
import tomato.notes.models.Notebook

class RecycleBinHelper(private val context: android.content.Context) {
    fun deleteNote(note: Note, callback: (() -> Unit)? = null) {
        ensureBackgroundThread {
            val noteId = note.id
            if (noteId != null) {
                // Widgets of a trashed note stay, showing a "deleted" state, so they come back on restore
                if (context.config.useRecycleBin) {
                    context.notesDB.updateDeletedTs(noteId, System.currentTimeMillis())
                } else {
                    context.notesDB.deleteNote(note)
                    context.widgetsDB.deleteNoteWidgets(noteId)
                }
                context.updateWidgets()
            }
            callback?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }

    fun deleteNotebook(notebook: Notebook, callback: (() -> Unit)? = null) {
        ensureBackgroundThread {
            val notebookId = notebook.id ?: return@ensureBackgroundThread
            val notes = context.notesDB.getNotesInNotebook(notebookId)
            val now = System.currentTimeMillis()
            if (context.config.useRecycleBin) {
                context.notesDB.markNotesDeletedInNotebook(notebookId, now)
                context.notebooksDB.updateDeletedTs(notebookId, now)
            } else {
                notes.forEach { note ->
                    note.id?.let { context.widgetsDB.deleteNoteWidgets(it) }
                    context.notesDB.deleteNote(note)
                }
                context.widgetsDB.deleteNotebookWidgets(notebookId)
                context.notebooksDB.deleteNotebook(notebook)
            }
            context.updateWidgets()
            callback?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }

    fun restoreNote(note: Note, callback: (() -> Unit)? = null) {
        ensureBackgroundThread {
            val noteId = note.id ?: return@ensureBackgroundThread
            val notebook = context.notebooksDB.getNotebookWithId(note.notebookId)
            when {
                notebook == null -> {
                    val fallbackId = context.notebooksDB.getNotebooks().firstOrNull()?.id
                        ?: context.notebooksDB.insertOrUpdate(
                            Notebook(
                                id = null,
                                title = context.getString(R.string.app_launcher_name),
                                protectionType = PROTECTION_NONE,
                                protectionHash = ""
                            )
                        )
                    note.notebookId = fallbackId
                    note.deletedTs = 0L
                    context.notesDB.insertOrUpdate(note)
                }
                notebook.isDeleted() -> {
                    context.notebooksDB.restoreNotebook(notebook.id!!)
                    context.notesDB.restoreNote(noteId)
                }
                else -> context.notesDB.restoreNote(noteId)
            }
            context.updateWidgets()
            callback?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }

    fun restoreNotebook(notebook: Notebook, callback: (() -> Unit)? = null) {
        ensureBackgroundThread {
            val notebookId = notebook.id ?: return@ensureBackgroundThread
            val deletedTs = notebook.deletedTs
            context.notebooksDB.restoreNotebook(notebookId)
            if (deletedTs > 0L) {
                context.notesDB.restoreNotesInNotebook(notebookId, deletedTs)
            }
            context.updateWidgets()
            callback?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }

    fun permanentlyDeleteNote(note: Note, callback: (() -> Unit)? = null) {
        ensureBackgroundThread {
            context.notesDB.deleteNote(note)
            note.id?.let { context.widgetsDB.deleteNoteWidgets(it) }
            context.updateWidgets()
            callback?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }

    fun permanentlyDeleteNotebook(notebook: Notebook, callback: (() -> Unit)? = null) {
        ensureBackgroundThread {
            val notebookId = notebook.id ?: return@ensureBackgroundThread
            deleteNoteWidgets(context.notesDB.getDeletedNotes().filter { it.notebookId == notebookId })
            context.notesDB.permanentlyDeleteNotesInNotebook(notebookId)
            context.widgetsDB.deleteNotebookWidgets(notebookId)
            context.notebooksDB.deleteNotebook(notebook)
            context.updateWidgets()
            callback?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }

    fun emptyRecycleBin(callback: (() -> Unit)? = null) {
        ensureBackgroundThread {
            deleteNoteWidgets(context.notesDB.getDeletedNotes())
            context.notebooksDB.getDeletedNotebooks().forEach { notebook ->
                notebook.id?.let { context.widgetsDB.deleteNotebookWidgets(it) }
            }
            context.notesDB.permanentlyDeleteAllDeletedNotes()
            context.notebooksDB.permanentlyDeleteAllDeletedNotebooks()
            context.updateWidgets()
            callback?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }

    fun emptyOldItems(callback: (() -> Unit)? = null) {
        ensureBackgroundThread {
            val cutoff = System.currentTimeMillis() - RECYCLE_BIN_RETENTION_MS
            val oldNotes = context.notesDB.getDeletedNotes().filter { it.deletedTs < cutoff }
            val oldNotebooks = context.notebooksDB.getDeletedNotebooks().filter { it.deletedTs < cutoff }
            deleteNoteWidgets(oldNotes)
            oldNotebooks.forEach { notebook ->
                notebook.id?.let { context.widgetsDB.deleteNotebookWidgets(it) }
            }
            context.notesDB.permanentlyDeleteNotesOlderThan(cutoff)
            context.notebooksDB.permanentlyDeleteNotebooksOlderThan(cutoff)
            if (oldNotes.isNotEmpty() || oldNotebooks.isNotEmpty()) {
                context.updateWidgets()
            }
            callback?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }
        }
    }

    // Only for notes that are removed for good, trashed notes keep their widgets
    private fun deleteNoteWidgets(notes: List<Note>) {
        notes.forEach { note ->
            note.id?.let { context.widgetsDB.deleteNoteWidgets(it) }
        }
    }

    fun hasItems(callback: (Boolean) -> Unit) {
        ensureBackgroundThread {
            val hasItems = context.notesDB.getDeletedNoteCount() > 0 || context.notebooksDB.getDeletedNotebookCount() > 0
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(hasItems)
            }
        }
    }
}
