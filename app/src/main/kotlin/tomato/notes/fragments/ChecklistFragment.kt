package tomato.notes.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import tomato.notes.R
import tomato.notes.activities.MainActivity
import tomato.notes.activities.SimpleActivity
import tomato.notes.adapters.ChecklistAdapter
import tomato.notes.databinding.FragmentChecklistBinding
import tomato.notes.dialogs.MigrateChecklistItemsDialog
import tomato.notes.dialogs.NewChecklistItemDialog
import tomato.notes.dialogs.RenameChecklistItemDialog
import tomato.notes.extensions.config
import tomato.notes.extensions.updateWidgets
import tomato.notes.helpers.CHECKED_ITEMS_TITLE_ID
import tomato.notes.helpers.NOTE_ID
import tomato.notes.helpers.NotesHelper
import tomato.notes.interfaces.ChecklistItemsListener
import tomato.notes.models.ChecklistItem
import tomato.notes.models.Note
import tomato.notes.models.SnapshotHistory
import java.io.File

class ChecklistFragment : NoteFragment(), ChecklistItemsListener {

    private var noteId = 0L
    private val itemsHistory = SnapshotHistory()
    private var applyingHistory = false

    private lateinit var binding: FragmentChecklistBinding

    var items = mutableListOf<ChecklistItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.setFragmentResultListener(NewChecklistItemDialog.REQUEST, this) { _, bundle ->
            val titles = bundle.getStringArrayList(NewChecklistItemDialog.RESULT_TITLES) ?: return@setFragmentResultListener
            addChecklistItems(titles)
        }
        childFragmentManager.setFragmentResultListener(RenameChecklistItemDialog.REQUEST, this) { _, bundle ->
            val itemId = bundle.getInt(RenameChecklistItemDialog.RESULT_ITEM_ID)
            val title = bundle.getString(RenameChecklistItemDialog.RESULT_TITLE) ?: return@setFragmentResultListener
            applyRenamedChecklistItem(itemId, title)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentChecklistBinding.inflate(inflater, container, false)
        noteId = requireArguments().getLong(NOTE_ID, 0L)
        setupFragmentColors()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        loadNoteById(noteId)
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)

