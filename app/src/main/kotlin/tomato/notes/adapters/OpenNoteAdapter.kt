package tomato.notes.adapters

import android.content.Context
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.views.MyRecyclerView
import tomato.notes.databinding.OpenNoteItemBinding
import tomato.notes.extensions.applyNoteCardBackground
import tomato.notes.extensions.config
import tomato.notes.extensions.getMutedTextColor
import tomato.notes.models.ChecklistItem
import tomato.notes.models.CounterItem
import tomato.notes.models.Note
import tomato.notes.models.NoteType

class OpenNoteAdapter(
    activity: BaseSimpleActivity, var items: List<Note>,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    override fun getActionMenuId() = 0

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = itemCount

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.id?.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun prepareActionMode(menu: Menu) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(OpenNoteItemBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bindView(item, true, false) { itemView, layoutPosition ->
            setupView(itemView, item)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    private fun setupView(view: View, note: Note) {
        OpenNoteItemBinding.bind(view).apply {
            root.setupCard()
            openNoteItemTitle.apply {
                text = note.title
                setTextColor(properPrimaryColor)
            }
            val formattedText = note.getFormattedValue(root.context)
            openNoteItemText.beGoneIf(formattedText.isNullOrBlank() || note.isLocked())
            iconLock.beVisibleIf(note.isLocked())
            iconLock.setImageDrawable(activity.resources.getColoredDrawableWithColor(com.simplemobiletools.commons.R.drawable.ic_lock_vector, properPrimaryColor))
            iconPin.beGone()
            val tags = note.formattedTags()
            openNoteItemTags.beVisibleIf(tags.isNotEmpty())
            openNoteItemTags.text = tags
            openNoteItemTags.setTextColor(activity.getMutedTextColor())
            openNoteItemText.apply {
                text = formattedText
                setTextColor(textColor)
            }
        }
    }

    private fun View.setupCard() {
        applyNoteCardBackground()
    }

    private fun Note.getFormattedValue(context: Context): CharSequence? {
        return when (type) {
            NoteType.TYPE_TEXT -> getNoteStoredValue(context)
            NoteType.TYPE_CHECKLIST -> {
                val checklistItemType = object : TypeToken<List<ChecklistItem>>() {}.type
                var items = Gson().fromJson<List<ChecklistItem>>(getNoteStoredValue(context), checklistItemType) ?: listOf()
                items = ChecklistItem.sorted(
                    items.filter { it.title != null },
                    context.config.getChecklistSorting(id),
                    context.config.moveDoneChecklistItems
                )
                val linePrefix = "• "
                val stringifiedItems = items.joinToString(separator = System.lineSeparator()) {
                    "${linePrefix}${it.title}"
                }

                val formattedText = SpannableString(stringifiedItems)
                var currentPos = 0
                items.forEach { item ->
                    currentPos += linePrefix.length
                    if (item.isDone) {
                        formattedText.setSpan(StrikethroughSpan(), currentPos, currentPos + item.title.length, 0)
                    }
                    currentPos += item.title.length
                    currentPos += System.lineSeparator().length
                }
                formattedText
            }

            NoteType.TYPE_COUNTER -> {
                val counterItemType = object : TypeToken<List<CounterItem>>() {}.type
                val rawValue = getNoteStoredValue(context)?.ifEmpty { "[]" } ?: "[]"
                val items = Gson().fromJson<List<CounterItem>>(rawValue, counterItemType) ?: listOf()
                items.joinToString(separator = System.lineSeparator()) { "${it.title}: ${it.count}" }
            }
        }
    }
}
