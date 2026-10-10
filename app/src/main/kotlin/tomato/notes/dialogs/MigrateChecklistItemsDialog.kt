package tomato.notes.dialogs

import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import tomato.notes.R
import tomato.notes.adapters.MigrateNoteAdapter
import tomato.notes.databinding.DialogMigrateNoteBinding
import tomato.notes.helpers.NotesHelper
import tomato.notes.helpers.NotebooksHelper
import tomato.notes.models.Note
import tomato.notes.models.Notebook
import tomato.notes.models.NoteType

class MigrateChecklistItemsDialog(
    val activity: BaseSimpleActivity,
    private val currentNoteId: Long,
    private val requireChecklist: Boolean = true,
    val callback: (targetNoteId: Long) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var allNotes = mutableListOf<Note>()
    private var allNotebooks = mutableListOf<Notebook>()

    init {
        NotebooksHelper(activity).getNotebooks { notebooks ->
            allNotebooks = notebooks.toMutableList()
            NotesHelper(activity).getNotes { notes ->
                allNotes = if (requireChecklist) {
                    notes.filter { it.id != currentNoteId && it.type == NoteType.TYPE_CHECKLIST }.toMutableList()
                } else {
                    notes.filter { it.id != currentNoteId }.toMutableList()
                }
                initDialog()
            }
        }
    }

    private fun initDialog() {
        val binding = DialogMigrateNoteBinding.inflate(activity.layoutInflater)

        if (allNotes.isEmpty()) {
            binding.migrateNoteList.beGone()
        }

        binding.migrateNoteList.layoutManager = LinearLayoutManager(activity)
        binding.migrateNoteList.adapter = MigrateNoteAdapter(
            activity = activity,
            notes = allNotes,
            notebooks = allNotebooks,
            recyclerView = binding.migrateNoteList,
            itemClick = { note ->
                callback((note as Note).id!!)
                dialog?.dismiss()
            }
        )

        // Setup the "Create new note" button
        binding.createNewNoteButton.beVisible()
        binding.createNewNoteButton.setOnClickListener {
            dialog?.dismiss()
            showCreateNewNoteDialog()
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.select_target_note) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun showCreateNewNoteDialog() {
        // Let user pick a notebook first, then create a note in it
        val notebookNames = allNotebooks.map { it.title }.toTypedArray()
        val notebookIds = allNotebooks.map { it.id }

        if (notebookNames.isEmpty()) {
            activity.toast(R.string.no_other_notebooks)
            return
        }

        val adapter = ArrayAdapter(activity, android.R.layout.select_dialog_singlechoice, notebookNames)
        AlertDialog.Builder(activity)
            .setTitle(R.string.select_target_notebook)
            .setAdapter(adapter) { _, which ->
                val selectedNotebookId = notebookIds[which]!!
                // Now show the new note dialog for this notebook
                NewNoteDialog(
                    activity = activity,
                    title = null,
                    setChecklistAsDefault = requireChecklist,
                    notebookId = selectedNotebookId,
                    callback = { newNote ->
                        NotesHelper(activity).insertOrUpdateNote(newNote) { newNoteId ->
                            callback(newNoteId)
                        }
                    }
                )
            }
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .show()
    }
}
