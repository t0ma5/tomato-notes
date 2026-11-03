package tomato.notes.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import com.simplemobiletools.commons.dialogs.ColorPickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.RadioItem
import tomato.notes.R
import tomato.notes.databinding.WidgetConfigBinding
import tomato.notes.extensions.config
import tomato.notes.extensions.getPercentageFontSize
import tomato.notes.extensions.unlockNotebookIfNeeded
import tomato.notes.extensions.updateWidgets
import tomato.notes.extensions.widgetsDB
import tomato.notes.helpers.NotebookUnlockSession
import tomato.notes.helpers.NotebookWidgetProvider
import tomato.notes.helpers.NotesHelper
import tomato.notes.helpers.useWidgetDynamicColors
import tomato.notes.models.Notebook
import tomato.notes.models.Widget

class NotebookWidgetConfigureActivity : SimpleActivity() {
    private var mBgAlpha = 0f
    private var mWidgetId = 0
    private var mBgColor = 0
    private var mBgColorWithoutTransparency = 0
    private var mTextColor = 0
    private var mCurrentNotebookId = 0L
    private var mShowTitle = true
    private var mNotebooks = listOf<Notebook>()
    private val binding by viewBinding(WidgetConfigBinding::inflate)

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(binding.root)
        initVariables()

        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        updateTextColors(binding.notesPickerHolder)
        binding.configSave.setOnClickListener { saveConfig() }
        binding.configBgColor.setOnClickListener { pickBackgroundColor() }
        binding.configTextColor.setOnClickListener { pickTextColor() }
        binding.notesPickerValue.setOnClickListener { showNotebookSelector() }
        binding.notesPickerLabel.setText(R.string.notebook_shown_widget)
        binding.showNoteTitle.setText(R.string.show_notebook_title)
        binding.showNoteTitle.isChecked = mShowTitle

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
        mBgColor = config.widgetBgColor
        mTextColor = config.widgetTextColor
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
        binding.textNoteViewTitle.beGoneIf(!mShowTitle)
        binding.checklistNoteView.beGone()
        binding.textNoteView.beVisible()

        tomato.notes.helpers.NotebooksHelper(this).getNotebooks {
            mNotebooks = it
            val notebook = mNotebooks.firstOrNull { notebook -> NotebookUnlockSession.canShow(notebook) }
            if (notebook != null) {
                updateCurrentNotebook(notebook)
            } else if (mNotebooks.size == 1) {
                unlockAndShowNotebook(mNotebooks.first())
            }
        }
    }

    private fun showNotebookSelector() {
        val items = ArrayList<RadioItem>()
        mNotebooks.forEach {
            items.add(RadioItem(it.id!!.toInt(), it.title))
        }
        RadioGroupDialog(this, items, mCurrentNotebookId.toInt()) {
            val selectedId = it as Int
            val notebook = mNotebooks.firstOrNull { notebook -> notebook.id!!.toInt() == selectedId } ?: return@RadioGroupDialog
            unlockAndShowNotebook(notebook)
        }
    }

    // The preview lists note titles, so a locked notebook must be unlocked first. The widget itself stays locked.
    private fun unlockAndShowNotebook(notebook: Notebook) {
        if (NotebookUnlockSession.canShow(notebook)) {
            updateCurrentNotebook(notebook)
        } else {
            unlockNotebookIfNeeded(notebook) { updateCurrentNotebook(notebook) }
        }
    }

    private fun updateCurrentNotebook(notebook: Notebook) {
        mCurrentNotebookId = notebook.id!!
        binding.notesPickerValue.text = notebook.title
        binding.textNoteViewTitle.text = notebook.title
        NotesHelper(this).getNotesInNotebook(mCurrentNotebookId) { notes ->
            val preview = notes.joinToString(separator = System.lineSeparator()) {
                if (it.isLocked()) getString(R.string.widget_locked_note) else it.title
            }.ifEmpty { getString(R.string.widget_config) }
            binding.textNoteView.text = preview
        }
    }

    private fun saveConfig() {
        if (mCurrentNotebookId == 0L) {
            finish()
            return
        }

        val widget = Widget(null, mWidgetId, 0L, mBgColor, mTextColor, mShowTitle, mCurrentNotebookId)
        config.apply {
            widgetBgColor = mBgColor
            widgetTextColor = mTextColor
        }

        // The widget provider reads the row, so only ask for an update once it is stored
        ensureBackgroundThread {
            widgetsDB.insertOrUpdate(widget)
            runOnUiThread {
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, NotebookWidgetProvider::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
                    sendBroadcast(this)
                }

                Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
                    setResult(Activity.RESULT_OK, this)
                }
                finish()
            }
        }
    }

    private fun updateBackgroundColor() {
        mBgColor = mBgColorWithoutTransparency.adjustAlpha(mBgAlpha)
        val previewBgColor = getPreviewBgColor()
        binding.textNoteView.setBackgroundColor(previewBgColor)
        binding.textNoteViewTitle.setBackgroundColor(previewBgColor)
        binding.configBgColor.setFillWithStroke(mBgColor, mBgColor)
        binding.configSave.backgroundTintList = ColorStateList.valueOf(getProperPrimaryColor())
    }

    private fun updateTextColor() {
        val previewTextColor = getPreviewTextColor()
        binding.textNoteView.setTextColor(previewTextColor)
        binding.textNoteViewTitle.setTextColor(previewTextColor)
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
