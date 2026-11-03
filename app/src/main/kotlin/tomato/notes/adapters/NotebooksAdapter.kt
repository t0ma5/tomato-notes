package tomato.notes.adapters

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.hypot
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import tomato.notes.R
import tomato.notes.databinding.ItemNotebookBinding
import tomato.notes.extensions.applyNoteCardBackground
import tomato.notes.extensions.getMutedTextColor
import tomato.notes.models.Notebook
import java.util.Collections

class NotebooksAdapter(
    private var notebooks: MutableList<Notebook>,
    private var noteCounts: Map<Long, Int> = emptyMap(),
    private val itemClick: (Notebook) -> Unit,
    private val itemLongClick: (Notebook) -> Unit,
    private val dragStart: (RecyclerView.ViewHolder) -> Unit,
    private val itemsReordered: (List<Notebook>) -> Unit,
) : RecyclerView.Adapter<NotebooksAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemNotebookBinding) : RecyclerView.ViewHolder(binding.root) {
        var handleDragStarted = false
        var handleDownRawX = 0f
        var handleDownRawY = 0f
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotebookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notebook = notebooks[position]
        val context = holder.itemView.context
        val primary = context.getProperPrimaryColor()
        val text = context.getProperTextColor()
        val muted = context.getMutedTextColor()
        holder.binding.apply {
            root.applyNoteCardBackground()
            notebookTitle.text = notebook.title
            notebookTitle.setTextColor(text)
            val count = noteCounts[notebook.id] ?: 0
            notebookNoteCount.text = root.resources.getQuantityString(R.plurals.notebook_note_count, count, count)
            notebookNoteCount.setTextColor(muted)
            notebookIcon.applyColorFilter(primary)
            notebookLockIcon.visibility = if (notebook.isLocked()) android.view.View.VISIBLE else android.view.View.GONE
            if (notebook.isLocked()) {
                notebookLockIcon.setImageDrawable(
                    root.resources.getColoredDrawableWithColor(
                        com.simplemobiletools.commons.R.drawable.ic_lock_vector,
                        primary
                    )
                )
                notebookLockIcon.applyColorFilter(primary)
            }

            notebookPinnedIcon.visibility = if (notebook.isPinned()) View.VISIBLE else View.GONE
            if (notebook.isPinned()) {
                notebookPinnedIcon.applyColorFilter(primary)
            }

            // TalkBack reads the card as one item: title, pinned, locked, note count
            root.contentDescription = listOfNotNull(
                notebook.title,
                context.getString(R.string.a11y_pinned).takeIf { notebook.isPinned() },
                context.getString(R.string.a11y_note_locked).takeIf { notebook.isLocked() },
                notebookNoteCount.text
            ).joinToString(", ")

            notebookDragHandle.applyColorFilter(muted)
            notebookDragHandle.contentDescription = context.getString(R.string.a11y_reorder_item)
            notebookDragHandle.expandTouchAreaTo48dp()
            val touchSlop = ViewConfiguration.get(root.context).scaledTouchSlop
            notebookDragHandle.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        holder.handleDragStarted = false
                        holder.handleDownRawX = event.rawX
                        holder.handleDownRawY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!holder.handleDragStarted) {
                            val dx = event.rawX - holder.handleDownRawX
                            val dy = event.rawY - holder.handleDownRawY
                            if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                                holder.handleDragStarted = true
                                dragStart(holder)
                            }
                        }
                    }
                }
                false
            }
            notebookDragHandle.setOnClickListener {
                if (!holder.handleDragStarted) {
                    itemLongClick(notebook)
                }
            }

            root.setOnClickListener { itemClick(notebook) }
            root.setOnLongClickListener {
                itemLongClick(notebook)
                true
            }
        }
    }

    override fun getItemCount() = notebooks.size

    // DiffUtil so added, removed and moved cards animate instead of the whole grid redrawing
    fun updateItems(newNotebooks: List<Notebook>, newNoteCounts: Map<Long, Int> = noteCounts) {
        val oldNotebooks = notebooks
        val oldNoteCounts = noteCounts
        notebooks = newNotebooks.toMutableList()
        noteCounts = newNoteCounts
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldNotebooks.size

            override fun getNewListSize() = notebooks.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldNotebooks[oldItemPosition].id == notebooks[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldNotebooks[oldItemPosition]
                val new = notebooks[newItemPosition]
                return old == new && oldNoteCounts[old.id] == noteCounts[new.id]
            }
        }).dispatchUpdatesTo(this)
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        val from = notebooks.getOrNull(fromPosition) ?: return false
        val to = notebooks.getOrNull(toPosition) ?: return false
        if (from.pinned != to.pinned) {
            return false
        }

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(notebooks, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(notebooks, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun onDragFinished() {
        itemsReordered(notebooks)
    }
}

// Grows the touch area to at least 48dp without changing how the view looks
private fun View.expandTouchAreaTo48dp() {
    val parentView = parent as? View ?: return
    parentView.post {
        val minSize = (48 * resources.displayMetrics.density).toInt()
        val rect = Rect()
        getHitRect(rect)
        val extraX = ((minSize - rect.width()) / 2).coerceAtLeast(0)
        val extraY = ((minSize - rect.height()) / 2).coerceAtLeast(0)
        rect.inset(-extraX, -extraY)
        parentView.touchDelegate = TouchDelegate(rect, this)
    }
}
