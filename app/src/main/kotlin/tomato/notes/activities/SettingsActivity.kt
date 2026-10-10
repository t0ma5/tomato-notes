package tomato.notes.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import tomato.notes.R
import tomato.notes.databinding.ActivitySettingsBinding
import tomato.notes.dialogs.BackupPasswordDialog
import tomato.notes.dialogs.ExportNotesDialog
import tomato.notes.dialogs.ManageAutoBackupsDialog
import tomato.notes.extensions.*
import tomato.notes.helpers.*
import tomato.notes.models.Note
import tomato.notes.models.Notebook
import tomato.notes.models.Widget
import tomato.notes.models.BackupData
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private val notesFileType = "application/json"
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.settingsCoordinator, binding.settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsToolbar)
    }

    override fun onResume() {
        super.onResume()
        setupPrimaryToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupAutosaveNotes()
        setupDisplaySuccess()
        setupClickableLinks()
        setupMonospacedFont()
        setupShowKeyboard()
        setupShowNotePicker()
        setupShowWordCount()
        setupEnableLineWrap()
        setupFontSize()
        setupNotebookColumns()
        setupGravity()
        setupCursorPlacement()
        setupIncognitoMode()
        setupCustomizeWidgetColors()
        setupNotesExport()
        setupNotesImport()
        setupEnableAutomaticBackups()
        setupManageAutomaticBackups()
        setupUseRecycleBin()
        setupEmptyRecycleBin()
        updateTextColors(binding.settingsNestedScrollview)

        arrayOf(
            binding.settingsColorCustomizationSectionLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsTextLabel,
            binding.settingsStartupLabel,
            binding.settingsSavingLabel,
            binding.settingsMigratingLabel,
            binding.settingsRecycleBinLabel,
            binding.settingsBackupsLabel,
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            toast(com.simplemobiletools.commons.R.string.importing)
            importNotes(uri)
        }
    }

    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(notesFileType)) { uri ->
        if (uri != null) {
            toast(com.simplemobiletools.commons.R.string.exporting)
            NotesHelper(this).getNotes { notes ->
                requestUnlockNotes(notes) { unlockedNotes ->
                    val notLockedNotes = notes.filterNot { it.isLocked() }
                    val notesToExport = unlockedNotes + notLockedNotes
                    exportNotes(notesToExport, uri, pendingExportPassword)
                    pendingExportPassword = null
                }
            }
        } else {
            pendingExportPassword = null
        }
    }

    private var pendingExportPassword: String? = null

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            openColorCustomization()
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        binding.settingsUseEnglish.isChecked = config.useEnglish
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        binding.settingsLanguage.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        binding.settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupAutosaveNotes() {
        binding.settingsAutosaveNotes.isChecked = config.autosaveNotes
        binding.settingsAutosaveNotesHolder.setOnClickListener {
            binding.settingsAutosaveNotes.toggle()
            config.autosaveNotes = binding.settingsAutosaveNotes.isChecked
        }
    }

    private fun setupDisplaySuccess() {
        binding.settingsDisplaySuccess.isChecked = config.displaySuccess
        binding.settingsDisplaySuccessHolder.setOnClickListener {
            binding.settingsDisplaySuccess.toggle()
            config.displaySuccess = binding.settingsDisplaySuccess.isChecked
        }
    }

    private fun setupClickableLinks() {
        binding.settingsClickableLinks.isChecked = config.clickableLinks
        binding.settingsClickableLinksHolder.setOnClickListener {
            binding.settingsClickableLinks.toggle()
            config.clickableLinks = binding.settingsClickableLinks.isChecked
        }
    }

    private fun setupMonospacedFont() {
        binding.settingsMonospacedFont.isChecked = config.monospacedFont
        binding.settingsMonospacedFontHolder.setOnClickListener {
            binding.settingsMonospacedFont.toggle()
            config.monospacedFont = binding.settingsMonospacedFont.isChecked
            updateWidgets()
        }
    }

    private fun setupShowKeyboard() {
        binding.settingsShowKeyboard.isChecked = config.showKeyboard
        binding.settingsShowKeyboardHolder.setOnClickListener {
            binding.settingsShowKeyboard.toggle()
            config.showKeyboard = binding.settingsShowKeyboard.isChecked
        }
    }

    private fun setupShowNotePicker() {
        ensureBackgroundThread {
            val noteCount = notesDB.getNotes().size
            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    return@runOnUiThread
                }
                binding.settingsShowNotePickerHolder.beVisibleIf(noteCount > 1)
            }
        }

        binding.settingsShowNotePicker.isChecked = config.showNotePicker
        binding.settingsShowNotePickerHolder.setOnClickListener {
            binding.settingsShowNotePicker.toggle()
            config.showNotePicker = binding.settingsShowNotePicker.isChecked
        }
    }

    private fun setupShowWordCount() {
        binding.settingsShowWordCount.isChecked = config.showWordCount
        binding.settingsShowWordCountHolder.setOnClickListener {
            binding.settingsShowWordCount.toggle()
            config.showWordCount = binding.settingsShowWordCount.isChecked
        }
    }

    private fun setupEnableLineWrap() {
        binding.settingsEnableLineWrap.isChecked = config.enableLineWrap
        binding.settingsEnableLineWrapHolder.setOnClickListener {
            binding.settingsEnableLineWrap.toggle()
            config.enableLineWrap = binding.settingsEnableLineWrap.isChecked
        }
    }

    private fun setupFontSize() {
        binding.settingsFontSize.text = getFontSizePercentText(config.fontSizePercentage)
        binding.settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_50_PERCENT, getFontSizePercentText(FONT_SIZE_50_PERCENT)),
                RadioItem(FONT_SIZE_60_PERCENT, getFontSizePercentText(FONT_SIZE_60_PERCENT)),
                RadioItem(FONT_SIZE_75_PERCENT, getFontSizePercentText(FONT_SIZE_75_PERCENT)),
                RadioItem(FONT_SIZE_90_PERCENT, getFontSizePercentText(FONT_SIZE_90_PERCENT)),
                RadioItem(FONT_SIZE_100_PERCENT, getFontSizePercentText(FONT_SIZE_100_PERCENT)),
                RadioItem(FONT_SIZE_125_PERCENT, getFontSizePercentText(FONT_SIZE_125_PERCENT)),
                RadioItem(FONT_SIZE_150_PERCENT, getFontSizePercentText(FONT_SIZE_150_PERCENT)),
                RadioItem(FONT_SIZE_175_PERCENT, getFontSizePercentText(FONT_SIZE_175_PERCENT)),
                RadioItem(FONT_SIZE_200_PERCENT, getFontSizePercentText(FONT_SIZE_200_PERCENT)),
                RadioItem(FONT_SIZE_250_PERCENT, getFontSizePercentText(FONT_SIZE_250_PERCENT)),
                RadioItem(FONT_SIZE_300_PERCENT, getFontSizePercentText(FONT_SIZE_300_PERCENT))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSizePercentage) {
                config.fontSizePercentage = it as Int
                binding.settingsFontSize.text = getFontSizePercentText(config.fontSizePercentage)
                updateWidgets()
            }
        }
    }

    private fun getFontSizePercentText(fontSizePercentage: Int): String = "$fontSizePercentage%"

    private fun setupNotebookColumns() {
        binding.settingsNotebookColumns.text = config.notebookColumns.toString()
        binding.settingsNotebookColumnsHolder.setOnClickListener {
            val items = (1..4).map { RadioItem(it, it.toString()) }
            RadioGroupDialog(this@SettingsActivity, ArrayList(items), config.notebookColumns) {
                config.notebookColumns = it as Int
                binding.settingsNotebookColumns.text = config.notebookColumns.toString()
            }
        }
    }

    private fun setupGravity() {
        binding.settingsGravity.text = getGravityText()
        binding.settingsGravityHolder.setOnClickListener {
            val items = listOf(GRAVITY_START, GRAVITY_CENTER, GRAVITY_END).map { RadioItem(it, getGravityOptionLabel(it)) }
            RadioGroupDialog(this@SettingsActivity, ArrayList(items), config.gravity) {
                config.gravity = it as Int
                binding.settingsGravity.text = getGravityText()
                updateWidgets()
            }
        }
    }

    private fun getGravityOptionLabel(gravity: Int): String {
        val leftToRightDirection = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_LTR
        val leftRightLabels = listOf(R.string.left, R.string.right)
        val startEndLabels = if (leftToRightDirection) {
            leftRightLabels
        } else {
            leftRightLabels.reversed()
        }
        return getString(
            when (gravity) {
                GRAVITY_START -> startEndLabels.first()
                GRAVITY_CENTER -> R.string.center
                else -> startEndLabels.last()
            }
        )
    }

    private fun getGravityText() = getGravityOptionLabel(config.gravity)

    private fun setupCursorPlacement() {
        binding.settingsCursorPlacement.isChecked = config.placeCursorToEnd
        binding.settingsCursorPlacementHolder.setOnClickListener {
            binding.settingsCursorPlacement.toggle()
            config.placeCursorToEnd = binding.settingsCursorPlacement.isChecked
        }
    }

    private fun setupCustomizeWidgetColors() {
        var widgetToCustomize: Widget? = null
        var allWidgetIds = intArrayOf()

        binding.settingsWidgetColorCustomizationHolder.setOnClickListener {
            Intent(this, WidgetConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                putExtra(ALL_WIDGET_IDS, allWidgetIds)

                widgetToCustomize?.apply {
                    putExtra(CUSTOMIZED_WIDGET_ID, widgetId)
                    putExtra(CUSTOMIZED_WIDGET_KEY_ID, id)
                    putExtra(CUSTOMIZED_WIDGET_NOTE_ID, noteId)
                    putExtra(CUSTOMIZED_WIDGET_BG_COLOR, widgetBgColor)
                    putExtra(CUSTOMIZED_WIDGET_TEXT_COLOR, widgetTextColor)
                    putExtra(CUSTOMIZED_WIDGET_SHOW_TITLE, widgetShowTitle)
                }

                startActivity(this)
            }
        }

        ensureBackgroundThread {
            val widgets = widgetsDB.getWidgets().filter { it.widgetId != 0 }
            allWidgetIds = widgets.map { it.widgetId }.toIntArray()
            if (widgets.size == 1) {
                widgetToCustomize = widgets.first()
            }
        }
    }

    private fun setupIncognitoMode() {
        binding.settingsUseIncognitoModeHolder.beVisibleIf(isOreoPlus())
        binding.settingsUseIncognitoMode.isChecked = config.useIncognitoMode
        binding.settingsUseIncognitoModeHolder.setOnClickListener {
            binding.settingsUseIncognitoMode.toggle()
            config.useIncognitoMode = binding.settingsUseIncognitoMode.isChecked
        }
    }

    private fun setupNotesExport() {
        binding.settingsExportNotesHolder.setOnClickListener {
            ExportNotesDialog(this) { filename, encrypt ->
                if (encrypt) {
                    BackupPasswordDialog(this, confirm = true, titleRes = R.string.export_encrypted) { password ->
                        pendingExportPassword = password
                        saveDocument.launch(filename)
                    }
                } else {
                    pendingExportPassword = null
                    saveDocument.launch(filename)
                }
            }
        }
    }

    private fun setupNotesImport() {
        binding.settingsImportNotesHolder.setOnClickListener {
            getContent.launch(notesFileType)
        }
    }

    private fun exportNotes(notes: List<Note>, uri: Uri, password: String?) {
        if (notes.isEmpty()) {
            toast(com.simplemobiletools.commons.R.string.no_entries_for_exporting)
        } else {
            ensureBackgroundThread {
                val result = try {
                    val outputStream = contentResolver.openOutputStream(uri)!!
                    NotesHelper(this).exportNotes(notes, outputStream, password)
                } catch (e: Exception) {
                    showErrorToast(e)
                    ExportResult.EXPORT_FAIL
                }

                runOnUiThread {
                    if (result == ExportResult.EXPORT_OK) {
                        toast(com.simplemobiletools.commons.R.string.exporting_successful)
                    } else {
                        toast(com.simplemobiletools.commons.R.string.exporting_failed)
                    }
                }
            }
        }
    }

    private fun importNotes(uri: Uri) {
        try {
            val fileContent = contentResolver.openInputStream(uri)!!.use { inputStream ->
                inputStream.bufferedReader().readText()
            }

            if (NotesEncryptionHelper.isPasswordEncrypted(fileContent)) {
                BackupPasswordDialog(this, confirm = false, titleRes = R.string.import_notes) { password ->
                    val jsonString = NotesEncryptionHelper.decrypt(fileContent, password)
                    if (jsonString == null) {
                        toast(R.string.wrong_password)
                    } else {
                        parseAndImport(jsonString)
                    }
                }
            } else {
                val jsonString = NotesEncryptionHelper.decryptLegacy(fileContent) ?: fileContent
                parseAndImport(jsonString)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun parseAndImport(jsonString: String) {
        try {
            var notes: List<Note>
            var notebooks: List<Notebook> = emptyList()
            var checklistSorting: List<Int?> = emptyList()
            try {
                val backupData = Json.decodeFromString<BackupData>(jsonString)
                notes = backupData.notes
                notebooks = backupData.notebooks
                checklistSorting = backupData.checklistSorting
                backupData.settings?.let { settings ->
                    config.autosaveNotes = settings.autosaveNotes
                    config.displaySuccess = settings.displaySuccess
                    config.clickableLinks = settings.clickableLinks
                    config.monospacedFont = settings.monospacedFont
                    config.showKeyboard = settings.showKeyboard
                    config.showNotePicker = settings.showNotePicker
                    config.showWordCount = settings.showWordCount
                    config.gravity = settings.gravity
                    config.placeCursorToEnd = settings.placeCursorToEnd
                    config.enableLineWrap = settings.enableLineWrap
                    config.useIncognitoMode = settings.useIncognitoMode
                    config.lastCreatedNoteType = settings.lastCreatedNoteType
                    config.moveDoneChecklistItems = settings.moveDoneChecklistItems
                    config.fontSizePercentage = settings.fontSizePercentage
                    config.addNewChecklistItemsTop = settings.addNewChecklistItemsTop
                    config.notebookColumns = settings.notebookColumns
                }
            } catch (e: Exception) {
                notes = Json.decodeFromString<List<Note>>(jsonString)
            }

            if (notes.isEmpty()) {
                toast(com.simplemobiletools.commons.R.string.no_entries_for_importing)
                return
            }
            NotesHelper(this).importNotes(this, notes, notebooks, checklistSorting) { importResult ->
                when (importResult) {
                    NotesHelper.ImportResult.IMPORT_OK -> toast(com.simplemobiletools.commons.R.string.importing_successful)
                    NotesHelper.ImportResult.IMPORT_PARTIAL -> toast(com.simplemobiletools.commons.R.string.importing_some_entries_failed)
                    NotesHelper.ImportResult.IMPORT_NOTHING_NEW -> toast(com.simplemobiletools.commons.R.string.no_new_items)
                    else -> toast(com.simplemobiletools.commons.R.string.importing_failed)
                }
            }
        } catch (_: SerializationException) {
            toast(com.simplemobiletools.commons.R.string.invalid_file_format)
        } catch (_: IllegalArgumentException) {
            toast(com.simplemobiletools.commons.R.string.invalid_file_format)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun setupEnableAutomaticBackups() {
        binding.settingsBackupsLabel.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackupsHolder.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackups.isChecked = config.autoBackup
        binding.settingsEnableAutomaticBackupsHolder.setOnClickListener {
            val wasBackupDisabled = !config.autoBackup
            if (wasBackupDisabled) {
                ManageAutoBackupsDialog(
                    activity = this,
                    onSuccess = {
                        enableOrDisableAutomaticBackups(true)
                        scheduleNextAutomaticBackup()
                    }
                )
            } else {
                cancelScheduledAutomaticBackup()
                enableOrDisableAutomaticBackups(false)
            }
        }
    }

    private fun setupManageAutomaticBackups() {
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(isRPlus() && config.autoBackup)
        binding.settingsManageAutomaticBackupsHolder.setOnClickListener {
            ManageAutoBackupsDialog(
                activity = this,
                onSuccess = {
                    scheduleNextAutomaticBackup()
                }
            )
        }
    }

    private fun enableOrDisableAutomaticBackups(enable: Boolean) {
        config.autoBackup = enable
        binding.settingsEnableAutomaticBackups.isChecked = enable
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(enable)
    }

    private fun setupUseRecycleBin() {
        binding.settingsUseRecycleBin.isChecked = config.useRecycleBin
        binding.settingsUseRecycleBinHolder.setOnClickListener {
            binding.settingsUseRecycleBin.toggle()
            config.useRecycleBin = binding.settingsUseRecycleBin.isChecked
        }
    }

    private fun setupEmptyRecycleBin() {
        RecycleBinHelper(this).hasItems { hasItems ->
            if (isDestroyed || isFinishing) {
                return@hasItems
            }
            binding.settingsEmptyRecycleBinHolder.beVisibleIf(hasItems)
        }
        binding.settingsEmptyRecycleBinHolder.setOnClickListener {
            ConfirmationDialog(
                this,
                "",
                R.string.empty_recycle_bin_confirm,
                com.simplemobiletools.commons.R.string.delete,
                com.simplemobiletools.commons.R.string.cancel
            ) {
                RecycleBinHelper(this).emptyRecycleBin {
                    toast(R.string.recycle_bin_emptied)
                    binding.settingsEmptyRecycleBinHolder.beGone()
                }
            }
        }
    }
}
