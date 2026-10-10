package tomato.notes.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import tomato.notes.R
import tomato.notes.activities.SimpleActivity
import tomato.notes.adapters.CounterAdapter
import tomato.notes.databinding.FragmentCounterBinding
import tomato.notes.dialogs.NewCounterItemDialog
import tomato.notes.extensions.updateWidgets
import tomato.notes.helpers.NOTE_ID
import tomato.notes.helpers.NotesHelper
import tomato.notes.models.CounterItem
import tomato.notes.models.Note
import tomato.notes.models.SnapshotHistory
import java.io.File

class CounterFragment : NoteFragment() {

    private var noteId = 0L
    private val itemsHistory = SnapshotHistory()
    private var applyingHistory = false

    private lateinit var binding: FragmentCounterBinding

    var items = mutableListOf<CounterItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCounterBinding.inflate(inflater, container, false)
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
        }
    }

    private fun setupFragmentColors() {
        val adjustedPrimaryColor = requireActivity().getProperPrimaryColor()
        binding.counterFab.apply {
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

    private fun loadNoteById(noteId: Long) {
        NotesHelper(requireActivity()).getNoteWithId(noteId) { storedNote ->
            if (storedNote != null && activity?.isDestroyed == false) {
                note = storedNote
                items = parseCounterItems(storedNote)
                setupFragment()
            }
        }
    }

    private fun parseCounterItems(note: Note): MutableList<CounterItem> {
        return try {
            val counterItemType = object : TypeToken<List<CounterItem>>() {}.type
            Gson().fromJson<ArrayList<CounterItem>>(note.getNoteStoredValue(requireActivity()), counterItemType) ?: ArrayList(1)
        } catch (e: Exception) {
            ArrayList(1)
        }
    }

    private fun setupFragment() {
        if (activity == null || requireActivity().isFinishing) {
            return
        }

        setupFragmentColors()
        checkLockState()
        setupAdapter()
    }

    override fun checkLockState() {
        if (note == null) {
            return
        }

        binding.apply {
            val canEdit = (!note!!.isLocked() || shouldShowLockedContent) && !note!!.isReadOnly
            counterContentHolder.beVisibleIf(!note!!.isLocked() || shouldShowLockedContent)
            counterFab.beVisibleIf(canEdit)
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

    private fun updateUIVisibility() {
        binding.apply {
            fragmentPlaceholder.beVisibleIf(items.isEmpty())
            fragmentPlaceholder2.beVisibleIf(items.isEmpty() && note?.isReadOnly != true)
            counterList.beVisibleIf(items.isNotEmpty())
        }
    }

    fun getCounterItems() = Gson().toJson(items)

    private fun showNewItemDialog() {
        NewCounterItemDialog(activity as SimpleActivity) { titles ->
            var currentMaxId = items.maxByOrNull { item -> item.id }?.id ?: 0
            val newItems = ArrayList<CounterItem>()

            titles.forEach { title ->
                title.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { row ->
                    newItems.add(CounterItem(id = currentMaxId + 1, dateCreated = System.currentTimeMillis(), title = row, count = 0))
                    currentMaxId++
                }
            }

            captureHistory()
            items.addAll(newItems)
            saveNote()
            setupAdapter()
        }
    }

    private fun setupAdapter() {
        updateUIVisibility()
        CounterAdapter(
            activity = activity as SimpleActivity,
            items = items,
            recyclerView = binding.counterList,
            itemClick = {},
            plusClick = plusClick@{ item, position ->
                if (note?.isReadOnly == true) {
                    return@plusClick
                }
                captureHistory()
                item.count++
                saveNote(refreshIndex = position)
                context?.updateWidgets()
            },
            minusClick = minusClick@{ item, position ->
                if (note?.isReadOnly == true) {
                    return@minusClick
                }
                if (item.count > 0) {
                    captureHistory()
                    item.count--
                    saveNote(refreshIndex = position)
                    context?.updateWidgets()
                } else {
                    val message = getString(R.string.delete_counter_item_prompt_message)
                    ConfirmationDialog(
                        activity as SimpleActivity,
                        message,
                        R.string.delete_counter_item,
                        com.simplemobiletools.commons.R.string.ok,
                        com.simplemobiletools.commons.R.string.cancel
                    ) {
                        captureHistory()
                        items.removeAt(position)
                        saveNote()
                        setupAdapter()
                        context?.updateWidgets()
                    }
                }
            },
            readOnly = note?.isReadOnly == true
        ).apply {
            binding.counterList.adapter = this
        }
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

        if (refreshIndex != -1) {
            binding.counterList.post {
                binding.counterList.adapter?.notifyItemChanged(refreshIndex)
            }
        }

        note!!.value = Gson().toJson(items)

        ensureBackgroundThread {
            saveNoteValue(note!!, note!!.value)
            context?.updateWidgets()
            activity?.runOnUiThread(callback)
        }
    }

    private fun captureHistory() {
        if (applyingHistory) {
            return
        }
        itemsHistory.push(getCounterItems())
        notifyHistoryChanged()
    }

    override fun undo() {
        val previous = itemsHistory.undo(getCounterItems()) ?: return
        applySnapshot(previous)
    }

    override fun redo() {
        val next = itemsHistory.redo(getCounterItems()) ?: return
        applySnapshot(next)
    }

    override fun isUndoAvailable() = itemsHistory.canUndo()

    override fun isRedoAvailable() = itemsHistory.canRedo()

    private fun applySnapshot(json: String) {
        applyingHistory = true
        items = Gson().fromJson<ArrayList<CounterItem>>(json, object : TypeToken<List<CounterItem>>() {}.type) ?: ArrayList(1)
        setupAdapter()
        saveNote()
        applyingHistory = false
        notifyHistoryChanged()
    }

    private fun FragmentCounterBinding.toCommonBinding(): CommonNoteBinding = this.let {
        object : CommonNoteBinding {
            override val root: View = it.root
            override val noteLockedLayout: View = it.noteLockedLayout
            override val noteLockedImage: ImageView = it.noteLockedImage
            override val noteLockedLabel: TextView = it.noteLockedLabel
            override val noteLockedShow: TextView = it.noteLockedShow
        }
    }
}
