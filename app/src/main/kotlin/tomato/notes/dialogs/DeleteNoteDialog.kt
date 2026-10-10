package tomato.notes.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import tomato.notes.R
import tomato.notes.activities.SimpleActivity
import tomato.notes.databinding.DialogDeleteNoteBinding
import tomato.notes.models.Note

class DeleteNoteDialog(val activity: SimpleActivity, val note: Note, val callback: (deleteFile: Boolean) -> Unit) {
    var dialog: AlertDialog? = null

    init {
        val message = String.format(activity.getString(R.string.delete_note_prompt_message), note.title)
        val binding = DialogDeleteNoteBinding.inflate(activity.layoutInflater).apply{
            if (note.path.isNotEmpty()) {
                deleteNoteCheckbox.text = String.format(activity.getString(R.string.delete_file_itself), note.path)
                deleteNoteCheckboxHolder.beVisible()
                deleteNoteCheckboxHolder.setOnClickListener {
                    deleteNoteCheckbox.toggle()
                }
            }
            deleteNoteDescription.text = message
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.delete) { dialog, which -> dialogConfirmed(binding.deleteNoteCheckbox.isChecked) }
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }

    private fun dialogConfirmed(deleteFile: Boolean) {
        callback(deleteFile && note.path.isNotEmpty())
        dialog?.dismiss()
    }
}
