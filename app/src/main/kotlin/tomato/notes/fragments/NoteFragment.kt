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

    protected fun saveNoteValue(note: Note, content: String?) {
        if (note.path.isEmpty()) {
            NotesHelper(requireActivity()).insertOrUpdateNote(note) {
                (activity as? MainActivity)?.noteSavedSuccessfully(note.title)
            }
        } else {
            if (content != null) {
                val displaySuccess = activity?.config?.displaySuccess ?: false
                (activity as? MainActivity)?.tryExportNoteValueToFile(note.path, note.title, content, displaySuccess)
            }
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
                callback?.invoke()
            }
        )
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
