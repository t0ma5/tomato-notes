package tomato.notes.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import tomato.notes.databinding.ItemNoteCardBinding
import tomato.notes.extensions.applyNoteCardBackground
import tomato.notes.extensions.getMutedTextColor
import tomato.notes.helpers.NotebookUnlockSession
import tomato.notes.helpers.previewLines
import tomato.notes.R
import tomato.notes.models.Note
import tomato.notes.models.Notebook
import java.util.Collections

class NotesListAdapter(
    private var notes: MutableList<Note>,
    private var notebooksById: Map<Long, Notebook>,
    private var showNotebookName: Boolean,
    private val itemClick: (Note) -> Unit,
    private val itemsReordered: (List<Note>) -> Unit,
    private val pinToggle: ((Note) -> Unit)? = null,
    private val deleteNote: ((Note) -> Unit)? = null,
) : RecyclerView.Adapter<NotesListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemNoteCardBinding) : RecyclerView.ViewHolder(binding.root) {
        // TalkBack actions added on the last bind, removed before the next one
        val accessibilityActionIds = mutableListOf<Int>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNoteCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = notes[position]
        val context = holder.itemView.context
        val primary = context.getProperPrimaryColor()
        val text = context.getProperTextColor()
        val muted = context.getMutedTextColor()
        holder.binding.apply {
            noteCardBody.applyNoteCardBackground()
            noteCardTitle.text = note.title
            noteCardTitle.setTextColor(text)
            // the pin sits in the top corner, so a pinned card's title stops before it
            noteCardPin.beVisibleIf(note.isPinned())
            if (note.isPinned()) {
                noteCardPin.setImageDrawable(context.resources.getColoredDrawableWithColor(R.drawable.ic_pin_vector, primary))
            }
            (noteCardTitle.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val endMargin = if (note.isPinned()) (22 * context.resources.displayMetrics.density).toInt() else 0
                if (params.marginEnd != endMargin) {
                    params.marginEnd = endMargin
                    noteCardTitle.layoutParams = params
                }
            }
            // A locked notebook hides previews until it is unlocked in this session
            val hidePreview = note.isLocked() || !NotebookUnlockSession.canShow(notebooksById[note.notebookId])
            val previewLines = if (hidePreview) emptyList() else note.previewLines(context)
            val previewViews = listOf(noteCardPreview1, noteCardPreview2, noteCardPreview3, noteCardPreview4)
            noteCardPreviewHolder.beGoneIf(previewLines.isEmpty())
            previewViews.forEachIndexed { index, view ->
                val line = previewLines.getOrNull(index)
                view.beGoneIf(line.isNullOrBlank())
                bindPreviewLine(view, line, text)
            }
            noteCardLock.beVisibleIf(note.isLocked())
            if (note.isLocked()) {
                noteCardLock.setImageDrawable(
                    context.resources.getColoredDrawableWithColor(com.simplemobiletools.commons.R.drawable.ic_lock_vector, primary)
                )
            }
            val notebookTitle = notebooksById[note.notebookId]?.title.orEmpty()
            noteCardNotebook.text = notebookTitle
            noteCardNotebook.setTextColor(muted)
            noteCardNotebook.beVisibleIf(showNotebookName && notebookTitle.isNotEmpty())
            // TalkBack reads the card as one item: title, notebook, pinned, locked
            root.contentDescription = listOfNotNull(
                note.title,
                notebookTitle.takeIf { showNotebookName && it.isNotEmpty() },
                context.getString(R.string.a11y_pinned).takeIf { note.isPinned() },
                context.getString(R.string.a11y_note_locked).takeIf { note.isLocked() }
            ).joinToString(", ")
            root.setOnClickListener { itemClick(note) }
            bindAccessibilityActions(holder, note)
        }
    }

    override fun getItemCount() = notes.size

    fun getNoteAt(position: Int): Note? = notes.getOrNull(position)

    // Removes a swiped card right away, the next refresh confirms it
    fun removeNoteAt(position: Int) {
        if (position in notes.indices) {
            notes.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    // The swipe actions (pin and delete) as TalkBack actions, so they work without swiping
    private fun bindAccessibilityActions(holder: ViewHolder, note: Note) {
        val view = holder.itemView
        holder.accessibilityActionIds.forEach { ViewCompat.removeAccessibilityAction(view, it) }
        holder.accessibilityActionIds.clear()
        val context = view.context
        pinToggle?.let { toggle ->
            val label = context.getString(if (note.isPinned()) R.string.unpin else R.string.pin)
            holder.accessibilityActionIds += ViewCompat.addAccessibilityAction(view, label) { _, _ ->
                toggle(note)
                true
            }
        }
        deleteNote?.let { delete ->
            val label = context.getString(com.simplemobiletools.commons.R.string.delete)
            holder.accessibilityActionIds += ViewCompat.addAccessibilityAction(view, label) { _, _ ->
                delete(note)
                true
            }
        }
        holder.accessibilityActionIds.removeAll { it == View.NO_ID }
    }

    private fun bindPreviewLine(view: TextView, line: String?, textColor: Int) {
        view.setLines(1)
        view.maxLines = 1
        view.setHorizontallyScrolling(true)
        view.ellipsize = TextUtils.TruncateAt.END
        view.text = line
        view.setTextColor(textColor)
    }

    // DiffUtil so added, removed and moved cards animate instead of the whole grid redrawing
    fun updateItems(newNotes: List<Note>, newNotebooks: Map<Long, Notebook>, showNotebook: Boolean) {
        val oldNotes = notes
        val oldNotebooks = notebooksById
        val oldShowNotebook = showNotebookName
        val oldVisibility = oldNotes.associate { it.notebookId to NotebookUnlockSession.canShow(oldNotebooks[it.notebookId]) }
        notes = newNotes.toMutableList()
        notebooksById = newNotebooks
        showNotebookName = showNotebook
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldNotes.size

            override fun getNewListSize() = notes.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldNotes[oldItemPosition].id == notes[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldNotes[oldItemPosition]
                val new = notes[newItemPosition]
                return old == new && oldShowNotebook == showNotebookName &&
                    oldNotebooks[old.notebookId] == notebooksById[new.notebookId] &&
                    oldVisibility[old.notebookId] == NotebookUnlockSession.canShow(notebooksById[new.notebookId])
            }
        }).dispatchUpdatesTo(this)
    }

    // Pinned notes stay first, so a card moves only among cards with the same pin state
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition == toPosition) {
            return false
        }
        val from = notes.getOrNull(fromPosition) ?: return false
        val to = notes.getOrNull(toPosition) ?: return false
        if (from.isPinned() != to.isPinned()) {
            return false
        }
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(notes, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(notes, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun onDragFinished() {
        itemsReordered(notes)
    }
}
