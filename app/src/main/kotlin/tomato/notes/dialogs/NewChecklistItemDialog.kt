package tomato.notes.dialogs

import android.app.Dialog
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.DialogFragment
import com.simplemobiletools.commons.databinding.DialogTitleBinding
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.isWhiteTheme
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.helpers.DARK_GREY
import com.simplemobiletools.commons.helpers.SORT_BY_CUSTOM
import tomato.notes.R
import tomato.notes.databinding.DialogNewChecklistItemBinding
import tomato.notes.databinding.ItemAddChecklistBinding
import tomato.notes.extensions.applyNotesDialogChrome
import tomato.notes.extensions.config

class NewChecklistItemDialog : DialogFragment() {
    private val titles = mutableListOf<AppCompatEditText>()
    private lateinit var binding: DialogNewChecklistItemBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val noteId = requireArguments().getLong(ARG_NOTE_ID)
        binding = DialogNewChecklistItemBinding.inflate(layoutInflater)
        val savedTitles = savedInstanceState?.getStringArrayList(STATE_TITLES)
        val rows = if (savedTitles.isNullOrEmpty()) listOf("") else savedTitles

        val plusTextColor = if (activity.isWhiteTheme()) {
            DARK_GREY
        } else {
            activity.getProperPrimaryColor().getContrastColor()
        }

        binding.apply {
            addItem.applyColorFilter(plusTextColor)
            addItem.setOnClickListener {
                addNewEditText()
            }
            settingsAddChecklistTop.beVisibleIf(activity.config.getChecklistSorting(noteId) and SORT_BY_CUSTOM != 0)
            settingsAddChecklistTop.isChecked = savedInstanceState?.getBoolean(STATE_ADD_TOP)
                ?: activity.config.addNewChecklistItemsTop
        }

        rows.forEach { addNewEditText(it, focus = false) }
        activity.updateTextColors(binding.root)

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .setView(binding.root)

        if (activity.baseConfig.isUsingSystemTheme) {
            builder.setTitle(R.string.add_new_checklist_items)
        }

        val dialog = builder.create()
        if (!activity.baseConfig.isUsingSystemTheme) {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val title = DialogTitleBinding.inflate(activity.layoutInflater)
            title.dialogTitleTextview.apply {
                setText(R.string.add_new_checklist_items)
                setTextColor(activity.getProperTextColor())
            }
            dialog.setCustomTitle(title.root)
        }
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val alertDialog = dialog as? AlertDialog ?: return
        val activity = activity ?: return
        alertDialog.applyNotesDialogChrome(activity)
        titles.lastOrNull()?.let { alertDialog.showKeyboard(it) }
        alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
            activity.config.addNewChecklistItemsTop = binding.settingsAddChecklistTop.isChecked
            when {
                titles.all { it.text.isNullOrEmpty() } -> activity.toast(com.simplemobiletools.commons.R.string.empty_name)
                else -> {
                    val entered = titles.map { it.text.toString() }.filter { it.isNotEmpty() }
                    parentFragmentManager.setFragmentResult(REQUEST, Bundle().apply {
                        putStringArrayList(RESULT_TITLES, ArrayList(entered))
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
        outState.putStringArrayList(STATE_TITLES, ArrayList(titles.map { it.text?.toString().orEmpty() }))
        outState.putBoolean(STATE_ADD_TOP, binding.settingsAddChecklistTop.isChecked)
    }

    private fun addNewEditText(initial: String = "", focus: Boolean = true) {
        val activity = activity ?: return
        ItemAddChecklistBinding.inflate(activity.layoutInflater).apply {
            root.setText(initial)
            root.isSaveEnabled = false
            root.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE || actionId == KeyEvent.KEYCODE_ENTER) {
                    addNewEditText()
                    true
                } else {
                    false
                }
            }
            titles.add(root)
            binding.checklistHolder.addView(this.root)
            activity.updateTextColors(binding.checklistHolder)
            if (focus) {
                binding.dialogHolder.post {
                    binding.dialogHolder.fullScroll(View.FOCUS_DOWN)
                    activity.showKeyboard(root)
                }
            }
        }
    }

    companion object {
        const val REQUEST = "new_checklist_items"
        const val RESULT_TITLES = "titles"
        private const val ARG_NOTE_ID = "note_id"
        private const val STATE_TITLES = "titles"
        private const val STATE_ADD_TOP = "add_top"

        fun newInstance(noteId: Long) = NewChecklistItemDialog().apply {
            arguments = Bundle().apply { putLong(ARG_NOTE_ID, noteId) }
        }
    }
}
