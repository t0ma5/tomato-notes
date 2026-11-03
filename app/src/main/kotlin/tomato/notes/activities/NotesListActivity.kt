package tomato.notes.activities

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.search.SearchView
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.getProperBackgroundColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.extensions.onTextChangeListener
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.RadioItem
import tomato.notes.R
import tomato.notes.adapters.NotesListAdapter
import tomato.notes.adapters.SearchResultsAdapter
import tomato.notes.databinding.ActivityNotesListBinding
import tomato.notes.dialogs.NewNoteDialog
import tomato.notes.extensions.config
import tomato.notes.extensions.notesDB
import tomato.notes.extensions.notebooksDB
import tomato.notes.extensions.unlockNotebookIfNeeded
import tomato.notes.helpers.NOTEBOOK_ID
import tomato.notes.helpers.NoteSearchHelper
import tomato.notes.helpers.NoteSearchResult
import tomato.notes.helpers.NotebookUnlockSession
import tomato.notes.helpers.NotesHelper
import tomato.notes.helpers.NotebooksHelper
import tomato.notes.helpers.OPEN_NEW_NOTE_DIALOG
import tomato.notes.helpers.OPEN_NOTE_ID
import tomato.notes.helpers.RecycleBinHelper
import tomato.notes.helpers.applyProperColors
import tomato.notes.models.Note
import tomato.notes.models.Notebook

class NotesListActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityNotesListBinding::inflate)
    private var adapter: NotesListAdapter? = null
    private var searchAdapter: SearchResultsAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var filterNotebookId = 0L
    private var pendingNewNotePrompt = false
    private var notebooksById = emptyMap<Long, Notebook>()

    // Notebook passed in NOTEBOOK_ID, its notes are listed only once its lock was passed
    private var filterNotebook: Notebook? = null
    private var isFilterNotebookUnlocked = false
    private var isUnlockPromptShowing = false

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
        updateMaterialActivityViews(binding.notesListCoordinator, null, useTransparentNavigation = true, useTopSearchMenu = false)
        applyBottomInsetsAsPadding(binding.notesList)
        applyBottomInsetsAsPadding(binding.searchResultsList)
        applyBottomInsetsAsMargin(binding.newNoteFab)
        onBackPressedDispatcher.addCallback(this, searchBackCallback)
        applyIntent(intent)

        binding.notesListToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.search -> {
                    openSearch()
                    true
                }
                R.id.switch_to_notebooks -> {
                    if (filterNotebookId > 0L) {
                        config.showNotebooks = false
                        startActivityWithSharedAxis(Intent(this, NotesListActivity::class.java))
                        finishWithoutCloseMotion()
                    } else {
                        switchToNotebooks()
                    }
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

        binding.notesList.layoutManager = GridLayoutManager(this, config.notebookColumns)
        adapter = NotesListAdapter(
            mutableListOf(),
            emptyMap(),
            showNotebookName = true,
            itemClick = { openNote(it) },
            itemsReordered = { persistNoteOrder(it) }
        )
        binding.notesList.adapter = adapter
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun isLongPressDragEnabled() = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                return adapter?.onItemMove(fromPosition, toPosition) == true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                adapter?.onDragFinished()
            }
        })
        itemTouchHelper?.attachToRecyclerView(binding.notesList)

        setupSearch()

        binding.newNoteFab.setOnClickListener {
            promptNewNote()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
        // onResume follows and reloads the list once the notebook's lock (if any) was passed
    }

    override fun onResume() {
        super.onResume()
        setupPrimaryToolbar(
            binding.notesListToolbar,
            if (filterNotebookId > 0L) NavigationIcon.Arrow else NavigationIcon.None
        )
        binding.notesListToolbar.menu.findItem(R.id.switch_to_notebooks)?.apply {
            if (filterNotebookId > 0L) {
                setIcon(R.drawable.ic_notes_vector)
                title = getString(R.string.switch_to_notes)
            } else {
                setIcon(R.drawable.ic_notebooks_vector)
                title = getString(R.string.switch_to_notebooks)
            }
        }
        (binding.notesList.layoutManager as? GridLayoutManager)?.spanCount = config.notebookColumns
        ensureFilterNotebookUnlocked {
            val shouldPrompt = pendingNewNotePrompt
            pendingNewNotePrompt = false
            refreshNotes {
                if (shouldPrompt) {
                    promptNewNote()
                }
            }
            // a notebook may have been locked again while the app was away, so its notes leave the results
            refreshSearchResults()
        }
        RecycleBinHelper(this).emptyOldItems()
        updateTextColors(binding.notesListCoordinator)
        updateSearchColors()
    }

    override fun finish() {
        super.finish()
        if (useSharedAxisMotion && !skipCloseMotion) {
            overrideSharedAxisCloseMotion()
        }
    }

    // Replaces this screen with another one, which slides in like a forward step
    private fun finishWithoutCloseMotion() {
        skipCloseMotion = true
        finish()
    }

    private fun persistNoteOrder(notes: List<Note>) {
        ensureBackgroundThread {
            val orders = notes.map { it.sortOrder }.sorted()
            notes.forEachIndexed { index, item ->
                val id = item.id ?: return@forEachIndexed
                val newOrder = orders.getOrElse(index) { index + 1 }
                if (item.sortOrder != newOrder) {
                    item.sortOrder = newOrder
                    notesDB.updateSortOrder(id, newOrder)
                }
            }
        }
    }

    private fun applyIntent(intent: Intent) {
        // the notebook widget may send 0 (or a negative id) when its notebook is gone, list all notes then
        val newNotebookId = intent.getLongExtra(NOTEBOOK_ID, 0L).coerceAtLeast(0L)
        if (newNotebookId != filterNotebookId) {
            filterNotebookId = newNotebookId
            filterNotebook = null
            isFilterNotebookUnlocked = false
            adapter?.updateItems(emptyList(), emptyMap(), showNotebook = filterNotebookId == 0L)
            closeSearch()
        }
        pendingNewNotePrompt = intent.getBooleanExtra(OPEN_NEW_NOTE_DIALOG, false)
        intent.removeExtra(OPEN_NEW_NOTE_DIALOG)
    }

    // Lists a locked notebook only after its lock was passed. An unlock earlier in this session (from the
    // notebooks screen) counts; once the app went to the background it is asked again. Cancel closes the screen.
    private fun ensureFilterNotebookUnlocked(onUnlocked: () -> Unit) {
        val notebookId = filterNotebookId
        if (notebookId <= 0L) {
            setSecureContent(false)
            onUnlocked()
            return
        }

        NotebooksHelper(this).getNotebookWithId(notebookId) { notebook ->
            if (isFinishing || isDestroyed || notebookId != filterNotebookId) {
                return@getNotebookWithId
            }

            if (notebook == null) {
                // deleted notebook, fall back to all notes
                filterNotebookId = 0L
                filterNotebook = null
                isFilterNotebookUnlocked = false
                setSecureContent(false)
                onUnlocked()
                return@getNotebookWithId
            }

            filterNotebook = notebook
            setSecureContent(notebook.isLocked())
            if (NotebookUnlockSession.canShow(notebook)) {
                isFilterNotebookUnlocked = true
                onUnlocked()
                return@getNotebookWithId
            }

            isFilterNotebookUnlocked = false
            adapter?.updateItems(emptyList(), emptyMap(), showNotebook = false)
            closeSearch()

            if (!isUnlockPromptShowing) {
                isUnlockPromptShowing = true
                unlockNotebookIfNeeded(notebook, onCancel = {
                    isUnlockPromptShowing = false
                    finish()
                }) {
                    isUnlockPromptShowing = false
                    isFilterNotebookUnlocked = true
                    onUnlocked()
                }
            }
        }
    }

    private fun canShowFilteredContent() = filterNotebookId <= 0L || isFilterNotebookUnlocked

    private fun switchToNotebooks() {
        hideKeyboard()
        config.showNotebooks = true
        startActivityWithSharedAxis(Intent(this, NotebooksActivity::class.java))
        finishWithoutCloseMotion()
    }

    private fun refreshNotes(onDone: (() -> Unit)? = null) {
        if (!canShowFilteredContent()) {
            return
        }

        ensureBackgroundThread {
            val notebooks = notebooksDB.getNotebooks()
            notebooksById = notebooks.associateBy { it.id ?: 0L }
            val notes = if (filterNotebookId > 0L) {
                notesDB.getNotesInNotebook(filterNotebookId)
            } else {
                notesDB.getNotes()
            }
            runOnUiThread {
                if (!canShowFilteredContent()) {
                    return@runOnUiThread
                }
                updateTitle()
                adapter?.updateItems(notes, notebooksById, showNotebook = filterNotebookId == 0L)
                onDone?.invoke()
            }
        }
    }

    private fun updateTitle() {
        val notebookTitle = notebooksById[filterNotebookId]?.title
        binding.notesListToolbar.title = if (filterNotebookId > 0L) {
            notebookTitle ?: getString(R.string.notebooks)
        } else {
            getString(R.string.app_launcher_name)
        }

        binding.notesListSearchView.setHint(
            if (filterNotebookId > 0L && notebookTitle != null) {
                getString(R.string.search_in_notebook, notebookTitle)
            } else {
                getString(R.string.search_notes_hint)
            }
        )
    }

    private fun promptNewNote() {
        val forcedNotebookId = filterNotebookId
        if (forcedNotebookId > 0L) {
            val notebook = notebooksById[forcedNotebookId]
            unlockNotebookIfNeeded(notebook) {
                showNewNoteDialog(forcedNotebookId)
            }
            return
        }

        NotesHelper(this).ensureAtLeastOneNotebook { notebookId ->
            NotebooksHelper(this).getNotebooks { notebooks ->
                when {
                    notebooks.size <= 1 -> showNewNoteDialog(notebooks.firstOrNull()?.id ?: notebookId)
                    else -> {
                        val items = ArrayList<RadioItem>()
                        notebooks.forEachIndexed { index, notebook ->
                            items.add(RadioItem(index, notebook.title))
                        }
                        RadioGroupDialog(this, items, -1, R.string.select_target_notebook) {
                            val notebook = notebooks[it as Int]
                            unlockNotebookIfNeeded(notebook) {
                                showNewNoteDialog(notebook.id!!)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showNewNoteDialog(notebookId: Long) {
        config.currentNotebookId = notebookId
        NewNoteDialog(
            activity = this,
            setChecklistAsDefault = false,
            notebookId = notebookId,
            callback = { note ->
                NotesHelper(this).insertOrUpdateNote(note) { newId ->
                    note.id = newId
                    openNote(note)
                }
            }
        )
    }

    private fun openNote(note: Note) {
        val notebook = notebooksById[note.notebookId]
        unlockNotebookIfNeeded(notebook) {
            openNoteUnlocked(note)
        }
    }

    private fun openNoteUnlocked(note: Note) {
        config.currentNotebookId = note.notebookId
        config.currentNoteId = note.id ?: 0L
        Intent(this, MainActivity::class.java).apply {
            putExtra(NOTEBOOK_ID, note.notebookId)
            putExtra(OPEN_NOTE_ID, note.id)
            startActivityWithSharedAxis(this)
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

    private fun filterByTag() {
        if (!canShowFilteredContent()) {
            return
        }

        ensureBackgroundThread {
            val tags = NoteSearchHelper(this).allTags()
            runOnUiThread {
                if (tags.isEmpty()) {
                    toast(R.string.no_tags_yet)
                    return@runOnUiThread
                }

                val items = ArrayList<RadioItem>()
                tags.forEachIndexed { index, tag ->
                    items.add(RadioItem(index, tag))
                }
                RadioGroupDialog(this, items) {
                    showTagResults(tags[it as Int])
                }
            }
        }
    }

    private fun setupSearch() {
        searchAdapter = SearchResultsAdapter(emptyList()) { result ->
            openSearchResult(result)
        }
        binding.searchResultsList.layoutManager = LinearLayoutManager(this)
        binding.searchResultsList.adapter = searchAdapter

        binding.notesListSearchView.apply {
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

    // The SearchView shows the keyboard itself once it is open
    private fun openSearch() {
        if (canShowFilteredContent()) {
            binding.notesListSearchView.show()
        }
    }

    private fun closeSearch() {
        searchGeneration++
        searchAdapter?.updateItems(emptyList())
        if (binding.notesListSearchView.isShowing) {
            binding.notesListSearchView.hide()
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
        binding.notesListSearchView.applyProperColors()
        if (binding.notesListSearchView.isShowing) {
            updateStatusbarColor(getProperBackgroundColor())
        }
    }

    private fun setSearchText(text: String) {
        isSettingSearchText = true
        binding.notesListSearchView.editText.setText(text)
        binding.notesListSearchView.editText.setSelection(text.length)
        isSettingSearchText = false
    }

    private fun showTagResults(tag: String) {
        if (!canShowFilteredContent()) {
            return
        }

        searchTag = tag
        setSearchText(tag)
        binding.notesListSearchView.show()
        loadTagResults(tag)
    }

    private fun loadTagResults(tag: String) {
        val generation = ++searchGeneration
        ensureBackgroundThread {
            val results = NoteSearchHelper(this).notesWithTag(tag).filterForCurrentNotebook()
            runOnUiThread {
                if (generation == searchGeneration && canShowFilteredContent()) {
                    searchAdapter?.updateItems(results)
                }
            }
        }
    }

    private fun performSearch(query: String) {
        val generation = ++searchGeneration
        if (query.isBlank() || !canShowFilteredContent()) {
            searchAdapter?.updateItems(emptyList())
            return
        }

        ensureBackgroundThread {
            val results = NoteSearchHelper(this).search(query).filterForCurrentNotebook()
            runOnUiThread {
                if (generation == searchGeneration && canShowFilteredContent()) {
                    searchAdapter?.updateItems(results)
                }
            }
        }
    }

    private fun refreshSearchResults() {
        if (!binding.notesListSearchView.isShowing) {
            return
        }

        val tag = searchTag
        if (tag != null) {
            loadTagResults(tag)
        } else {
            performSearch(binding.notesListSearchView.editText.text.toString())
        }
    }

    private fun List<NoteSearchResult>.filterForCurrentNotebook(): List<NoteSearchResult> {
        if (filterNotebookId <= 0L) {
            return this
        }
        return filter { result ->
            result.notebook?.id == filterNotebookId || result.note?.notebookId == filterNotebookId
        }
    }

    // The search stays open behind the opened item, so back returns to the same results
    private fun openSearchResult(result: NoteSearchResult) {
        hideKeyboard()
        if (result.notebook != null) {
            openNotebook(result.notebook)
            return
        }
        val note = result.note ?: return
        openNote(note)
    }
}

// Material shared axis X motion between the notebooks, notes list and note editor screens. Before API 34 the
// caller sets it per call; from API 34 the opened screen sets it once with overrideActivityTransition, which
// also drives the predictive back animation. It is skipped while the system animations are turned off.
private const val SHARED_AXIS_MOTION = "shared_axis_motion"

internal fun Activity.areSystemAnimationsEnabled() =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

internal fun Activity.startActivityWithSharedAxis(intent: Intent) {
    val animate = areSystemAnimationsEnabled()
    if (animate) {
        intent.putExtra(SHARED_AXIS_MOTION, true)
    }

    startActivity(intent)
    if (animate && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.shared_axis_x_forward_enter, R.anim.shared_axis_x_forward_exit)
    }
}

// Call from onCreate (and onNewIntent for reused screens). Returns whether the screen was opened with the
// shared axis motion, then its finish() should call overrideSharedAxisCloseMotion().
internal fun Activity.setupSharedAxisTransitions(): Boolean {
    val useMotion = intent?.getBooleanExtra(SHARED_AXIS_MOTION, false) == true && areSystemAnimationsEnabled()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        if (useMotion) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.shared_axis_x_forward_enter, R.anim.shared_axis_x_forward_exit)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.shared_axis_x_backward_enter, R.anim.shared_axis_x_backward_exit)
        } else {
            // a reused screen reached from a widget or shortcut keeps the system transitions
            clearOverrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN)
            clearOverrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE)
        }
    }
    return useMotion
}

// Before API 34 the close motion is set right after finish(); from API 34 setupSharedAxisTransitions did it
internal fun Activity.overrideSharedAxisCloseMotion() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && areSystemAnimationsEnabled()) {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.shared_axis_x_backward_enter, R.anim.shared_axis_x_backward_exit)
    }
}
