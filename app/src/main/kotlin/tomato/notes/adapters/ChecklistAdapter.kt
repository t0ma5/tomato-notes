package tomato.notes.adapters

import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.removeBit
import com.simplemobiletools.commons.helpers.SORT_BY_CUSTOM
import com.simplemobiletools.commons.interfaces.ItemMoveCallback
import com.simplemobiletools.commons.interfaces.ItemTouchHelperContract
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.views.MyRecyclerView
import tomato.notes.R
import tomato.notes.databinding.ItemCheckedTasksBinding
import tomato.notes.databinding.ItemChecklistBinding
import tomato.notes.extensions.config
import tomato.notes.extensions.getPercentageFontSize
import tomato.notes.helpers.DONE_CHECKLIST_ITEM_ALPHA
import tomato.notes.interfaces.ChecklistItemsListener
import tomato.notes.models.ChecklistItem
import java.util.Collections

class ChecklistAdapter(
    activity: BaseSimpleActivity,
    var items: MutableList<ChecklistItem>,
    val listener: ChecklistItemsListener?,
    recyclerView: MyRecyclerView,
    val showIcons: Boolean,
    private val noteId: Long,
    private val readOnly: Boolean = false,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract {

    private lateinit var crossDrawable: Drawable
    private lateinit var checkDrawable: Drawable
    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener

    init {
        setupDragListener(!readOnly)
        initDrawables()

        if (!readOnly) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this))
            touchHelper!!.attachToRecyclerView(recyclerView)
        }

        startReorderDragListener = object : StartReorderDragListener {
            override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                if (!readOnly) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = if (readOnly) 0 else R.menu.cab_checklist

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_move_to_top -> moveSelectedItemsToTop()
            R.id.cab_move_to_bottom -> moveSelectedItemsToBottom()
            R.id.cab_rename -> renameChecklistItem()
            R.id.cab_migrate -> migrateSelectedItems()
            R.id.cab_delete -> deleteSelection()
        }
    }

    override fun getSelectableItemCount() = if (readOnly) 0 else items.count { !it.isSectionHeader() }

    override fun getIsItemSelectable(position: Int) = !readOnly && items.getOrNull(position)?.isSectionHeader() != true

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun prepareActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) {
            return
        }

        menu.findItem(R.id.cab_rename).isVisible = isOneItemSelected()
    }

    override fun getItemViewType(position: Int) = if (items.getOrNull(position)?.isSectionHeader() == true) {
        VIEW_TYPE_CHECKED_SECTION
    } else {
        VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = if (viewType == VIEW_TYPE_CHECKED_SECTION) {
            ItemCheckedTasksBinding.inflate(layoutInflater, parent, false).root
        } else {
            ItemChecklistBinding.inflate(layoutInflater, parent, false).root
        }
        return createViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        if (item.isSectionHeader()) {
            holder.bindView(item, true, false) { itemView, _ ->
                setupSectionView(itemView, item)
            }
        } else {
            holder.bindView(item, true, true) { itemView, _ ->
                setupView(itemView, item, holder)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    private fun initDrawables() {
        val res = activity.resources
        crossDrawable = res.getColoredDrawableWithColor(
            com.simplemobiletools.commons.R.drawable.ic_cross_vector,
            res.getColor(com.simplemobiletools.commons.R.color.md_red_700)
        )
        checkDrawable = res.getColoredDrawableWithColor(
            com.simplemobiletools.commons.R.drawable.ic_check_vector,
            res.getColor(com.simplemobiletools.commons.R.color.md_green_700)
        )
    }

    private fun renameChecklistItem() {
        listener?.renameChecklistItem(getSelectedItems().first())
    }

    private fun migrateSelectedItems() {
        listener?.migrateChecklistItems(selectedKeys.toList())
        finishActMode()
    }

    private fun deleteSelection() {
        val removeItems = ArrayList<ChecklistItem>(selectedKeys.size)
        val positions = ArrayList<Int>()
        selectedKeys.forEach {
            val key = it
            val position = items.indexOfFirst { it.id == key }
            if (position != -1) {
                positions.add(position)

                val favorite = getItemWithKey(key)
                if (favorite != null) {
                    removeItems.add(favorite)
                }
            }
        }

        listener?.captureHistory()
        items.removeAll(removeItems.toSet())
        listener?.onItemsReordered(items)
        positions.sortDescending()
        removeSelectedItems(positions)

        listener?.saveChecklist {
            if (items.none { !it.isSectionHeader() }) {
                listener.refreshItems()
            }
        }
    }

    private fun moveSelectedItemsToTop() {
        listener?.captureHistory()
        activity.config.saveChecklistSorting(noteId, SORT_BY_CUSTOM)
        val movedPositions = mutableListOf<Int>()
        selectedKeys.reversed().forEach { checklistId ->
            val position = items.indexOfFirst { it.id == checklistId }
            if (position == -1 || items[position].isSectionHeader()) {
                return@forEach
            }
            val tempItem = items[position]
            items.removeAt(position)
            movedPositions.add(position)
            items.add(0, tempItem)
        }

        movedPositions.forEach {
            notifyItemMoved(it, 0)
        }
        listener?.onItemsReordered(items)
        listener?.saveChecklist()
    }

    private fun moveSelectedItemsToBottom() {
        listener?.captureHistory()
        activity.config.saveChecklistSorting(noteId, SORT_BY_CUSTOM)
        val movedPositions = mutableListOf<Int>()
        selectedKeys.forEach { checklistId ->
            val position = items.indexOfFirst { it.id == checklistId }
            if (position == -1 || items[position].isSectionHeader()) {
                return@forEach
            }
            val tempItem = items[position]
            items.removeAt(position)
            movedPositions.add(position)
            items.add(items.size, tempItem)
        }

        movedPositions.forEach {
            notifyItemMoved(it, items.size - 1)
        }
        listener?.onItemsReordered(items)
        listener?.saveChecklist()
    }

    private fun getItemWithKey(key: Int): ChecklistItem? = items.firstOrNull { it.id == key }

    private fun getSelectedItems() = items.filter { selectedKeys.contains(it.id) && !it.isSectionHeader() } as ArrayList<ChecklistItem>

    private fun setupSectionView(view: View, checklistItem: ChecklistItem) {
        ItemCheckedTasksBinding.bind(view).apply {
            checkedTasksTitle.apply {
                text = checklistItem.title
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getPercentageFontSize())
            }

            val headerIndex = items.indexOfFirst { it.id == checklistItem.id }
            val collapsed = headerIndex == items.lastIndex
            checkedTasksChevron.rotation = if (collapsed) 180f else 0f
            checkedTasksChevron.applyColorFilter(textColor)
        }
    }

    private fun setupView(view: View, checklistItem: ChecklistItem, holder: ViewHolder) {
        val isSelected = selectedKeys.contains(checklistItem.id)
        ItemChecklistBinding.bind(view).apply {
            checklistTitle.apply {
                text = checklistItem.title
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getPercentageFontSize())
                gravity = context.config.getTextGravity()

                if (checklistItem.isDone) {
                    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    alpha = DONE_CHECKLIST_ITEM_ALPHA
                } else {
                    paintFlags = paintFlags.removeBit(Paint.STRIKE_THRU_TEXT_FLAG)
                    alpha = 1f
                }
            }

            checklistImage.setImageDrawable(if (checklistItem.isDone) checkDrawable else crossDrawable)
            checklistImage.beVisibleIf(showIcons && selectedKeys.isEmpty())
            checklistHolder.isSelected = isSelected

            checklistDragHandle.beVisibleIf(selectedKeys.isNotEmpty())
            checklistDragHandle.applyColorFilter(textColor)
            checklistDragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startReorderDragListener.requestDrag(holder)
                }
                false
            }
        }
    }

    private var capturedDrag = false

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        val fromItem = items.getOrNull(fromPosition) ?: return
        val toItem = items.getOrNull(toPosition) ?: return
        if (fromItem.isSectionHeader() || toItem.isSectionHeader()) {
            return
        }
        if (!capturedDrag) {
            listener?.captureHistory()
            capturedDrag = true
        }
        activity.config.saveChecklistSorting(noteId, SORT_BY_CUSTOM)
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {
        capturedDrag = false
    }

    override fun onRowClear(myViewHolder: ViewHolder?) {
        listener?.onItemsReordered(items)
        listener?.saveChecklist()
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_CHECKED_SECTION = 1
    }
}
