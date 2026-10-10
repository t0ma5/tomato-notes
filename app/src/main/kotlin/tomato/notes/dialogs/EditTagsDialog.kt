package tomato.notes.dialogs

import android.content.DialogInterface.BUTTON_POSITIVE
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showKeyboard
import tomato.notes.R
import tomato.notes.activities.SimpleActivity
import tomato.notes.databinding.DialogEditTagsBinding
import tomato.notes.helpers.NotesHelper
import tomato.notes.models.Note

class EditTagsDialog(val activity: SimpleActivity, val note: Note, val callback: (note: Note) -> Unit) {
    init {
        val binding = DialogEditTagsBinding.inflate(activity.layoutInflater)
        binding.noteTagsValue.setText(note.tagList().joinToString(", "))

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.edit_tags) { alertDialog ->
                    alertDialog.showKeyboard(binding.noteTagsValue)
                    alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                        val values = binding.noteTagsValue.text.toString()
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        note.setTagList(values)
                        NotesHelper(activity).insertOrUpdateNote(note) {
                            alertDialog.dismiss()
                            callback(note)
                        }
                    }
                }
            }
    }
}
