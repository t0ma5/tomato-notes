package tomato.notes.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.ColorPickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.IS_CUSTOMIZING_COLORS
import com.simplemobiletools.commons.helpers.PROTECTION_FINGERPRINT
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.RadioItem
import tomato.notes.R
import tomato.notes.adapters.ChecklistAdapter
import tomato.notes.adapters.CounterAdapter
import tomato.notes.databinding.WidgetConfigBinding
import tomato.notes.extensions.config
import tomato.notes.extensions.getPercentageFontSize
import tomato.notes.extensions.notebooksDB
import tomato.notes.extensions.unlockNotebookIfNeeded
import tomato.notes.extensions.updateWidgets
import tomato.notes.extensions.widgetsDB
import tomato.notes.helpers.*
import tomato.notes.models.ChecklistItem
import tomato.notes.models.CounterItem
import tomato.notes.models.Note
import tomato.notes.models.Notebook
import tomato.notes.models.NoteType
import tomato.notes.models.Widget

class WidgetConfigureActivity : SimpleActivity() {
    private var mBgAlpha = 0f
    private var mWidgetId = 0
    private var mBgColor = 0
    private var mBgColorWithoutTransparency = 0
    private var mTextColor = 0
    private var mCurrentNoteId = 0L
    private var mIsCustomizingColors = false
    private var mShowTitle = false
    private var mNotes = listOf<Note>()
    private var mNotebooks = mapOf<Long?, Notebook>()
    private var mAllWidgetIds = intArrayOf()
    private val binding by viewBinding(WidgetConfigBinding::inflate)

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(binding.root)
        initVariables()

        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !mIsCustomizingColors) {
            finish()
            return
        }

        updateTextColors(binding.notesPickerHolder)
        binding.configSave.setOnClickListener { saveConfig() }
        binding.configBgColor.setOnClickListener { pickBackgroundColor() }
        binding.configTextColor.setOnClickListener { pickTextColor() }
        binding.notesPickerValue.setOnClickListener { showNoteSelector() }

        val primaryColor = getProperPrimaryColor()
        binding.configBgSeekbar.setColors(mTextColor, primaryColor, primaryColor)
        binding.notesPickerHolder.background = ColorDrawable(getProperBackgroundColor())

        binding.showNoteTitleHolder.setOnClickListener {
            binding.showNoteTitle.toggle()
            handleNoteTitleDisplay()
        }
        setupDynamicColors()
    }

    override fun onResume() {
        super.onResume()
        binding.textNoteView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getPercentageFontSize())
    }

    private fun initVariables() {
        val extras = intent.extras
        if (extras?.getInt(CUSTOMIZED_WIDGET_ID, 0) == 0) {
            mBgColor = config.widgetBgColor
            mTextColor = config.widgetTextColor
        } else {
            mBgColor = extras?.getInt(CUSTOMIZED_WIDGET_BG_COLOR) ?: config.widgetBgColor
            mTextColor = extras?.getInt(CUSTOMIZED_WIDGET_TEXT_COLOR) ?: config.widgetTextColor
            mShowTitle = extras?.getBoolean(CUSTOMIZED_WIDGET_SHOW_TITLE) ?: false
        }

        if (mTextColor == resources.getColor(com.simplemobiletools.commons.R.color.default_widget_text_color) && config.isUsingSystemTheme) {
            mTextColor = resources.getColor(com.simplemobiletools.commons.R.color.you_primary_color, theme)
        }

        mBgAlpha = Color.alpha(mBgColor) / 255.toFloat()

        mBgColorWithoutTransparency = Color.rgb(Color.red(mBgColor), Color.green(mBgColor), Color.blue(mBgColor))
        binding.configBgSeekbar.apply {
            progress = (mBgAlpha * 100).toInt()

            onSeekBarChangeListener {
                mBgAlpha = it / 100f
                updateBackgroundColor()
            }
        }
        updateBackgroundColor()

        updateTextColor()
        mIsCustomizingColors = extras?.getBoolean(IS_CUSTOMIZING_COLORS) ?: false
        mAllWidgetIds = extras?.getIntArray(ALL_WIDGET_IDS) ?: intArrayOf()
        binding.notesPickerHolder.beVisibleIf(!mIsCustomizingColors)
        binding.textNoteViewTitle.beGoneIf(!mShowTitle)

        NotesHelper(this).getNotes { notes ->
            ensureBackgroundThread {
                val notebooks = notebooksDB.getNotebooks().associateBy { it.id }
                runOnUiThread {
                    mNotes = notes
                    mNotebooks = notebooks
                    onNotesLoaded()
                }
            }
        }
    }

    private fun onNotesLoaded() {
        binding.notesPickerHolder.beVisibleIf(mNotes.size > 1 && !mIsCustomizingColors)
        val note = mNotes.firstOrNull { !it.isLocked() && NotebookUnlockSession.canShow(mNotebooks[it.notebookId]) }

        if (mNotes.size == 1 && note == null) {
            unlockAndShowNote(mNotes.first()) { finish() }
        } else if (note != null) {
            updateCurrentNote(note)
        }
    }

    // The preview shows the note's content, so both the notebook lock and the note lock must be passed first
    private fun unlockAndShowNote(note: Note, failureCallback: (() -> Unit)? = null) {
        val notebook = mNotebooks[note.notebookId]
        val showNote = {
            if (note.protectionType == PROTECTION_NONE || note.shouldBeUnlocked(this)) {
                updateCurrentNote(note)
            } else {
                performSecurityCheck(
                    protectionType = note.protectionType,
                    requiredHash = note.protectionHash,
                    successCallback = { _, _ -> updateCurrentNote(note) },
                    // The commons biometric prompt reports each misread finger as a failure, so don't close on it
                    failureCallback = if (note.protectionType == PROTECTION_FINGERPRINT) null else failureCallback
                )
            }
        }

        if (NotebookUnlockSession.canShow(notebook)) {
            showNote()
        } else {
            unlockNotebookIfNeeded(notebook, onCancel = { failureCallback?.invoke() }) { showNote() }
        }
    }

    private fun showNoteSelector() {
        val items = ArrayList<RadioItem>()
        mNotes.forEach {
            items.add(RadioItem(it.id!!.toInt(), it.title))
        }

        RadioGroupDialog(this, items, mCurrentNoteId.toInt()) {
            val selectedId = it as Int
            val note = mNotes.firstOrNull { it.id!!.toInt() == selectedId } ?: return@RadioGroupDialog
            unlockAndShowNote(note)
        }
    }

    private fun updateCurrentNote(note: Note) {
        mCurrentNoteId = note.id!!
        binding.notesPickerValue.text = note.title
        binding.textNoteViewTitle.text = note.title
        if (note.type == NoteType.TYPE_CHECKLIST) {
            val checklistItemType = object : TypeToken<List<ChecklistItem>>() {}.type
            val items = try {
                Gson().fromJson<ArrayList<ChecklistItem>>(note.value, checklistItemType)?.filter { it.title != null }?.let { ArrayList(it) }
            } catch (e: Exception) {
                null
            } ?: ArrayList(1)
            items.apply {
                if (isEmpty()) {
                    add(ChecklistItem(0, System.currentTimeMillis(), "Milk", true))
                    add(ChecklistItem(1, System.currentTimeMillis(), "Butter", true))
                    add(ChecklistItem(2, System.currentTimeMillis(), "Salt", false))
                    add(ChecklistItem(3, System.currentTimeMillis(), "Water", false))
                    add(ChecklistItem(4, System.currentTimeMillis(), "Meat", true))
                }
            }

            ChecklistAdapter(this, items, null, binding.checklistNoteView, false, note.id!!) {}.apply {
                updateTextColor(getPreviewTextColor())
                binding.checklistNoteView.adapter = this
            }
            binding.textNoteView.beGone()
            binding.checklistNoteView.beVisible()
        } else if (note.type == NoteType.TYPE_COUNTER) {
            val counterItemType = object : TypeToken<List<CounterItem>>() {}.type
            val items = try {
                Gson().fromJson<ArrayList<CounterItem>>(note.value, counterItemType)?.filter { it.title != null }?.let { ArrayList(it) }
            } catch (e: Exception) {
                null
            } ?: ArrayList(1)
            items.apply {
                if (isEmpty()) {
                    add(CounterItem(0, System.currentTimeMillis(), "Coffee", 3))
                    add(CounterItem(1, System.currentTimeMillis(), "Workout", 1))
                    add(CounterItem(2, System.currentTimeMillis(), "Water", 0))
                }
            }

            CounterAdapter(
                activity = this,
                items = items,
                recyclerView = binding.checklistNoteView,
                itemClick = {},
                plusClick = { _, _ -> },
                minusClick = { _, _ -> }
            ).apply {
                updateTextColor(getPreviewTextColor())
                binding.checklistNoteView.adapter = this
            }
            binding.textNoteView.beGone()
            binding.checklistNoteView.beVisible()
        } else {
            val sampleValue = if (note.value.isEmpty() || mIsCustomizingColors) getString(R.string.widget_config) else note.value
            binding.textNoteView.text = sampleValue
            binding.textNoteView.typeface = if (config.monospacedFont) Typeface.MONOSPACE else Typeface.DEFAULT
            binding.textNoteView.beVisible()
            binding.checklistNoteView.beGone()
        }
    }

    private fun saveConfig() {
        if (mIsCustomizingColors) {
            ensureBackgroundThread {
                widgetsDB.updateWidgetColors(mBgColor, mTextColor)
                runOnUiThread {
                    storeWidgetBackground()
                    requestWidgetUpdate()
                    finishWithResult()
                }
            }
            return
        }

        if (mCurrentNoteId == 0L) {
            finish()
            return
        }

        val extras = intent.extras
        val id = if (extras?.containsKey(CUSTOMIZED_WIDGET_KEY_ID) == true) extras.getLong(CUSTOMIZED_WIDGET_KEY_ID) else null
        mWidgetId = extras?.getInt(CUSTOMIZED_WIDGET_ID, mWidgetId) ?: mWidgetId
        mCurrentNoteId = extras?.getLong(CUSTOMIZED_WIDGET_NOTE_ID, mCurrentNoteId) ?: mCurrentNoteId
        val widget = Widget(id, mWidgetId, mCurrentNoteId, mBgColor, mTextColor, mShowTitle)
        // The widget provider reads the row, so only ask for an update once it is stored
        ensureBackgroundThread {
            widgetsDB.insertOrUpdate(widget)
            runOnUiThread {
                storeWidgetBackground()
                requestWidgetUpdate()
                finishWithResult()
            }
        }
    }

    private fun finishWithResult() {
        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    private fun storeWidgetBackground() {
        config.apply {
            widgetBgColor = mBgColor
            widgetTextColor = mTextColor
        }
    }

    private fun requestWidgetUpdate() {
        val widgetIds = if (mAllWidgetIds.isNotEmpty()) mAllWidgetIds else intArrayOf(mWidgetId)
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetProvider::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            sendBroadcast(this)
        }
        updateWidgets()
    }

    private fun updateBackgroundColor() {
        mBgColor = mBgColorWithoutTransparency.adjustAlpha(mBgAlpha)
        val previewBgColor = getPreviewBgColor()
        binding.textNoteView.setBackgroundColor(previewBgColor)
        binding.checklistNoteView.setBackgroundColor(previewBgColor)
        binding.textNoteViewTitle.setBackgroundColor(previewBgColor)
        binding.configBgColor.setFillWithStroke(mBgColor, mBgColor)
        binding.configSave.backgroundTintList = ColorStateList.valueOf(getProperPrimaryColor())
    }

    private fun updateTextColor() {
        val previewTextColor = getPreviewTextColor()
        binding.textNoteView.setTextColor(previewTextColor)
        binding.textNoteViewTitle.setTextColor(previewTextColor)
        (binding.checklistNoteView.adapter as? ChecklistAdapter)?.updateTextColor(previewTextColor)
        (binding.checklistNoteView.adapter as? CounterAdapter)?.updateTextColor(previewTextColor)
        binding.configTextColor.setFillWithStroke(mTextColor, mTextColor)
        binding.configSave.setTextColor(getProperPrimaryColor().getContrastColor())
    }

    private fun pickBackgroundColor() {
        ColorPickerDialog(this, mBgColorWithoutTransparency) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mBgColorWithoutTransparency = color
                updateBackgroundColor()
            }
        }
    }

    private fun pickTextColor() {
        ColorPickerDialog(this, mTextColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mTextColor = color
                updateTextColor()
            }
        }
    }

    // Android 12+ only. Global like the widget colors, so it applies to every widget right away.
    private fun setupDynamicColors() {
        binding.widgetDynamicColorsHolder.apply {
            beVisibleIf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            background = ColorDrawable(getProperBackgroundColor())
            updateTextColors(this)
            setOnClickListener {
                binding.widgetDynamicColors.toggle()
                config.widgetUseDynamicColors = binding.widgetDynamicColors.isChecked
                updateWidgets()
                updateColorPickers()
                updateBackgroundColor()
                updateTextColor()
            }
        }
        binding.widgetDynamicColors.isChecked = useWidgetDynamicColors()
        updateColorPickers()
    }

    // The custom colors stay stored, the pickers are only greyed out while system colors are used
    private fun updateColorPickers() {
        val isEnabled = !useWidgetDynamicColors()
        arrayOf(binding.configBgColor, binding.configTextColor, binding.configBgSeekbarHolder, binding.configBgSeekbar).forEach {
            it.isEnabled = isEnabled
        }
        arrayOf(binding.configBgColor, binding.configTextColor, binding.configBgSeekbarHolder).forEach {
            it.alpha = if (isEnabled) 1f else 0.4f
        }
    }

    // The preview shows what the widget will look like
    private fun getPreviewBgColor() = if (useWidgetDynamicColors()) getColor(R.color.widget_dynamic_bg) else mBgColor

    private fun getPreviewTextColor() = if (useWidgetDynamicColors()) getColor(R.color.widget_dynamic_text) else mTextColor

    private fun handleNoteTitleDisplay() {
        val showTitle = binding.showNoteTitle.isChecked
        binding.textNoteViewTitle.beGoneIf(!showTitle)
        mShowTitle = showTitle
    }
}
