package tomato.notes.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import tomato.notes.R
import tomato.notes.databinding.DialogSetNotebookPasswordBinding
import tomato.notes.databinding.DialogUnlockNotebookPasswordBinding

class BackupPasswordDialog(
    activity: Activity,
    confirm: Boolean,
    titleRes: Int = R.string.export_encrypted,
    callback: (password: String) -> Unit,
) {
    init {
        if (confirm) {
            val binding = DialogSetNotebookPasswordBinding.inflate(activity.layoutInflater)
            activity.getAlertDialogBuilder()
                .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
                .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
                .apply {
                    activity.setupDialogStuff(binding.root, this, titleRes) { alertDialog ->
                        alertDialog.showKeyboard(binding.password)
                        alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                            val password = binding.password.text?.toString().orEmpty()
                            val confirmed = binding.confirmPassword.text?.toString().orEmpty()
                            when {
                                password.isBlank() -> activity.toast(R.string.empty_password)
                                password != confirmed -> activity.toast(R.string.passwords_do_not_match)
                                else -> {
                                    callback(password)
                                    alertDialog.dismiss()
                                }
                            }
                        }
                    }
                }
        } else {
            val binding = DialogUnlockNotebookPasswordBinding.inflate(activity.layoutInflater)
            activity.getAlertDialogBuilder()
                .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
                .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
                .apply {
                    activity.setupDialogStuff(binding.root, this, titleRes) { alertDialog ->
                        alertDialog.showKeyboard(binding.password)
                        alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                            val password = binding.password.text?.toString().orEmpty()
                            if (password.isBlank()) {
                                activity.toast(R.string.empty_password)
                            } else {
                                callback(password)
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
        }
    }
}
