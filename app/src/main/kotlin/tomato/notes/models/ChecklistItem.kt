package tomato.notes.models

import com.simplemobiletools.commons.helpers.SORT_BY_CUSTOM
import com.simplemobiletools.commons.helpers.SORT_BY_TITLE
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import tomato.notes.helpers.CHECKED_ITEMS_TITLE_ID
import tomato.notes.helpers.CollatorBasedComparator
import tomato.notes.helpers.SORT_BY_DONE
import kotlinx.serialization.Serializable

@Serializable
data class ChecklistItem(
    val id: Int,
    val dateCreated: Long = 0L,
    var title: String,
    var isDone: Boolean
) : Comparable<ChecklistItem> {

    companion object {
        var sorting = 0

        fun sorted(items: List<ChecklistItem>, sorting: Int, moveDoneToBottom: Boolean): MutableList<ChecklistItem> {
            this.sorting = sorting
            val result = items.filter { !it.isSectionHeader() }.toMutableList()
            if (sorting and SORT_BY_CUSTOM == 0) {
                result.sort()
                if (moveDoneToBottom) {
                    result.sortBy { it.isDone }
                }
            }
            return result
        }
    }

    fun isSectionHeader() = id == CHECKED_ITEMS_TITLE_ID

    override fun compareTo(other: ChecklistItem): Int {
        var result = when {
            sorting and SORT_BY_DONE != 0 -> isDone.compareTo(other.isDone)
            sorting and SORT_BY_TITLE != 0 -> CollatorBasedComparator().compare(title, other.title)
            else -> dateCreated.compareTo(other.dateCreated)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }
}
