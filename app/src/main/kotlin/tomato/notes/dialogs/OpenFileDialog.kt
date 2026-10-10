package tomato.notes.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.humanizePath
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import tomato.notes.R
import tomato.notes.activities.SimpleActivity
import tomato.notes.databinding.DialogOpenFileBinding
import tomato.notes.models.Note
import tomato.notes.models.NoteType
import java.io.File

class OpenFileDialog(val activity: SimpleActivity, val path: String, val callback: (note: Note) -> Unit) : AlertDialog.Builder(activity) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogOpenFileBinding.inflate(activity.layoutInflater).apply {
            openFileFilename.setText(activity.humanizePath(path))
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.open_file) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val updateFileOnEdit = binding.openFileType.checkedRadioButtonId == binding.openFileUpdateFile.id
                        val storePath = if (updateFileOnEdit) path else ""
                        val storeContent = if (updateFileOnEdit) "" else File(path).readText()

                        if (updateFileOnEdit) {
                            activity.handleSAFDialog(path) {
                                saveNote(storeContent, storePath)
                            }
                        } else {
                            saveNote(storeContent, storePath)
                        }
                    }
                }
            }
    }

    private fun saveNote(storeContent: String, storePath: String) {
        val filename = path.getFilenameFromPath()
        val note = Note(
            id = null,
            title = filename,
            value = storeContent,
            type = NoteType.TYPE_TEXT,
            path = storePath,
            protectionType = PROTECTION_NONE,
            protectionHash = ""
        )
        callback(note)
        dialog?.dismiss()
    }
}