        if (menuVisible) {
            activity?.hideKeyboard()
            notifyHistoryChanged()
        } else if (::binding.isInitialized) {
            (binding.checklistList.adapter as? ChecklistAdapter)?.finishActMode()
        }
    }

    private fun loadNoteById(noteId: Long) {
        NotesHelper(requireActivity()).getNoteWithId(noteId) { storedNote ->
            if (storedNote != null && activity?.isDestroyed == false) {
                note = storedNote

                try {
                    val checklistItemType = object : TypeToken<List<ChecklistItem>>() {}.type
                    items = Gson().fromJson<ArrayList<ChecklistItem>>(storedNote.getNoteStoredValue(requireActivity()), checklistItemType) ?: ArrayList(1)

                    // checklist title can be null only because of the glitch in upgrade to 6.6.0, remove this check in the future
                    items = items.filter { it.title != null && !it.isSectionHeader() }.toMutableList() as ArrayList<ChecklistItem>
                    setupFragment()
                } catch (e: Exception) {
                    migrateCheckListOnFailure(storedNote)
                }
            }
        }
    }

    private fun migrateCheckListOnFailure(note: Note) {
        items.clear()

        note.getNoteStoredValue(requireActivity())?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEachIndexed { index, value ->
            items.add(
                ChecklistItem(
                    id = index,
                    title = value,
                    isDone = false
                )
            )
        }

        saveChecklist()
    }

    private fun setupFragment() {
        if (activity == null || requireActivity().isFinishing) {
            return
        }

        setupFragmentColors()
        checkLockState()
        setupAdapter()
    }

    private fun setupFragmentColors() {
        val adjustedPrimaryColor = requireActivity().getProperPrimaryColor()
        binding.checklistFab.apply {
            setColors(
                requireActivity().getProperTextColor(),
                adjustedPrimaryColor,
                adjustedPrimaryColor.getContrastColor()
            )

            setOnClickListener {
                if (note?.isReadOnly == true) {
                    return@setOnClickListener
                }
                showNewItemDialog()
                (binding.checklistList.adapter as? ChecklistAdapter)?.finishActMode()
            }
        }

        binding.fragmentPlaceholder.setTextColor(requireActivity().getProperTextColor())
        binding.fragmentPlaceholder2.apply {
            setTextColor(adjustedPrimaryColor)
            underlineText()
            setOnClickListener {
                if (note?.isReadOnly == true) {
                    return@setOnClickListener
                }
                showNewItemDialog()
            }
        }
    }

    override fun checkLockState() {
        if (note == null) {
            return
        }

        binding.apply {
            val canEdit = (!note!!.isLocked() || shouldShowLockedContent) && !note!!.isReadOnly
            checklistContentHolder.beVisibleIf(!note!!.isLocked() || shouldShowLockedContent)
            checklistFab.beVisibleIf(canEdit)
            setupLockedViews(this.toCommonBinding(), note!!)
        }
    }

    override fun updateReadOnlyState(isReadOnly: Boolean) {
        note?.isReadOnly = isReadOnly
        if (::binding.isInitialized) {
            checkLockState()
            setupAdapter()
        }
    }

    private fun showNewItemDialog() {
        NewChecklistItemDialog.newInstance(noteId).show(childFragmentManager, NewChecklistItemDialog.REQUEST)
    }

    private fun addChecklistItems(titles: ArrayList<String>) {
        var currentMaxId = items.maxByOrNull { item -> item.id }?.id ?: 0
        val newItems = ArrayList<ChecklistItem>()

        titles.forEach { title ->
            title.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { row ->
                newItems.add(ChecklistItem(currentMaxId + 1, System.currentTimeMillis(), row, false))
                currentMaxId++
            }
        }

        captureHistory()
        items = persistedItems()
        if (config?.addNewChecklistItemsTop == true) {
            items.addAll(0, newItems)
        } else {
            items.addAll(newItems)
        }

        saveNote()
        setupAdapter()
    }

    override fun renameChecklistItem(item: ChecklistItem) {
        RenameChecklistItemDialog.newInstance(item.id, item.title)
            .show(childFragmentManager, RenameChecklistItemDialog.REQUEST)
    }

    private fun applyRenamedChecklistItem(itemId: Int, newTitle: String) {
        val item = items.firstOrNull { it.id == itemId } ?: return
        captureHistory()
        item.title = newTitle
        saveChecklist()
        (binding.checklistList.adapter as? ChecklistAdapter)?.finishActMode()
        setupAdapter()
    }

    private fun setupAdapter() {
        updateUIVisibility()
        val ctx = context ?: return
        items = ChecklistItem.sorted(
            items = persistedItems(),
            sorting = ctx.config.getChecklistSorting(noteId),
            moveDoneToBottom = ctx.config.moveDoneChecklistItems
        )
        val displayedItems = displayedItems()
        ChecklistAdapter(
            activity = activity as SimpleActivity,
            items = displayedItems,
            listener = this,
            recyclerView = binding.checklistList,
            showIcons = note?.isReadOnly != true,
            noteId = noteId,
            readOnly = note?.isReadOnly == true
        ) { item ->
            val clickedNote = item as ChecklistItem
            if (clickedNote.isSectionHeader()) {
                val collapsed = ctx.config.getCheckedItemsCollapsed(noteId)
                ctx.config.saveCheckedItemsCollapsed(noteId, !collapsed)
                setupAdapter()
                return@ChecklistAdapter
            }
            if (note?.isReadOnly == true) {
                return@ChecklistAdapter
            }
            captureHistory()
            clickedNote.isDone = !clickedNote.isDone

            saveNote()
            setupAdapter()
            context?.updateWidgets()
        }.apply {
            binding.checklistList.adapter = this
        }
    }

    private fun persistedItems() = items.filter { !it.isSectionHeader() }.toMutableList()

    private fun displayedItems(): MutableList<ChecklistItem> {
        if (config?.moveDoneChecklistItems != true) {
            return items
        }

        val checkedItems = items.filter { it.isDone }
        if (checkedItems.isEmpty()) {
            return items
        }

        val displayed = items.filter { !it.isDone }.toMutableList()
        displayed.add(
            ChecklistItem(
                id = CHECKED_ITEMS_TITLE_ID,
                dateCreated = 0L,
                title = getString(R.string.checked_items_count, checkedItems.size),
                isDone = false
            )
        )
        if (config?.getCheckedItemsCollapsed(noteId) != true) {
            displayed.addAll(checkedItems)
        }
        return displayed
    }

    private fun saveNote(refreshIndex: Int = -1, callback: () -> Unit = {}) {
        if (note == null) {
            return
        }

        if (note!!.path.isNotEmpty() && !note!!.path.startsWith("content://") && !File(note!!.path).exists()) {
            return
        }

        if (context == null || activity == null) {
            return
        }

        if (note != null) {
            if (refreshIndex != -1) {
                binding.checklistList.post {
                    binding.checklistList.adapter?.notifyItemChanged(refreshIndex)
                }
            }

            note!!.value = getChecklistItems()

            ensureBackgroundThread {
                saveNoteValue(note!!, note!!.value)
                context?.updateWidgets()
                activity?.runOnUiThread(callback)
            }
        }
    }

    fun removeDoneItems() {
        captureHistory()
        items = persistedItems().filter { !it.isDone }.toMutableList() as ArrayList<ChecklistItem>
        saveNote()
        setupAdapter()
    }

    fun uncheckAllItems() {
        captureHistory()
        persistedItems().forEach { it.isDone = false }
        saveNote()
        setupAdapter()
    }

    private fun updateUIVisibility() {
        val isEmpty = persistedItems().isEmpty()
        binding.apply {
            fragmentPlaceholder.beVisibleIf(isEmpty)
            fragmentPlaceholder2.beVisibleIf(isEmpty && note?.isReadOnly != true)
            checklistList.beVisibleIf(!isEmpty)
        }
    }

    fun getChecklistItems() = Gson().toJson(persistedItems())

    override fun saveChecklist(callback: () -> Unit) {
        if (note?.isReadOnly == true) {
            return
        }
        syncFromDisplayed()
        saveNote(callback = callback)
    }

    override fun onItemsReordered(reorderedItems: List<ChecklistItem>) {
        if (note?.isReadOnly == true) {
            return
        }
        syncFromDisplayed(reorderedItems)
    }

    private fun syncFromDisplayed(displayed: List<ChecklistItem>? = (binding.checklistList.adapter as? ChecklistAdapter)?.items) {
        if (displayed == null) {
            items = persistedItems()
            return
        }
        val visible = displayed.filter { !it.isSectionHeader() }
        val visibleIds = visible.map { it.id }.toSet()
        val hidden = persistedItems().filter { it.id !in visibleIds }
        items = (visible + hidden).toMutableList()
    }

    override fun captureHistory() {
        if (applyingHistory) {
            return
        }
        itemsHistory.push(getChecklistItems())
        notifyHistoryChanged()
    }

    override fun undo() {
        val previous = itemsHistory.undo(getChecklistItems()) ?: return
        applySnapshot(previous)
    }

    override fun redo() {
        val next = itemsHistory.redo(getChecklistItems()) ?: return
        applySnapshot(next)
    }

    override fun isUndoAvailable() = itemsHistory.canUndo()

    override fun isRedoAvailable() = itemsHistory.canRedo()

    private fun applySnapshot(json: String) {
        applyingHistory = true
        val checklistItemType = object : TypeToken<List<ChecklistItem>>() {}.type
        items = Gson().fromJson<ArrayList<ChecklistItem>>(json, checklistItemType) ?: ArrayList(1)
        items = persistedItems()
        setupAdapter()
        saveNote()
        applyingHistory = false
        notifyHistoryChanged()
    }

    override fun refreshItems() {
        loadNoteById(noteId)
        setupAdapter()
    }

    override fun migrateChecklistItems(itemIds: List<Int>) {
        MigrateChecklistItemsDialog(requireActivity() as SimpleActivity, noteId) { targetNoteId ->
            performMigration(itemIds, targetNoteId)
        }
    }

    override fun moveEntireNote() {
        (requireActivity() as? MainActivity)?.let { mainActivity ->
            // Delegate to the main activity's move note to notebook functionality
            mainActivity.onMoveNoteRequested()
        }
    }

    private fun performMigration(itemIds: List<Int>, targetNoteId: Long) {
            val itemsToMigrate = persistedItems().filter { it.id in itemIds }
        
        NotesHelper(requireActivity()).getNoteWithId(targetNoteId) { targetNote ->
            if (targetNote == null) {
                return@getNoteWithId
            }

            // Get target note's current checklist items
            val targetItems = try {
                val checklistItemType = object : TypeToken<List<ChecklistItem>>() {}.type
                Gson().fromJson<ArrayList<ChecklistItem>>(targetNote.getNoteStoredValue(requireActivity()), checklistItemType) ?: ArrayList(1)
            } catch (e: Exception) {
                ArrayList(1)
            }

            // Find max ID in target note to avoid conflicts
            val maxTargetId = targetItems.maxByOrNull { it.id }?.id ?: 0
            var newId = maxTargetId + 1

            // Add migrated items to target note with new IDs
            val migratedItems = itemsToMigrate.map { 
                ChecklistItem(newId++, it.dateCreated, it.title, it.isDone)
            }
            targetItems.addAll(migratedItems)
            targetNote.value = Gson().toJson(targetItems)

            // Save target note
            ensureBackgroundThread {
                saveNoteValue(targetNote, targetNote.value)
                
                // Remove items from current note
                activity?.runOnUiThread {
                    captureHistory()
                    items = persistedItems().filter { it.id !in itemIds }.toMutableList()
                    saveNote()
                    setupAdapter()
                    context?.updateWidgets()
                    activity?.toast(R.string.items_moved_successfully)
                }
            }
        }
    }

    private fun FragmentChecklistBinding.toCommonBinding(): CommonNoteBinding = this.let {
        object : CommonNoteBinding {
            override val root: View = it.root
            override val noteLockedLayout: View = it.noteLockedLayout
            override val noteLockedImage: ImageView = it.noteLockedImage
            override val noteLockedLabel: TextView = it.noteLockedLabel
            override val noteLockedShow: TextView = it.noteLockedShow
        }
    }
}
