package tomato.notes.helpers

import android.content.Context
import android.graphics.Color
import android.os.Environment
import android.view.Gravity
import com.simplemobiletools.commons.helpers.BaseConfig
import tomato.notes.models.NoteType

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)

        private const val DARK_RED_THEME_REVISION = 2
        private const val DARK_RED_THEME_REVISION_KEY = "dark_red_theme_revision"
        private const val WIDGET_COLOR_REVISION = 1
        private const val WIDGET_COLOR_REVISION_KEY = "widget_color_revision"
    }

    var autosaveNotes: Boolean
        get() = prefs.getBoolean(AUTOSAVE_NOTES, true)
        set(autosaveNotes) = prefs.edit().putBoolean(AUTOSAVE_NOTES, autosaveNotes).apply()

    var displaySuccess: Boolean
        get() = prefs.getBoolean(DISPLAY_SUCCESS, true)
        set(displaySuccess) = prefs.edit().putBoolean(DISPLAY_SUCCESS, displaySuccess).apply()

    var clickableLinks: Boolean
        get() = prefs.getBoolean(CLICKABLE_LINKS, true)
        set(clickableLinks) = prefs.edit().putBoolean(CLICKABLE_LINKS, clickableLinks).apply()

    var monospacedFont: Boolean
        get() = prefs.getBoolean(MONOSPACED_FONT, false)
        set(monospacedFont) = prefs.edit().putBoolean(MONOSPACED_FONT, monospacedFont).apply()

    var showKeyboard: Boolean
        get() = prefs.getBoolean(SHOW_KEYBOARD, false)
        set(showKeyboard) = prefs.edit().putBoolean(SHOW_KEYBOARD, showKeyboard).apply()

    var showNotebooks: Boolean
        get() = prefs.getBoolean(SHOW_NOTEBOOKS, false)
        set(showNotebooks) = prefs.edit().putBoolean(SHOW_NOTEBOOKS, showNotebooks).apply()

    fun applyDefaultDarkRedThemeIfNeeded() {
        if (prefs.getInt(DARK_RED_THEME_REVISION_KEY, 0) >= DARK_RED_THEME_REVISION) {
            return
        }
        isUsingSystemTheme = false
        isUsingAutoTheme = false
        isUsingSharedTheme = false
        textColor = Color.parseColor("#FFF5F5F5")
        backgroundColor = Color.parseColor("#FF121212")
        val red = context.resources.getColor(com.simplemobiletools.commons.R.color.md_red_700, context.theme)
        primaryColor = red
        accentColor = red
        prefs.edit().putInt(DARK_RED_THEME_REVISION_KEY, DARK_RED_THEME_REVISION).apply()
    }

    fun applyDefaultWidgetColorsIfNeeded() {
        if (prefs.getInt(WIDGET_COLOR_REVISION_KEY, 0) >= WIDGET_COLOR_REVISION) {
            return
        }
        val oldBg = Color.parseColor("#AA000000")
        val oldText = Color.parseColor("#FFD32F2F")
        if (widgetBgColor == oldBg) {
            widgetBgColor = Color.parseColor("#00121212")
        }
        if (widgetTextColor == oldText) {
            widgetTextColor = Color.WHITE
        }
        prefs.edit().putInt(WIDGET_COLOR_REVISION_KEY, WIDGET_COLOR_REVISION).apply()
    }

    var showNotePicker: Boolean
        get() = prefs.getBoolean(SHOW_NOTE_PICKER, false)
        set(showNotePicker) = prefs.edit().putBoolean(SHOW_NOTE_PICKER, showNotePicker).apply()

    var showWordCount: Boolean
        get() = prefs.getBoolean(SHOW_WORD_COUNT, false)
        set(showWordCount) = prefs.edit().putBoolean(SHOW_WORD_COUNT, showWordCount).apply()

    var gravity: Int
        get() = prefs.getInt(GRAVITY, GRAVITY_START)
        set(size) = prefs.edit().putInt(GRAVITY, size).apply()

    var currentNoteId: Long
        get() = prefs.getLong(CURRENT_NOTE_ID, 1L)
        set(id) = prefs.edit().putLong(CURRENT_NOTE_ID, id).apply()

    var currentNotebookId: Long
        get() = prefs.getLong(CURRENT_NOTEBOOK_ID, 1L)
        set(id) = prefs.edit().putLong(CURRENT_NOTEBOOK_ID, id).apply()

    var widgetNoteId: Long
        get() = prefs.getLong(WIDGET_NOTE_ID, 1L)
        set(id) = prefs.edit().putLong(WIDGET_NOTE_ID, id).apply()

    var placeCursorToEnd: Boolean
        get() = prefs.getBoolean(CURSOR_PLACEMENT, true)
        set(placement) = prefs.edit().putBoolean(CURSOR_PLACEMENT, placement).apply()

    var enableLineWrap: Boolean
        get() = prefs.getBoolean(ENABLE_LINE_WRAP, true)
        set(enableLineWrap) = prefs.edit().putBoolean(ENABLE_LINE_WRAP, enableLineWrap).apply()

    var lastUsedExtension: String
        get() = prefs.getString(LAST_USED_EXTENSION, "txt")!!
        set(lastUsedExtension) = prefs.edit().putString(LAST_USED_EXTENSION, lastUsedExtension).apply()

    var lastUsedSavePath: String
        get() = prefs.getString(LAST_USED_SAVE_PATH, Environment.getExternalStorageDirectory().toString())!!
        set(lastUsedSavePath) = prefs.edit().putString(LAST_USED_SAVE_PATH, lastUsedSavePath).apply()

    var useIncognitoMode: Boolean
        get() = prefs.getBoolean(USE_INCOGNITO_MODE, false)
        set(useIncognitoMode) = prefs.edit().putBoolean(USE_INCOGNITO_MODE, useIncognitoMode).apply()

    var lastCreatedNoteType: Int
        get() = prefs.getInt(LAST_CREATED_NOTE_TYPE, NoteType.TYPE_TEXT.value)
        set(lastCreatedNoteType) = prefs.edit().putInt(LAST_CREATED_NOTE_TYPE, lastCreatedNoteType).apply()

    var moveDoneChecklistItems: Boolean
        get() = prefs.getBoolean(MOVE_DONE_CHECKLIST_ITEMS, false)
        set(moveDoneChecklistItems) = prefs.edit().putBoolean(MOVE_DONE_CHECKLIST_ITEMS, moveDoneChecklistItems).apply()

    fun getTextGravity() = when (gravity) {
        GRAVITY_CENTER -> Gravity.CENTER_HORIZONTAL
        GRAVITY_END -> Gravity.END
        else -> Gravity.START
    }

    var fontSizePercentage: Int
        get() = prefs.getInt(FONT_SIZE_PERCENTAGE, 100)
        set(fontSizePercentage) = prefs.edit().putInt(FONT_SIZE_PERCENTAGE, fontSizePercentage).apply()

    var addNewChecklistItemsTop: Boolean
        get() = prefs.getBoolean(ADD_NEW_CHECKLIST_ITEMS_TOP, false)
        set(addNewCheckListItemsTop) = prefs.edit().putBoolean(ADD_NEW_CHECKLIST_ITEMS_TOP, addNewCheckListItemsTop).apply()

    var notebookColumns: Int
        get() = prefs.getInt(NOTEBOOK_COLUMNS, 2).coerceIn(1, 4)
        set(notebookColumns) = prefs.edit().putInt(NOTEBOOK_COLUMNS, notebookColumns.coerceIn(1, 4)).apply()

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, true)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    fun getChecklistSorting(noteId: Long?): Int {
        if (noteId != null && prefs.contains("$CHECKLIST_SORTING_PREFIX$noteId")) {
            return prefs.getInt("$CHECKLIST_SORTING_PREFIX$noteId", sorting)
        }
        return sorting
    }

    fun saveChecklistSorting(noteId: Long, sorting: Int) {
        prefs.edit().putInt("$CHECKLIST_SORTING_PREFIX$noteId", sorting).apply()
    }

    fun removeChecklistSorting(noteId: Long) {
        prefs.edit().remove("$CHECKLIST_SORTING_PREFIX$noteId").apply()
    }

    fun hasCustomChecklistSorting(noteId: Long) = prefs.contains("$CHECKLIST_SORTING_PREFIX$noteId")

    fun getCheckedItemsCollapsed(noteId: Long) = prefs.getBoolean("$CHECKED_ITEMS_COLLAPSED_PREFIX$noteId", false)

    fun saveCheckedItemsCollapsed(noteId: Long, collapsed: Boolean) {
        prefs.edit().putBoolean("$CHECKED_ITEMS_COLLAPSED_PREFIX$noteId", collapsed).apply()
    }
}
