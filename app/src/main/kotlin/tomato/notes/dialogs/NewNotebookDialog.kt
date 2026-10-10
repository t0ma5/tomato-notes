package tomato.notes.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import tomato.notes.R
import tomato.notes.databinding.DialogNewNotebookBinding
import tomato.notes.extensions.notebooksDB
import tomato.notes.models.Notebook

class NewNotebookDialog(val activity: Activity, title: String? = null, callback: (notebook: Notebook) -> Unit) {
    init {
        val binding = DialogNewNotebookBinding.inflate(activity.layoutInflater)
        val view = binding.root
        binding.notebookTitle.setText(title)

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.new_notebook) { alertDialog ->
                    alertDialog.showKeyboard(binding.notebookTitle)
                    alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.notebookTitle.text?.toString()?.trim().orEmpty()
                        ensureBackgroundThread {
                            when {
                                newTitle.isEmpty() -> activity.toast(R.string.no_title)
                                activity.notebooksDB.getNotebookIdWithTitle(newTitle) != null -> activity.toast(R.string.title_taken)
                                else -> {
                                    val notebook = Notebook(
                                        id = null,
                                        title = newTitle,
                                        protectionType = PROTECTION_NONE,
                                        protectionHash = ""
                                    )
                                    activity.runOnUiThread {
                                        callback(notebook)
                                        alertDialog.dismiss()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}
