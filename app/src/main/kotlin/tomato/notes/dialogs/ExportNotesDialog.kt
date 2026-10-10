package tomato.notes.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import tomato.notes.R
import tomato.notes.activities.SimpleActivity
import tomato.notes.databinding.DialogExportNotesBinding

class ExportNotesDialog(val activity: SimpleActivity, callback: (filename: String, encrypt: Boolean) -> Unit) {

    init {
        val binding = DialogExportNotesBinding.inflate(activity.layoutInflater).apply {
            val textColor = activity.getProperTextColor()
            exportNotesFilename.setText(
                buildString {
                    append(root.context.getString(com.simplemobiletools.commons.R.string.notes))
                    append("_")
                    append(root.context.getCurrentFormattedDateTime())
                }
            )
            exportPlain.setTextColor(textColor)
            exportEncrypted.setTextColor(textColor)
        }

        activity.getAlertDialogBuilder().setPositiveButton(com.simplemobiletools.commons.R.string.ok, null).setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null).apply {
            activity.setupDialogStuff(binding.root, this, R.string.export_notes) { alertDialog ->
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

                    val filename = binding.exportNotesFilename.value
                    when {
                        filename.isEmpty() -> activity.toast(com.simplemobiletools.commons.R.string.empty_name)
                        filename.isAValidFilename() -> {
                            callback(filename, binding.exportEncrypted.isChecked)
                            alertDialog.dismiss()
                        }

                        else -> activity.toast(com.simplemobiletools.commons.R.string.invalid_name)
                    }
                }
            }
        }
    }
}

