package tomato.notes.activities

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.search.SearchView
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.deleteFile
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.commons.extensions.getProperBackgroundColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.extensions.onTextChangeListener
import com.simplemobiletools.commons.extensions.performSecurityCheck
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.models.RadioItem
import tomato.notes.R
import tomato.notes.adapters.NotesListAdapter
import tomato.notes.adapters.SearchResultsAdapter
import tomato.notes.databinding.ActivityNotesListBinding
import tomato.notes.dialogs.DeleteNoteDialog
import tomato.notes.dialogs.NewNoteDialog
import tomato.notes.extensions.config
import tomato.notes.extensions.getMutedTextColor
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
import tomato.notes.helpers.showUndoSnackbar
import tomato.notes.models.Note
import tomato.notes.models.NoteType
import tomato.notes.models.Notebook

class NotesListActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityNotesListBinding::inflate)
    private var adapter: NotesListAdapter? = null
    private var searchAdapter: SearchResultsAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var filterNotebookId = 0L
    private var pendingNewNotePrompt = false
    private var notebooksById = emptyMap<Long, Notebook>()

    // Every note of the current scope (one notebook or all), pinned first; the grid shows those passing the filter chip
    private var allNotes = emptyList<Note>()
    private var hasLoadedNotes = false

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
            itemsReordered = { persistNoteOrder(it) },
            pinToggle = { togglePinned(it) },
            deleteNote = { requestDeleteNote(it, swipedPosition = null) }
        )
        binding.notesList.adapter = adapter
        itemTouchHelper = ItemTouchHelper(NoteCardTouchCallback())
        itemTouchHelper?.attachToRecyclerView(binding.notesList)

        setupFilterChips()
        setupSearch()

        binding.notesEmptyAction.setOnClickListener {
            if (activeFilter() != NoteFilter.ALL) {
                binding.notesFilterChips.check(R.id.notes_filter_all)
            } else {
                promptNewNote()
            }
        }

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
        updateFilterChipColors()
        updateEmptyState()
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
            clearShownNotes()
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
            clearShownNotes()
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
                // pinned notes first, each group keeps its own order (the sort is stable)
                allNotes = notes.sortedByDescending { it.isPinned() }
                hasLoadedNotes = true
                showNotes()
                onDone?.invoke()
            }
        }
    }

    // Empties the grid while a locked notebook waits for its lock, without showing the empty state
    private fun clearShownNotes() {
        allNotes = emptyList()
        hasLoadedNotes = false
        adapter?.updateItems(emptyList(), emptyMap(), showNotebook = filterNotebookId == 0L)
        binding.notesFilterScroll.beGone()
        binding.notesEmptyHolder.beGone()
    }

    private fun showNotes() {
        val chipFilter = activeFilter()
        binding.notesFilterScroll.beVisibleIf(allNotes.size >= 2)
        adapter?.updateItems(allNotes.filter { it.matchesFilter(chipFilter) }, notebooksById, showNotebook = filterNotebookId == 0L)
        updateEmptyState()
    }

    // The chip row (and so its filter) applies only while it is shown, from 2 notes on
    private fun activeFilter() = if (allNotes.size >= 2) sessionFilter else NoteFilter.ALL

    private fun Note.matchesFilter(filter: NoteFilter) = when (filter) {
        NoteFilter.ALL -> true
        NoteFilter.PINNED -> isPinned()
        NoteFilter.CHECKLISTS -> type == NoteType.TYPE_CHECKLIST
        NoteFilter.LOCKED -> isLocked()
    }

    private fun filterChipIds() = mapOf(
        R.id.notes_filter_all to NoteFilter.ALL,
        R.id.notes_filter_pinned to NoteFilter.PINNED,
        R.id.notes_filter_checklists to NoteFilter.CHECKLISTS,
        R.id.notes_filter_locked to NoteFilter.LOCKED
    )

    private fun setupFilterChips() {
        val chipIds = filterChipIds()
        val selectedId = chipIds.entries.firstOrNull { it.value == sessionFilter }?.key ?: R.id.notes_filter_all
        binding.notesFilterChips.check(selectedId)
        binding.notesFilterChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            if (checkedId == null) {
                // "All" stands for no filter, so unchecking the selected chip falls back to it
                group.check(R.id.notes_filter_all)
                return@setOnCheckedStateChangeListener
            }

            val filter = chipIds[checkedId] ?: NoteFilter.ALL
            if (filter != sessionFilter) {
                sessionFilter = filter
                showNotes()
                refreshSearchResults()
            }
        }
    }

    // Solid commons colors, so the chips suit both the custom themes and System default (Material You)
    private fun updateFilterChipColors() {
        val primary = getProperPrimaryColor()
        val onPrimary = primary.getContrastColor()
        val textColor = getProperTextColor()
        val background = getProperBackgroundColor()
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val chipBackground = ColorStateList(states, intArrayOf(primary, background))
        val chipStroke = ColorStateList(states, intArrayOf(primary, ColorUtils.blendARGB(background, textColor, 0.35f)))
        val chipText = ColorStateList(states, intArrayOf(onPrimary, textColor))
        val strokeWidth = resources.displayMetrics.density
        filterChipIds().keys.forEach { id ->
            binding.notesFilterChips.findViewById<Chip>(id)?.apply {
                chipBackgroundColor = chipBackground
                chipStrokeColor = chipStroke
                chipStrokeWidth = strokeWidth
                setTextColor(chipText)
                checkedIconTint = ColorStateList.valueOf(onPrimary)
            }
        }
    }

    // Shown once the notes were loaded and nothing passes: "New note" when there are no notes at all,
    // "Clear filter" when only the filter chip left nothing
    private fun updateEmptyState() {
        val isEmpty = hasLoadedNotes && canShowFilteredContent() && adapter?.itemCount == 0
        binding.notesEmptyHolder.beVisibleIf(isEmpty)
        if (!isEmpty) {
            return
        }

        val isFiltered = allNotes.isNotEmpty() && activeFilter() != NoteFilter.ALL
        val iconId = if (isFiltered) com.simplemobiletools.commons.R.drawable.ic_filter_vector else R.drawable.ic_notes_vector
        binding.notesEmptyIcon.setImageDrawable(resources.getColoredDrawableWithColor(iconId, getMutedTextColor()))
        binding.notesEmptyText.setText(
            when {
                isFiltered -> R.string.list_no_matching_notes
                filterNotebookId > 0L -> R.string.list_empty_notebook
                else -> R.string.list_empty_notes
            }
        )
        binding.notesEmptyText.setTextColor(getProperTextColor())
        binding.notesEmptyAction.setText(if (isFiltered) R.string.list_clear_filter else R.string.list_new_note)
        binding.notesEmptyAction.applyEmptyStateButtonColors()
    }

    private fun togglePinned(note: Note) {
        val noteId = note.id ?: return
        val newPinned = if (note.isPinned()) 0 else 1
        ensureBackgroundThread {
            notesDB.updatePinned(noteId, newPinned)
            runOnUiThread {
                refreshNotes()
            }
        }
    }

    // Swipe left and the TalkBack Delete action. The same checks as deleting from the note screen: the notebook's
    // lock, then the note's own lock. With the Recycle bin on, a plain note goes at once and a snackbar offers Undo;
    // a permanent delete, or a file-linked note, asks first. A swiped card comes back whenever a prompt shows,
    // so cancelling leaves it in place.
    private fun requestDeleteNote(note: Note, swipedPosition: Int?) {
        val notebook = notebooksById[note.notebookId]
        val needsNotebookUnlock = notebook?.isLocked() == true && !NotebookUnlockSession.isUnlocked(notebook.id)
        val deletesAtOnce = config.useRecycleBin && note.path.isEmpty()
        if (swipedPosition != null) {
            if (deletesAtOnce && !needsNotebookUnlock && !note.isLocked()) {
                adapter?.removeNoteAt(swipedPosition)
            } else {
                adapter?.notifyItemChanged(swipedPosition)
            }
        }

        unlockNotebookIfNeeded(notebook) {
            unlockNoteIfNeeded(note) {
                if (deletesAtOnce) {
                    doDeleteNote(note, deleteFile = false)
                } else {
                    DeleteNoteDialog(this, note) { deleteFile ->
                        if (deleteFile) {
                            handleSAFDialog(note.path) { success ->
                                if (success) {
                                    doDeleteNote(note, deleteFile = true)
                                }
                            }
                        } else {
                            doDeleteNote(note, deleteFile = false)
                        }
                    }
                }
            }
        }
    }

    private fun unlockNoteIfNeeded(note: Note, callback: () -> Unit) {
        if (!note.isLocked()) {
            callback()
            return
        }

        performSecurityCheck(
            protectionType = note.protectionType,
            requiredHash = note.protectionHash,
            successCallback = { _, _ -> callback() }
        )
    }

    private fun doDeleteNote(note: Note, deleteFile: Boolean) {
        val useRecycleBin = config.useRecycleBin
        RecycleBinHelper(this).deleteNote(note) {
            if (useRecycleBin && !deleteFile) {
                showUndoSnackbar(binding.notesListCoordinator, getString(R.string.note_moved_to_recycle_bin), binding.newNoteFab) {
                    undoDeleteNote(note)
                }
            } else if (useRecycleBin) {
                toast(R.string.moved_to_recycle_bin)
            }
            // a trashed note keeps its checklist sorting, so it comes back as it was
            if (!useRecycleBin) {
                note.id?.let { config.removeChecklistSorting(it) }
            }
            if (deleteFile) {
                deleteFile(FileDirItem(note.path, note.title)) {
                    if (!it) {
                        toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                    }
                }
            }
            refreshNotes()
        }
    }

    // Only clears the note's deleted mark, so its notebook, order, pin and lock stay as they were
    private fun undoDeleteNote(note: Note) {
        RecycleBinHelper(this).restoreNote(note) {
            if (!isFinishing && !isDestroyed) {
                refreshNotes()
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
        val chipFilter = activeFilter()
        ensureBackgroundThread {
            val results = NoteSearchHelper(this).notesWithTag(tag).filterForCurrentNotebook().filterForChip(chipFilter)
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

        val chipFilter = activeFilter()
        ensureBackgroundThread {
            val results = NoteSearchHelper(this).search(query).filterForCurrentNotebook().filterForChip(chipFilter)
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

    // A selected filter chip narrows the search too; notebook matches are left out then, as they are no notes
    private fun List<NoteSearchResult>.filterForChip(chipFilter: NoteFilter): List<NoteSearchResult> {
        if (chipFilter == NoteFilter.ALL) {
            return this
        }
        return filter { result -> result.note?.matchesFilter(chipFilter) == true }
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

    // Long-press drags a card to reorder it; a swipe right pins or unpins it, a swipe left deletes it.
    // While swiping, the uncovered space shows the action's color and icon under the card.
    private inner class NoteCardTouchCallback : ItemTouchHelper.Callback() {
        private val density = resources.displayMetrics.density
        private val cornerRadius = 10 * density     // same as the card background, see applyNoteCardBackground
        private val iconSize = (24 * density).toInt()
        private val iconMargin = (20 * density).toInt()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val icons = HashMap<Pair<Int, Int>, Drawable>()
        private var orderChanged = false

        override fun isLongPressDragEnabled() = true

        override fun isItemViewSwipeEnabled() = true

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            return makeMovementFlags(dragFlags, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            val moved = adapter?.onItemMove(fromPosition, toPosition) == true
            orderChanged = orderChanged || moved
            return moved
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val note = adapter?.getNoteAt(position)
            if (note == null) {
                if (position != RecyclerView.NO_POSITION) {
                    adapter?.notifyItemChanged(position)
                }
                return
            }

            if (direction == ItemTouchHelper.RIGHT) {
                // the card comes back at once, the refresh then moves it among the pinned or unpinned notes
                adapter?.notifyItemChanged(position)
                togglePinned(note)
            } else {
                requestDeleteNote(note, position)
            }
        }

        // Called after both drags and swipes, the order is saved only when a drag moved something
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (orderChanged) {
                orderChanged = false
                adapter?.onDragFinished()
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                drawSwipeBackground(c, viewHolder, dX)
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }

        // A rounded rect in the card's own bounds, clipped to the uncovered side plus one corner radius,
        // so it also fills in behind the moving card's rounded corners
        private fun drawSwipeBackground(c: Canvas, viewHolder: RecyclerView.ViewHolder, dX: Float) {
            val holder = viewHolder as? NotesListAdapter.ViewHolder ?: return
            val itemView = holder.itemView
            val card = holder.binding.noteCardBody
            val left = itemView.left + card.left.toFloat()
            val top = itemView.top + card.top.toFloat()
            val right = itemView.left + card.right.toFloat()
            val bottom = itemView.top + card.bottom.toFloat()
            val isPin = dX > 0f
            val color = if (isPin) {
                getProperPrimaryColor()
            } else {
                resources.getColor(com.simplemobiletools.commons.R.color.md_red_700, theme)
            }

            c.save()
            if (isPin) {
                c.clipRect(left, top, left + dX + cornerRadius, bottom)
            } else {
                c.clipRect(right + dX - cornerRadius, top, right, bottom)
            }
            paint.color = color
            rect.set(left, top, right, bottom)
            c.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            val iconId = if (isPin) R.drawable.ic_pin_vector else com.simplemobiletools.commons.R.drawable.ic_delete_vector
            val iconColor = if (isPin) color.getContrastColor() else Color.WHITE
            val icon = icons.getOrPut(iconId to iconColor) { resources.getColoredDrawableWithColor(iconId, iconColor) }
            val iconTop = (top + (bottom - top - iconSize) / 2).toInt()
            val iconLeft = if (isPin) left.toInt() + iconMargin else right.toInt() - iconMargin - iconSize
            icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            icon.draw(c)
            c.restore()
        }
    }

    private enum class NoteFilter { ALL, PINNED, CHECKLISTS, LOCKED }

    companion object {
        // The selected filter chip lasts for the app session only, it is never saved
        private var sessionFilter = NoteFilter.ALL
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

// Filled button of the empty states, in the commons primary color
internal fun MaterialButton.applyEmptyStateButtonColors() {
    val primary = context.getProperPrimaryColor()
    backgroundTintList = ColorStateList.valueOf(primary)
    setTextColor(primary.getContrastColor())
}

// Before API 34 the close motion is set right after finish(); from API 34 setupSharedAxisTransitions did it
internal fun Activity.overrideSharedAxisCloseMotion() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && areSystemAnimationsEnabled()) {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.shared_axis_x_backward_enter, R.anim.shared_axis_x_backward_exit)
    }
}
