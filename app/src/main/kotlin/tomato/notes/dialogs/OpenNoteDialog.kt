package tomato.notes.dialogs

import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.views.AutoStaggeredGridLayoutManager
import tomato.notes.R
import tomato.notes.adapters.OpenNoteAdapter
import tomato.notes.databinding.DialogOpenNoteBinding
import tomato.notes.helpers.NotesHelper
import tomato.notes.models.Note

class OpenNoteDialog(val activity: BaseSimpleActivity, private val notebookId: Long, val callback: (checkedId: Long, newNote: Note?) -> Unit) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogOpenNoteBinding.inflate(activity.layoutInflater)

        val noteItemWidth = activity.resources.getDimensionPixelSize(R.dimen.grid_note_item_width)
        binding.dialogOpenNoteList.layoutManager = AutoStaggeredGridLayoutManager(noteItemWidth, StaggeredGridLayoutManager.VERTICAL)

        NotesHelper(activity).getNotesInNotebook(notebookId) {
            initDialog(it, binding)
        }
    }

    private fun initDialog(notes: List<Note>, binding: DialogOpenNoteBinding) {
        binding.dialogOpenNoteList.adapter = OpenNoteAdapter(activity, notes, binding.dialogOpenNoteList) {
            it as Note
            callback(it.id!!, null)
            dialog?.dismiss()
        }

        binding.newNoteFab.setOnClickListener {
            NewNoteDialog(
                activity = activity,
                setChecklistAsDefault = false,
                notebookId = notebookId,
                callback = { note ->
                    callback(0, note)
                    dialog?.dismiss()
                }
            )
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.open_note) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
