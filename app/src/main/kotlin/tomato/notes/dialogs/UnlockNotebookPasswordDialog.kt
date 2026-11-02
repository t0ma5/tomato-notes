package tomato.notes.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import tomato.notes.R
import tomato.notes.databinding.DialogUnlockNotebookPasswordBinding
import tomato.notes.extensions.notebooksDB
import tomato.notes.helpers.NotebookPasswordHasher

class UnlockNotebookPasswordDialog(
    val activity: Activity,
    private val storedHash: String,
    onCancel: (() -> Unit)? = null,
    callback: () -> Unit
) {
    private var isUnlocked = false

    init {
        val binding = DialogUnlockNotebookPasswordBinding.inflate(activity.layoutInflater)
        val view = binding.root

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.unlock_notebook) { alertDialog ->
                    alertDialog.showKeyboard(binding.password)
                    alertDialog.setOnDismissListener {
                        if (!isUnlocked) {
                            onCancel?.invoke()
                        }
                    }
                    val positiveButton = alertDialog.getButton(BUTTON_POSITIVE)
                    positiveButton.setOnClickListener {
                        val password = binding.password.text?.toString().orEmpty()
                        positiveButton.isEnabled = false
                        // PBKDF2 is slow, keep it off the main thread
                        ensureBackgroundThread {
                            val isValid = NotebookPasswordHasher.verify(password, storedHash)
                            if (isValid && NotebookPasswordHasher.needsRehash(storedHash)) {
                                activity.notebooksDB.replaceProtectionHash(storedHash, NotebookPasswordHasher.createHash(password))
                            }
                            activity.runOnUiThread {
                                if (activity.isFinishing || activity.isDestroyed || !alertDialog.isShowing) {
                                    return@runOnUiThread
                                }
                                positiveButton.isEnabled = true
                                if (isValid) {
                                    isUnlocked = true
                                    callback()
                                    alertDialog.dismiss()
                                } else {
                                    activity.toast(R.string.wrong_password)
                                }
                            }
                        }
                    }
                }
            }
    }
}
