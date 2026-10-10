package tomato.notes.interfaces

import tomato.notes.models.ChecklistItem

interface ChecklistItemsListener {
    fun refreshItems()

    fun saveChecklist(callback: () -> Unit = {})

    fun migrateChecklistItems(itemIds: List<Int>)

    fun moveEntireNote()

    fun captureHistory()

    fun renameChecklistItem(item: ChecklistItem)

    fun onItemsReordered(reorderedItems: List<ChecklistItem>) {}
}
