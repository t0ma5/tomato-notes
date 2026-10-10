package tomato.notes.dialogs

import android.app.Dialog
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import tomato.notes.databinding.DialogRenameChecklistItemBinding
import tomato.notes.extensions.applyNotesDialogChrome

class RenameChecklistItemDialog : DialogFragment() {
    private lateinit var binding: DialogRenameChecklistItemBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val title = savedInstanceState?.getString(STATE_TITLE)
            ?: requireArguments().getString(ARG_TITLE).orEmpty()
        binding = DialogRenameChecklistItemBinding.inflate(layoutInflater).apply {
            checklistItemTitle.setText(title)
            checklistItemTitle.setSelection(title.length)
            checklistItemTitle.isSaveEnabled = false
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .setView(binding.root)

        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        val alertDialog = dialog as? AlertDialog ?: return
        val activity = activity ?: return
        alertDialog.applyNotesDialogChrome(activity)
        alertDialog.showKeyboard(binding.checklistItemTitle)
        alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
            val newTitle = binding.checklistItemTitle.value
            when {
                newTitle.isEmpty() -> activity.toast(com.simplemobiletools.commons.R.string.empty_name)
                else -> {
                    parentFragmentManager.setFragmentResult(REQUEST, Bundle().apply {
                        putInt(RESULT_ITEM_ID, requireArguments().getInt(ARG_ITEM_ID))
                        putString(RESULT_TITLE, newTitle)
                    })
                    dismiss()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::binding.isInitialized) {
            return
        }
        outState.putString(STATE_TITLE, binding.checklistItemTitle.text?.toString().orEmpty())
    }

    companion object {
        const val REQUEST = "rename_checklist_item"
        const val RESULT_ITEM_ID = "item_id"
        const val RESULT_TITLE = "title"
        private const val ARG_ITEM_ID = "item_id"
        private const val ARG_TITLE = "title"
        private const val STATE_TITLE = "title"

        fun newInstance(itemId: Int, title: String) = RenameChecklistItemDialog().apply {
            arguments = Bundle().apply {
                putInt(ARG_ITEM_ID, itemId)
                putString(ARG_TITLE, title)
            }
        }
    }
}
