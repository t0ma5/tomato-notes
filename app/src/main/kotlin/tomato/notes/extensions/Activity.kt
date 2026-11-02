package tomato.notes.extensions

import android.app.Activity
import androidx.biometric.BiometricPrompt
import androidx.biometric.auth.AuthPromptCallback
import androidx.biometric.auth.AuthPromptHost
import androidx.biometric.auth.Class2BiometricAuthPrompt
import androidx.fragment.app.FragmentActivity
import com.simplemobiletools.commons.extensions.performSecurityCheck
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.PROTECTION_FINGERPRINT
import com.simplemobiletools.commons.helpers.isRPlus
import tomato.notes.dialogs.UnlockNotebookPasswordDialog
import tomato.notes.helpers.NotebookPasswordHasher
import tomato.notes.helpers.NotebookUnlockSession
import tomato.notes.models.Notebook

// Skips the prompt when the notebook was already unlocked in this session, unless force is set (e.g. removing the lock).
// A successful unlock is remembered in NotebookUnlockSession. onCancel runs when the prompt is closed without unlocking.
fun Activity.unlockNotebookIfNeeded(
    notebook: Notebook?,
    force: Boolean = false,
    onCancel: (() -> Unit)? = null,
    callback: () -> Unit
) {
    if (notebook == null || !notebook.isLocked() || (!force && NotebookUnlockSession.isUnlocked(notebook.id))) {
        callback()
        return
    }

    val onUnlocked = {
        NotebookUnlockSession.markUnlocked(notebook.id)
        callback()
    }

    when {
        NotebookPasswordHasher.isPasswordHash(notebook.protectionHash) -> {
            UnlockNotebookPasswordDialog(this, notebook.protectionHash, onCancel, onUnlocked)
        }

        notebook.protectionType == PROTECTION_FINGERPRINT && isRPlus() && this is FragmentActivity -> {
            showNotebookBiometricPrompt(onUnlocked, onCancel)
        }

        else -> {
            performSecurityCheck(
                protectionType = notebook.protectionType,
                requiredHash = notebook.protectionHash,
                successCallback = { _, _ -> onUnlocked() },
                failureCallback = onCancel
            )
        }
    }
}

// Like commons showBiometricPrompt, but a misread finger only shows a toast; the prompt stays open and
// onCancel runs once, when the prompt is actually closed
private fun FragmentActivity.showNotebookBiometricPrompt(onUnlocked: () -> Unit, onCancel: (() -> Unit)?) {
    Class2BiometricAuthPrompt.Builder(
        getText(com.simplemobiletools.commons.R.string.authenticate),
        getText(com.simplemobiletools.commons.R.string.cancel)
    )
        .build()
        .startAuthentication(
            AuthPromptHost(this),
            object : AuthPromptCallback() {
                override fun onAuthenticationSucceeded(activity: FragmentActivity?, result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(activity: FragmentActivity?, errorCode: Int, errString: CharSequence) {
                    val isCanceledByUser = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    if (!isCanceledByUser) {
                        toast(errString.toString())
                    }
                    onCancel?.invoke()
                }

                override fun onAuthenticationFailed(activity: FragmentActivity?) {
                    toast(com.simplemobiletools.commons.R.string.authentication_failed)
                }
            }
        )
}
