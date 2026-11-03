package tomato.notes.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.search.SearchView
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.SecurityDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SHOW_ALL_TABS
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import tomato.notes.R
import tomato.notes.adapters.NotebooksAdapter
import tomato.notes.adapters.SearchResultsAdapter
import tomato.notes.databinding.ActivityNotebooksBinding
import tomato.notes.dialogs.NewNotebookDialog
import tomato.notes.dialogs.NotebookActionsDialog
import tomato.notes.dialogs.RenameNotebookDialog
import tomato.notes.extensions.config
import tomato.notes.extensions.getMutedTextColor
import tomato.notes.extensions.notesDB
import tomato.notes.extensions.notebooksDB
import tomato.notes.extensions.unlockNotebookIfNeeded
import tomato.notes.extensions.updateWidgets
import tomato.notes.helpers.OPEN_NEW_NOTE_DIALOG
import tomato.notes.helpers.NOTEBOOK_ID
import tomato.notes.helpers.NoteSearchHelper
import tomato.notes.helpers.OPEN_NOTE_ID
import tomato.notes.helpers.NotebooksHelper
import tomato.notes.helpers.NotesHelper
import tomato.notes.helpers.RecycleBinHelper
import tomato.notes.helpers.applyProperColors
import tomato.notes.helpers.showUndoSnackbar
import tomato.notes.models.Note
import tomato.notes.models.Notebook

class NotebooksActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityNotebooksBinding::inflate)
    private var adapter: NotebooksAdapter? = null
    private var searchAdapter: SearchResultsAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    // Search: the tag whose notes are listed (null for a text search), and a counter that drops stale results
    private var searchTag: String? = null
    private var searchGeneration = 0
    private var isSettingSearchText = false

    private var useSharedAxisMotion = false
    private var skipCloseMotion = false

    // Enabled only while the search is open, so otherwise back (and its predictive animation) leaves the screen
    private val searchBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeSearch()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        useSharedAxisMotion = setupSharedAxisTransitions()

        updateMaterialActivityViews(binding.notebooksCoordinator, null, useTransparentNavigation = true, useTopSearchMenu = false)
        applyBottomInsetsAsPadding(binding.notebooksList)
        applyBottomInsetsAsPadding(binding.searchResultsList)
        applyBottomInsetsAsMargin(binding.newNotebookFab)
        onBackPressedDispatcher.addCallback(this, searchBackCallback)

        binding.notebooksToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.search -> {
                    binding.notebooksSearchView.show()
                    true
                }
                R.id.switch_to_notes -> {
                    switchToNotes()
                    true
                }
                R.id.filter_by_tag -> {
                    filterByTag()
                    true
                }
                R.id.recycle_bin -> {
                    startActivity(Intent(this, RecycleBinActivity::class.java))
                    true
                }
                R.id.settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.about -> {
                    launchAbout()
                    true
                }
                else -> false
            }
        }

        binding.notebooksList.layoutManager = GridLayoutManager(this, config.notebookColumns)

        adapter = NotebooksAdapter(
            mutableListOf(),
            emptyMap(),
            itemClick = { openNotebook(it) },
            itemLongClick = { showNotebookActions(it) },
            dragStart = { viewHolder -> itemTouchHelper?.startDrag(viewHolder) },
            itemsReordered = { persistNotebookOrder(it) }
        )
        binding.notebooksList.adapter = adapter

        setupSearch()

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                return adapter?.onItemMove(fromPosition, toPosition) == true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                adapter?.onDragFinished()
            }
        })
        itemTouchHelper?.attachToRecyclerView(binding.notebooksList)

        binding.newNotebookFab.setOnClickListener {
            promptNewNotebook()
        }
        binding.notebooksEmptyAction.setOnClickListener {
            promptNewNotebook()
        }
    }

    private fun promptNewNotebook() {
        NewNotebookDialog(this) { notebook ->
            NotebooksHelper(this).insertOrUpdateNotebook(notebook) { id ->
                notebook.id = id
                openNotebookUnlocked(notebook, promptForFirstNote = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupPrimaryToolbar(binding.notebooksToolbar)

        (binding.notebooksList.layoutManager as? GridLayoutManager)?.spanCount = config.notebookColumns
        refreshNotebooks()
        // a notebook may have been locked again while the app was away, so its notes leave the results
        refreshSearchResults()
        RecycleBinHelper(this).emptyOldItems()
        updateTextColors(binding.notebooksCoordinator)
        updateSearchColors()
    }

    override fun finish() {
        super.finish()
        if (useSharedAxisMotion && !skipCloseMotion) {
            overrideSharedAxisCloseMotion()
        }
    }

    private fun switchToNotes() {
        hideKeyboard()
        config.showNotebooks = false
        startActivityWithSharedAxis(Intent(this, NotesListActivity::class.java))
        // replaced by the notes list, which slides in like a forward step
        skipCloseMotion = true
        finish()
    }

    private fun refreshNotebooks() {
        ensureBackgroundThread {
            val notebooks = notebooksDB.getNotebooks()
            val counts = notesDB.getNotes().groupingBy { it.notebookId }.eachCount()
            runOnUiThread {
                adapter?.updateItems(notebooks, counts)
                updateEmptyState(notebooks.isEmpty())
            }
        }
    }

    // Shown instead of the grid once the notebooks were loaded and there are none
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.notebooksEmptyHolder.beVisibleIf(isEmpty)
        if (isEmpty) {
            binding.notebooksEmptyIcon.setImageDrawable(resources.getColoredDrawableWithColor(R.drawable.ic_notebooks_vector, getMutedTextColor()))
            binding.notebooksEmptyText.setTextColor(getProperTextColor())
            binding.notebooksEmptyAction.applyEmptyStateButtonColors()
        }
    }

    private fun openNotebook(notebook: Notebook) {
        unlockNotebookIfNeeded(notebook) {
            openNotebookUnlocked(notebook)
        }
    }

    private fun openNotebookUnlocked(notebook: Notebook) {
        config.currentNotebookId = notebook.id ?: 0L
        Intent(this, NotesListActivity::class.java).apply {
            putExtra(NOTEBOOK_ID, config.currentNotebookId)
            startActivityWithSharedAxis(this)
        }
    }

    private fun openNotebookUnlocked(notebook: Notebook, promptForFirstNote: Boolean) {
        config.currentNotebookId = notebook.id ?: 0L
        Intent(this, NotesListActivity::class.java).apply {
            putExtra(NOTEBOOK_ID, config.currentNotebookId)
            if (promptForFirstNote) {
                putExtra(OPEN_NEW_NOTE_DIALOG, true)
            }
            startActivityWithSharedAxis(this)
        }
    }

    private fun showNotebookActions(notebook: Notebook) {
        NotebookActionsDialog(
            activity = this,
            notebook = notebook,
            onRename = {
                RenameNotebookDialog(this, notebook) {
                    refreshNotebooks()
                }
            },
            onPin = { togglePinned(notebook) },
            onLock = {
                if (notebook.isLocked()) {
                    unlockNotebook(notebook)
                } else {
                    lockNotebook(notebook)
                }
            },
            onDelete = { deleteNotebook(notebook) }
        )
    }

    private fun togglePinned(notebook: Notebook) {
        val notebookId = notebook.id ?: return
        ensureBackgroundThread {
            val newPinned = if (notebook.isPinned()) 0 else 1
            val maxSortOrder = notebooksDB.getMaxSortOrder(newPinned) ?: 0
            val newSortOrder = maxSortOrder + 1
            notebooksDB.updatePinned(notebookId, newPinned)
            notebooksDB.updateSortOrder(notebookId, newSortOrder)
            runOnUiThread {
                refreshNotebooks()
            }
        }
    }

    private fun persistNotebookOrder(notebooks: List<Notebook>) {
        ensureBackgroundThread {
            val pinnedNotebooks = notebooks.filter { it.isPinned() }
            val unpinnedNotebooks = notebooks.filterNot { it.isPinned() }

            pinnedNotebooks.forEachIndexed { index, item ->
                val id = item.id ?: return@forEachIndexed
                val newOrder = index + 1
                if (item.sortOrder != newOrder) {
                    item.sortOrder = newOrder
                    notebooksDB.updateSortOrder(id, newOrder)
                }
            }

            unpinnedNotebooks.forEachIndexed { index, item ->
                val id = item.id ?: return@forEachIndexed
                val newOrder = index + 1
                if (item.sortOrder != newOrder) {
                    item.sortOrder = newOrder
                    notebooksDB.updateSortOrder(id, newOrder)
                }
            }
        }
    }

    // A locked notebook asks for its lock first, even if it was unlocked earlier in this session.
    // With the Recycle bin on, the delete can be undone, so it happens at once and a snackbar offers Undo.
    // A permanent delete still asks first.
    private fun deleteNotebook(notebook: Notebook) {
        unlockNotebookIfNeeded(notebook, force = true) {
            deleteUnlockedNotebook(notebook)
        }
    }

    private fun deleteUnlockedNotebook(notebook: Notebook) {
        if (config.useRecycleBin) {
            doDeleteNotebook(notebook)
            return
        }

        val notebookTitle = notebook.title
        val message = String.format(getString(R.string.delete_notebook_prompt_message), notebookTitle)
        com.simplemobiletools.commons.dialogs.ConfirmationDialog(
            this,
            message,
            0,
            com.simplemobiletools.commons.R.string.ok,
            com.simplemobiletools.commons.R.string.cancel
        ) {
            doDeleteNotebook(notebook)
        }
    }

    private fun doDeleteNotebook(notebook: Notebook) {
        val notebookId = notebook.id ?: return
        val useRecycleBin = config.useRecycleBin
        RecycleBinHelper(this).deleteNotebook(notebook) {
            if (useRecycleBin) {
                showUndoSnackbar(binding.notebooksCoordinator, getString(R.string.notebook_moved_to_recycle_bin), binding.newNotebookFab) {
                    undoDeleteNotebook(notebook)
                }
            }
            if (config.currentNotebookId == notebookId) {
                ensureBackgroundThread {
                    val nextId = notebooksDB.getNotebooks().firstOrNull()?.id ?: 0L
                    config.currentNotebookId = nextId
                    runOnUiThread { refreshNotebooks() }
                }
            } else {
                refreshNotebooks()
            }
        }
    }

    // Only clears the deleted marks, so the notebook keeps its pin, order and lock, and gets back
    // exactly the notes that were trashed with it
    private fun undoDeleteNotebook(notebook: Notebook) {
        RecycleBinHelper(this).restoreNotebook(notebook) {
            if (!isFinishing && !isDestroyed) {
                refreshNotebooks()
            }
        }
    }

    private fun lockNotebook(notebook: Notebook) {
        ConfirmationDialog(
            this,
            "",
            R.string.locking_warning,
            com.simplemobiletools.commons.R.string.ok,
            com.simplemobiletools.commons.R.string.cancel
        ) {
            SecurityDialog(this, "", SHOW_ALL_TABS) { hash, type, success ->
                if (success) {
                    notebook.protectionHash = hash
                    notebook.protectionType = type
                    NotebooksHelper(this).insertOrUpdateNotebook(notebook) {
                        refreshNotebooks()
                        updateWidgets()
                    }
                }
            }
        }
    }

    private fun unlockNotebook(notebook: Notebook) {
        unlockNotebookIfNeeded(notebook, force = true) {
            removeProtection(notebook)
        }
    }

    private fun removeProtection(notebook: Notebook) {
        notebook.protectionHash = ""
        notebook.protectionType = PROTECTION_NONE
        NotebooksHelper(this).insertOrUpdateNotebook(notebook) {
            refreshNotebooks()
            updateWidgets()
        }
    }

    private fun filterByTag() {
        ensureBackgroundThread {
            val tags = NoteSearchHelper(this).allTags()
            runOnUiThread {
                if (tags.isEmpty()) {
                    toast(R.string.no_tags_yet)
                    return@runOnUiThread
                }

                val items = ArrayList<com.simplemobiletools.commons.models.RadioItem>()
                tags.forEachIndexed { index, tag ->
                    items.add(com.simplemobiletools.commons.models.RadioItem(index, tag))
                }
                com.simplemobiletools.commons.dialogs.RadioGroupDialog(this, items) {
                    showTagResults(tags[it as Int])
                }
            }
        }
    }

    // Material SearchView opened from the toolbar's search action. It shows the keyboard itself once it opens.
    private fun setupSearch() {
        searchAdapter = SearchResultsAdapter(emptyList()) { result ->
            openSearchResult(result)
        }
        binding.searchResultsList.layoutManager = LinearLayoutManager(this)
        binding.searchResultsList.adapter = searchAdapter

        binding.notebooksSearchView.apply {
            // commons already keeps the whole screen below the status bar
            setStatusBarSpacerEnabled(false)
            editText.onTextChangeListener { query ->
                if (!isSettingSearchText) {
                    searchTag = null
                    performSearch(query)
                }
            }
            addTransitionListener { _, _, newState ->
                onSearchStateChanged(newState)
            }
        }
    }

    private fun closeSearch() {
        searchGeneration++
        searchAdapter?.updateItems(emptyList())
        if (binding.notebooksSearchView.isShowing) {
            binding.notebooksSearchView.hide()
        }
    }

    private fun onSearchStateChanged(newState: SearchView.TransitionState) {
        val isOpen = newState == SearchView.TransitionState.SHOWING || newState == SearchView.TransitionState.SHOWN
        searchBackCallback.isEnabled = isOpen
        when (newState) {
            SearchView.TransitionState.SHOWING -> updateSearchColors()
            SearchView.TransitionState.HIDING -> updateStatusbarColor(getProperPrimaryColor())
            SearchView.TransitionState.HIDDEN -> {
                searchTag = null
                searchGeneration++
                setSearchText("")
                searchAdapter?.updateItems(emptyList())
                updateStatusbarColor(getProperPrimaryColor())
            }
            else -> {}
        }
    }

    private fun updateSearchColors() {
        binding.notebooksSearchView.applyProperColors()
        if (binding.notebooksSearchView.isShowing) {
            updateStatusbarColor(getProperBackgroundColor())
        }
    }

    private fun setSearchText(text: String) {
        isSettingSearchText = true
        binding.notebooksSearchView.editText.setText(text)
        binding.notebooksSearchView.editText.setSelection(text.length)
        isSettingSearchText = false
    }

    private fun showTagResults(tag: String) {
        searchTag = tag
        setSearchText(tag)
        binding.notebooksSearchView.show()
        loadTagResults(tag)
    }

    private fun loadTagResults(tag: String) {
        val generation = ++searchGeneration
        ensureBackgroundThread {
            val results = NoteSearchHelper(this).notesWithTag(tag)
            runOnUiThread {
                if (generation == searchGeneration) {
                    searchAdapter?.updateItems(results)
                }
            }
        }
    }

    private fun performSearch(query: String) {
        val generation = ++searchGeneration
        if (query.isBlank()) {
            searchAdapter?.updateItems(emptyList())
            return
        }

        ensureBackgroundThread {
            val results = NoteSearchHelper(this).search(query)
            runOnUiThread {
                if (generation == searchGeneration) {
                    searchAdapter?.updateItems(results)
                }
            }
        }
    }

    private fun refreshSearchResults() {
        if (!binding.notebooksSearchView.isShowing) {
            return
        }

        val tag = searchTag
        if (tag != null) {
            loadTagResults(tag)
        } else {
            performSearch(binding.notebooksSearchView.editText.text.toString())
        }
    }

    // The search stays open behind the opened item, so back returns to the same results
    private fun openSearchResult(result: tomato.notes.helpers.NoteSearchResult) {
        hideKeyboard()
        if (result.notebook != null) {
            openNotebook(result.notebook)
            return
        }

        val note = result.note ?: return
        NotebooksHelper(this).getNotebookWithId(note.notebookId) { notebook ->
            if (notebook == null) {
                return@getNotebookWithId
            }
            unlockNotebookIfNeeded(notebook) {
                openNote(note)
            }
        }
    }

    private fun openNote(note: Note) {
        config.currentNotebookId = note.notebookId
        Intent(this, MainActivity::class.java).apply {
            putExtra(NOTEBOOK_ID, note.notebookId)
            putExtra(OPEN_NOTE_ID, note.id)
            startActivityWithSharedAxis(this)
        }
    }
}
