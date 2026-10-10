package tomato.notes.extensions

import android.app.Activity
import com.simplemobiletools.commons.extensions.performSecurityCheck
import tomato.notes.dialogs.UnlockNotebookPasswordDialog
import tomato.notes.helpers.NotebookPasswordHasher
import tomato.notes.models.Notebook

fun Activity.unlockNotebookIfNeeded(notebook: Notebook?, callback: () -> Unit) {
    if (notebook == null || !notebook.isLocked()) {
        callback()
        return
    }
    if (NotebookPasswordHasher.isLegacyHash(notebook.protectionHash)) {
        UnlockNotebookPasswordDialog(this, notebook.protectionHash, callback)
    } else {
        performSecurityCheck(
            protectionType = notebook.protectionType,
            requiredHash = notebook.protectionHash,
            successCallback = { _, _ -> callback() }
        )
    }
}
