package tomato.notes.fragments

import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import tomato.notes.activities.MainActivity
import tomato.notes.extensions.config
import tomato.notes.extensions.getPercentageFontSize
import tomato.notes.helpers.NotesHelper
import tomato.notes.models.Note

abstract class NoteFragment : Fragment() {
    protected var note: Note? = null
    var shouldShowLockedContent = false

    protected fun setupLockedViews(binding: CommonNoteBinding, note: Note) {
        binding.apply {
            noteLockedLayout.beVisibleIf(note.isLocked() && !shouldShowLockedContent)
            noteLockedImage.applyColorFilter(requireContext().getProperTextColor())

            noteLockedLabel.setTextColor(requireContext().getProperTextColor())
            noteLockedLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, binding.root.context.getPercentageFontSize())

            noteLockedShow.underlineText()
            noteLockedShow.setTextColor(requireContext().getProperPrimaryColor())
            noteLockedShow.setTextSize(TypedValue.COMPLEX_UNIT_PX, binding.root.context.getPercentageFontSize())
            noteLockedShow.setOnClickListener {
                handleUnlocking()
            }
        }
    }

    // Call on the main thread: the DB write runs in the background, file exports may show permission or SAF dialogs.
    // onSaved runs on the main thread once the DB write is done, or right after the file export was started.
    protected fun saveNoteValue(note: Note, content: String?, onSaved: (() -> Unit)? = null) {
        val activity = activity ?: return
        if (note.path.isEmpty()) {
            NotesHelper(activity.applicationContext).insertOrUpdateNote(note) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    (activity as? MainActivity)?.noteSavedSuccessfully(note.title)
                }
                onSaved?.invoke()
            }
        } else {
            if (content != null && !activity.isFinishing && !activity.isDestroyed) {
                val displaySuccess = activity.config.displaySuccess
                (activity as? MainActivity)?.tryExportNoteValueToFile(note.path, note.title, content, displaySuccess)
            }
            onSaved?.invoke()
        }
    }

    fun handleUnlocking(callback: (() -> Unit)? = null) {
        if (callback != null && (note!!.protectionType == PROTECTION_NONE || shouldShowLockedContent)) {
            callback()
            return
        }

        activity?.performSecurityCheck(
            protectionType = note!!.protectionType,
            requiredHash = note!!.protectionHash,
            successCallback = { _, _ ->
                shouldShowLockedContent = true
                checkLockState()
                (activity as? MainActivity)?.updateSecureFlag()
                callback?.invoke()
            }
        )
    }

    // Leaving the screen hides a locked note again, so coming back asks for authentication anew
    override fun onStop() {
        super.onStop()
        if (shouldShowLockedContent) {
            shouldShowLockedContent = false
            if (view != null) {
                checkLockState()
            }
        }
    }

    fun updateNoteValue(value: String) {
        note?.value = value
    }

    fun updateNotePath(path: String) {
        note?.path = path
    }

    open fun undo() {}

    open fun redo() {}

    open fun isUndoAvailable() = false

    open fun isRedoAvailable() = false

    open fun updateReadOnlyState(isReadOnly: Boolean) {}

    protected fun notifyHistoryChanged() {
        (activity as? MainActivity)?.currentNoteTextChanged(note?.value ?: "", isUndoAvailable(), isRedoAvailable())
    }

    abstract fun checkLockState()

    interface CommonNoteBinding {
        val root: View
        val noteLockedLayout: View
        val noteLockedImage: ImageView
        val noteLockedLabel: TextView
        val noteLockedShow: TextView
    }
}
