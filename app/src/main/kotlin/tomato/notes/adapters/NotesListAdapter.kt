package tomato.notes.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
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
) : RecyclerView.Adapter<NotesListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemNoteCardBinding) : RecyclerView.ViewHolder(binding.root)

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
            // TalkBack reads the card as one item: title, notebook, locked
            root.contentDescription = listOfNotNull(
                note.title,
                notebookTitle.takeIf { showNotebookName && it.isNotEmpty() },
                context.getString(R.string.a11y_note_locked).takeIf { note.isLocked() }
            ).joinToString(", ")
            root.setOnClickListener { itemClick(note) }
        }
    }

    override fun getItemCount() = notes.size

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

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition == toPosition) {
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
