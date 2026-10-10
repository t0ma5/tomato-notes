package tomato.notes.activities

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import tomato.notes.extensions.notesDB
import tomato.notes.extensions.notebooksDB
import tomato.notes.extensions.unlockNotebookIfNeeded
import tomato.notes.helpers.OPEN_NEW_NOTE_DIALOG
import tomato.notes.helpers.NOTEBOOK_ID
import tomato.notes.helpers.NoteSearchHelper
import tomato.notes.helpers.OPEN_NOTE_ID
import tomato.notes.helpers.NotebooksHelper
import tomato.notes.helpers.NotesHelper
import tomato.notes.helpers.RecycleBinHelper
import tomato.notes.models.Note
import tomato.notes.models.Notebook

class NotebooksActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityNotebooksBinding::inflate)
    private var adapter: NotebooksAdapter? = null
    private var searchAdapter: SearchResultsAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var searchVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.notebooksCoordinator, null, useTransparentNavigation = true, useTopSearchMenu = false)

        binding.notebooksToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.search -> {
                    toggleSearch()
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

        searchAdapter = SearchResultsAdapter(emptyList()) { result ->
            openSearchResult(result)
        }
        binding.searchResultsList.layoutManager = LinearLayoutManager(this)
        binding.searchResultsList.adapter = searchAdapter
        binding.notebooksSearch.onTextChangeListener { query ->
            performSearch(query)
        }

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
            NewNotebookDialog(this) { notebook ->
                NotebooksHelper(this).insertOrUpdateNotebook(notebook) { id ->
                    notebook.id = id
                    openNotebookUnlocked(notebook, promptForFirstNote = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupPrimaryToolbar(binding.notebooksToolbar)

        (binding.notebooksList.layoutManager as? GridLayoutManager)?.spanCount = config.notebookColumns
        refreshNotebooks()
        RecycleBinHelper(this).emptyOldItems()
        updateTextColors(binding.notebooksCoordinator)
    }

    private fun switchToNotes() {
        if (searchVisible) {
            toggleSearch()
        }
        hideKeyboard()
        config.showNotebooks = false
        startActivity(Intent(this, NotesListActivity::class.java))
        finish()
    }

    private fun refreshNotebooks() {
        ensureBackgroundThread {
            NotesHelper(this).cleanupPlaceholderNotesSync()
            val notebooks = notebooksDB.getNotebooks()
            val counts = notesDB.getNotes().groupingBy { it.notebookId }.eachCount()
            runOnUiThread {
                adapter?.updateItems(notebooks, counts)
            }
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
            startActivity(this)
        }
    }

    private fun openNotebookUnlocked(notebook: Notebook, promptForFirstNote: Boolean) {
        config.currentNotebookId = notebook.id ?: 0L
        Intent(this, NotesListActivity::class.java).apply {
            putExtra(NOTEBOOK_ID, config.currentNotebookId)
            if (promptForFirstNote) {
                putExtra(OPEN_NEW_NOTE_DIALOG, true)
            }
            startActivity(this)
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

    private fun deleteNotebook(notebook: Notebook) {
        val notebookTitle = notebook.title
        val message = String.format(getString(R.string.delete_notebook_prompt_message), notebookTitle)
        com.simplemobiletools.commons.dialogs.ConfirmationDialog(
            this,
            message,
            0,
            com.simplemobiletools.commons.R.string.ok,
            com.simplemobiletools.commons.R.string.cancel
        ) {
            val notebookId = notebook.id ?: return@ConfirmationDialog
            RecycleBinHelper(this).deleteNotebook(notebook) {
                if (config.useRecycleBin) {
                    toast(R.string.moved_to_recycle_bin)
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
                    }
                }
            }
        }
    }

    private fun unlockNotebook(notebook: Notebook) {
        unlockNotebookIfNeeded(notebook) {
            removeProtection(notebook)
        }
    }

    private fun removeProtection(notebook: Notebook) {
        notebook.protectionHash = ""
        notebook.protectionType = PROTECTION_NONE
        NotebooksHelper(this).insertOrUpdateNotebook(notebook) {
            refreshNotebooks()
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
                    val tag = tags[it as Int]
                    searchVisible = true
                    binding.notebooksSearch.beVisible()
                    binding.notebooksSearch.setText(tag)
                    ensureBackgroundThread {
                        val results = NoteSearchHelper(this).notesWithTag(tag)
                        runOnUiThread {
                            searchAdapter?.updateItems(results)
                            binding.notebooksList.beGone()
                            binding.searchResultsList.beVisible()
                            binding.newNotebookFab.beGone()
                        }
                    }
                }
            }
        }
    }

    private fun toggleSearch() {
        searchVisible = !searchVisible
        binding.notebooksSearch.beVisibleIf(searchVisible)
        if (searchVisible) {
            binding.notebooksSearch.requestFocus()
            showKeyboard(binding.notebooksSearch)
        } else {
            hideKeyboard()
            binding.notebooksSearch.setText("")
            showNotebooksList()
        }
    }

    private fun performSearch(query: String) {
        if (!searchVisible || query.isBlank()) {
            showNotebooksList()
            return
        }

        ensureBackgroundThread {
            val results = NoteSearchHelper(this).search(query)
            runOnUiThread {
                searchAdapter?.updateItems(results)
                binding.notebooksList.beGone()
                binding.searchResultsList.beVisible()
                binding.newNotebookFab.beGone()
            }
        }
    }

    private fun showNotebooksList() {
        binding.searchResultsList.beGone()
        binding.notebooksList.beVisible()
        binding.newNotebookFab.beVisible()
        searchAdapter?.updateItems(emptyList())
    }

    private fun openSearchResult(result: tomato.notes.helpers.NoteSearchResult) {
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
            startActivity(this)
        }
    }
}
