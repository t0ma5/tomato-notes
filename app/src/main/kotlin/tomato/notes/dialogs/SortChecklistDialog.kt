package tomato.notes.dialogs

import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.SORT_BY_CUSTOM
import com.simplemobiletools.commons.helpers.SORT_BY_DATE_CREATED
import com.simplemobiletools.commons.helpers.SORT_BY_TITLE
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import tomato.notes.R
import tomato.notes.helpers.SORT_BY_DONE
import tomato.notes.activities.SimpleActivity
import tomato.notes.databinding.DialogSortChecklistBinding
import tomato.notes.extensions.config

class SortChecklistDialog(private val activity: SimpleActivity, private val noteId: Long?, private val callback: () -> Unit) {
    private val binding = DialogSortChecklistBinding.inflate(activity.layoutInflater)
    private val view = binding.root
    private val config = activity.config
    private var currSorting = config.getChecklistSorting(noteId)

    init {
        setupSortRadio()
        setupOrderRadio()
        setupMoveUndoneChecklistItems()
        setupUseForThisChecklistOnly()

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, com.simplemobiletools.commons.R.string.sort_by)
            }
    }

    private fun setupSortRadio() {
        val fieldRadio = binding.sortingDialogRadioSorting
        fieldRadio.setOnCheckedChangeListener { _, checkedId ->
            val isCustomSorting = checkedId == binding.sortingDialogRadioCustom.id
            binding.sortingDialogRadioOrder.beGoneIf(isCustomSorting)
            binding.sortingDialogOrderDivider.root.beGoneIf(isCustomSorting)
            binding.moveUndoneChecklistItemsDivider.root.beGoneIf(isCustomSorting)
            binding.settingsMoveUndoneChecklistItemsHolder.beGoneIf(isCustomSorting)
        }

        var fieldBtn = binding.sortingDialogRadioTitle

        if (currSorting and SORT_BY_DATE_CREATED != 0) {
            fieldBtn = binding.sortingDialogRadioDateCreated
        }

        if (currSorting and SORT_BY_DONE != 0) {
            fieldBtn = binding.sortingDialogRadioDone
        }

        if (currSorting and SORT_BY_CUSTOM != 0) {
            fieldBtn = binding.sortingDialogRadioCustom
        }

        fieldBtn.isChecked = true
    }

    private fun setupOrderRadio() {
        var orderBtn = binding.sortingDialogRadioAscending

        if (currSorting and SORT_DESCENDING != 0) {
            orderBtn = binding.sortingDialogRadioDescending
        }

        orderBtn.isChecked = true
    }

    private fun setupMoveUndoneChecklistItems() {
        binding.settingsMoveUndoneChecklistItems.isChecked = config.moveDoneChecklistItems
        binding.settingsMoveUndoneChecklistItemsHolder.setOnClickListener {
            binding.settingsMoveUndoneChecklistItems.toggle()
        }
    }

    private fun setupUseForThisChecklistOnly() {
        binding.useForThisChecklistHolder.beGoneIf(noteId == null)
        binding.useForThisChecklistDivider.root.beGoneIf(noteId == null)
        binding.useForThisChecklistOnly.isChecked = true
        binding.useForThisChecklistHolder.setOnClickListener {
            binding.useForThisChecklistOnly.toggle()
        }
    }

    private fun dialogConfirmed() {
        val sortingRadio = binding.sortingDialogRadioSorting
        var sorting = when (sortingRadio.checkedRadioButtonId) {
            R.id.sorting_dialog_radio_date_created -> SORT_BY_DATE_CREATED
            R.id.sorting_dialog_radio_done -> SORT_BY_DONE
            R.id.sorting_dialog_radio_custom -> SORT_BY_CUSTOM
            else -> SORT_BY_TITLE
        }

        if (sortingRadio.checkedRadioButtonId != R.id.sorting_dialog_radio_custom
            && binding.sortingDialogRadioOrder.checkedRadioButtonId == R.id.sorting_dialog_radio_descending
        ) {
            sorting = sorting or SORT_DESCENDING
        }

        if (noteId != null && binding.useForThisChecklistOnly.isChecked) {
            config.saveChecklistSorting(noteId, sorting)
        } else {
            if (noteId != null) {
                config.removeChecklistSorting(noteId)
            }
            config.sorting = sorting
        }

        config.moveDoneChecklistItems = binding.settingsMoveUndoneChecklistItems.isChecked
        callback()
    }
}
