package tomato.notes.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import tomato.notes.databinding.ItemNoteCardBinding
import tomato.notes.extensions.applyNoteCardBackground
import tomato.notes.extensions.getMutedTextColor
import tomato.notes.helpers.previewLines
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
            val previewLines = note.previewLines(context)
            val previewViews = listOf(noteCardPreview1, noteCardPreview2, noteCardPreview3, noteCardPreview4)
            noteCardPreviewHolder.beGoneIf(previewLines.isEmpty() || note.isLocked())
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

    fun updateItems(newNotes: List<Note>, newNotebooks: Map<Long, Notebook>, showNotebook: Boolean) {
        notes = newNotes.toMutableList()
        notebooksById = newNotebooks
        showNotebookName = showNotebook
        notifyDataSetChanged()
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
