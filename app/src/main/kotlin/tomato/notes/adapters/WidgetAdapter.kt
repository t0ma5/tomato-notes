package tomato.notes.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.simplemobiletools.commons.extensions.adjustAlpha
import com.simplemobiletools.commons.extensions.setText
import com.simplemobiletools.commons.extensions.setTextSize
import com.simplemobiletools.commons.helpers.WIDGET_TEXT_COLOR
import tomato.notes.R
import tomato.notes.R.id.widget_text_holder
import tomato.notes.extensions.config
import tomato.notes.extensions.getPercentageFontSize
import tomato.notes.extensions.notebooksDB
import tomato.notes.extensions.notesDB
import tomato.notes.helpers.*
import tomato.notes.models.ChecklistItem
import tomato.notes.models.CounterItem
import tomato.notes.models.Note
import tomato.notes.models.NoteType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class WidgetAdapter(val context: Context, val intent: Intent) : RemoteViewsService.RemoteViewsFactory {
    private val textIds = arrayOf(
        R.id.widget_text_left, R.id.widget_text_center, R.id.widget_text_right,
        R.id.widget_text_left_monospace, R.id.widget_text_center_monospace, R.id.widget_text_right_monospace
    )
    private val checklistIds = arrayOf(
        R.id.checklist_text_left, R.id.checklist_text_center, R.id.checklist_text_right,
        R.id.checklist_text_left_monospace, R.id.checklist_text_center_monospace, R.id.checklist_text_right_monospace
    )
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private var widgetTextColor = DEFAULT_WIDGET_TEXT_COLOR
    private var note: Note? = null
    private var checklistItems = mutableListOf<ChecklistItem>()
    private var counterItems = mutableListOf<CounterItem>()
    private var notebookNotes = listOf<Note>()
    private var isNotebookWidget = false

    // When set, the widget shows only this text (locked, deleted or missing content)
    private var placeholder: String? = null
    private var placeholderOpenNoteId = 0L

    override fun getViewAt(position: Int): RemoteViews {
        val noteId = intent.getLongExtra(NOTE_ID, 0L)
        val remoteView: RemoteViews
        val textSize = context.getPercentageFontSize() / context.resources.displayMetrics.density

        placeholder?.let {
            return getTextRow(it, textSize, placeholderOpenNoteId)
        }

        if (isNotebookWidget) {
            val rowNote = notebookNotes.getOrNull(position)
            // Home screen widgets never show a locked note's title
            val title = when {
                rowNote == null -> ""
                rowNote.isLocked() -> context.getString(R.string.widget_locked_note)
                else -> rowNote.title
            }
            return getTextRow(title, textSize, rowNote?.id ?: 0L)
        }

        if (note == null) {
            return RemoteViews(context.packageName, R.layout.widget_text_layout)
        }

        if (note!!.type == NoteType.TYPE_CHECKLIST) {
            remoteView = RemoteViews(context.packageName, R.layout.item_checklist_widget).apply {
                val checklistItem = checklistItems.getOrNull(position) ?: return@apply
                val isDone = checklistItem.isDone && !checklistItem.isSectionHeader()
                val widgetNewTextColor = if (isDone) widgetTextColor.adjustAlpha(DONE_CHECKLIST_ITEM_ALPHA) else widgetTextColor
                val paintFlags = if (isDone) Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG else Paint.ANTI_ALIAS_FLAG

                for (id in checklistIds) {
                    setText(id, checklistItem.title)
                    setTextColor(id, widgetNewTextColor)
                    setTextSize(id, textSize)
                    setInt(id, "setPaintFlags", paintFlags)
                    setViewVisibility(id, View.GONE)
                }

                setViewVisibility(getProperChecklistTextView(context), View.VISIBLE)

                Intent().apply {
                    putExtra(OPEN_NOTE_ID, noteId)
                    setOnClickFillInIntent(R.id.checklist_text_holder, this)
                }
            }
        } else {
            val noteText = if (note!!.type == NoteType.TYPE_COUNTER) {
                counterItems.joinToString(separator = System.lineSeparator()) { "${it.title}: ${it.count}" }
            } else {
                note!!.getNoteStoredValue(context) ?: ""
            }
            remoteView = getTextRow(noteText, textSize, noteId)
        }

        return remoteView
    }

    private fun getTextRow(text: String, textSize: Float, openNoteId: Long): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_text_layout).apply {
            for (id in textIds) {
                setText(id, text)
                setTextColor(id, widgetTextColor)
                setTextSize(id, textSize)
                setViewVisibility(id, View.GONE)
            }
            setViewVisibility(getProperTextView(context), View.VISIBLE)

            Intent().apply {
                putExtra(OPEN_NOTE_ID, openNoteId)
                setOnClickFillInIntent(widget_text_holder, this)
            }
        }
    }

    private fun getProperTextView(context: Context): Int {
        val gravity = context.config.gravity
        val isMonospaced = context.config.monospacedFont

        return when {
            gravity == GRAVITY_CENTER && isMonospaced -> R.id.widget_text_center_monospace
            gravity == GRAVITY_CENTER -> R.id.widget_text_center
            gravity == GRAVITY_END && isMonospaced -> R.id.widget_text_right_monospace
            gravity == GRAVITY_END -> R.id.widget_text_right
            isMonospaced -> R.id.widget_text_left_monospace
            else -> R.id.widget_text_left
        }
    }

    private fun getProperChecklistTextView(context: Context): Int {
        val gravity = context.config.gravity
        val isMonospaced = context.config.monospacedFont

        return when {
            gravity == GRAVITY_CENTER && isMonospaced -> R.id.checklist_text_center_monospace
            gravity == GRAVITY_CENTER -> R.id.checklist_text_center
            gravity == GRAVITY_END && isMonospaced -> R.id.checklist_text_right_monospace
            gravity == GRAVITY_END -> R.id.checklist_text_right
            isMonospaced -> R.id.checklist_text_left_monospace
            else -> R.id.checklist_text_left
        }
    }

    override fun onCreate() {}

    override fun getLoadingView() = null

    override fun getItemId(position: Int) = position.toLong()

    override fun onDataSetChanged() {
        widgetTextColor = intent.getIntExtra(WIDGET_TEXT_COLOR, DEFAULT_WIDGET_TEXT_COLOR)
        placeholder = null
        placeholderOpenNoteId = 0L
        note = null
        checklistItems = mutableListOf()
        counterItems = mutableListOf()
        notebookNotes = listOf()

        val notebookId = intent.getLongExtra(NOTEBOOK_ID, 0L)
        isNotebookWidget = intent.hasExtra(NOTEBOOK_ID)
        if (isNotebookWidget) {
            val notebook = context.notebooksDB.getNotebookWithId(notebookId)
            when {
                notebook == null || notebook.isDeleted() -> placeholder = context.getString(R.string.widget_notebook_unavailable)
                notebook.isLocked() -> placeholder = context.getString(R.string.note_content_locked)
                else -> notebookNotes = context.notesDB.getNotesInNotebook(notebookId)
            }
            return
        }

        val noteId = intent.getLongExtra(NOTE_ID, 0L)
        val loadedNote = context.notesDB.getNoteWithId(noteId)
        val notebook = loadedNote?.let { context.notebooksDB.getNotebookWithId(it.notebookId) }
        if (loadedNote == null || loadedNote.isDeleted()) {
            placeholder = context.getString(R.string.widget_note_unavailable)
            return
        }

        // Never show locked content on the home screen, even if unlocked in the app
        if (loadedNote.isLocked() || notebook?.isLocked() == true) {
            placeholder = context.getString(R.string.note_content_locked)
            placeholderOpenNoteId = noteId
            return
        }

        note = loadedNote
        if (loadedNote.type == NoteType.TYPE_CHECKLIST) {
            val items = decodeList<ChecklistItem>(loadedNote.getNoteStoredValue(context))
            val sorting = context.config.getChecklistSorting(loadedNote.id)
            val moveDone = context.config.moveDoneChecklistItems
            checklistItems = getDisplayedChecklistItems(ChecklistItem.displayOrder(items, sorting, moveDone), moveDone, loadedNote.id ?: noteId)
        } else if (loadedNote.type == NoteType.TYPE_COUNTER) {
            counterItems = decodeList<CounterItem>(loadedNote.getNoteStoredValue(context)).toMutableList()
        }
    }

    // Same layout as ChecklistFragment: unchecked items, then a "Checked items" row, then checked items unless collapsed
    private fun getDisplayedChecklistItems(ordered: List<ChecklistItem>, moveDone: Boolean, noteId: Long): MutableList<ChecklistItem> {
        val checkedItems = ordered.filter { it.isDone }
        if (!moveDone || checkedItems.isEmpty()) {
            return ordered.toMutableList()
        }

        val displayed = ordered.filter { !it.isDone }.toMutableList()
        displayed.add(
            ChecklistItem(
                id = CHECKED_ITEMS_TITLE_ID,
                dateCreated = 0L,
                title = context.getString(R.string.checked_items_count, checkedItems.size),
                isDone = false
            )
        )
        if (!context.config.getCheckedItemsCollapsed(noteId)) {
            displayed.addAll(checkedItems)
        }
        return displayed
    }

    private inline fun <reified T> decodeList(value: String?): List<T> {
        val raw = value?.ifEmpty { "[]" } ?: return emptyList()
        return try {
            json.decodeFromString<List<T>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun hasStableIds() = false

    override fun getCount(): Int {
        return when {
            placeholder != null -> 1
            isNotebookWidget -> notebookNotes.size
            note?.type == NoteType.TYPE_CHECKLIST -> checklistItems.size
            else -> 1
        }
    }

    override fun getViewTypeCount() = 2

    override fun onDestroy() {}
}
