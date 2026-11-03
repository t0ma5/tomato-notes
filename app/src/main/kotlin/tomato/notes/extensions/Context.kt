package tomato.notes.extensions

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.app.AlarmManagerCompat
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ExportResult
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import tomato.notes.R
import tomato.notes.databases.NotesDatabase
import tomato.notes.dialogs.UnlockNotesDialog
import tomato.notes.helpers.*
import tomato.notes.interfaces.NotebooksDao
import tomato.notes.interfaces.NotesDao
import tomato.notes.interfaces.WidgetsDao
import tomato.notes.models.Note
import tomato.notes.receivers.AutomaticBackupReceiver
import org.joda.time.DateTime
import java.io.File
import java.io.FileOutputStream

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.notesDB: NotesDao get() = NotesDatabase.getInstance(applicationContext).NotesDao()

val Context.notebooksDB: NotebooksDao get() = NotesDatabase.getInstance(applicationContext).NotebooksDao()

val Context.widgetsDB: WidgetsDao get() = NotesDatabase.getInstance(applicationContext).WidgetsDao()

fun Context.updateWidgets() {
    val widgetIDs = AppWidgetManager.getInstance(applicationContext)?.getAppWidgetIds(ComponentName(applicationContext, MyWidgetProvider::class.java)) ?: return
    if (widgetIDs.isNotEmpty()) {
        Intent(applicationContext, MyWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIDs)
            sendBroadcast(this)
        }
    }

    val notebookWidgetIDs = AppWidgetManager.getInstance(applicationContext)?.getAppWidgetIds(ComponentName(applicationContext, NotebookWidgetProvider::class.java)) ?: intArrayOf()
    if (notebookWidgetIDs.isNotEmpty()) {
        Intent(applicationContext, NotebookWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, notebookWidgetIDs)
            sendBroadcast(this)
        }
    }
}

fun Context.getPercentageFontSize() = resources.getDimension(com.simplemobiletools.commons.R.dimen.middle_text_size) * (config.fontSizePercentage / 100f)

fun Context.getCardSurfaceColor(): Int {
    val bg = getProperBackgroundColor()
    // System default (Material You): a tonal card made from the wallpaper colors instead of fixed greys
    if (config.isUsingSystemTheme) {
        return ColorUtils.blendARGB(bg, getProperTextColor(), 0.06f)
    }
    return if (bg.getContrastColor() == Color.WHITE) {
        resources.getColor(R.color.note_card_surface, theme)
    } else {
        Color.WHITE
    }
}

fun Context.getMutedTextColor(): Int {
    if (config.isUsingSystemTheme) {
        return getProperTextColor().adjustAlpha(0.7f)
    }
    return if (getProperBackgroundColor().getContrastColor() == Color.WHITE) {
        resources.getColor(R.color.note_card_muted, theme)
    } else {
        getProperTextColor().adjustAlpha(0.65f)
    }
}

fun View.applyNoteCardBackground() {
    val surface = context.getCardSurfaceColor()
    val stroke = if (context.config.isUsingSystemTheme) {
        ColorUtils.blendARGB(context.getProperBackgroundColor(), context.getProperTextColor(), 0.14f)
    } else if (surface == Color.WHITE) {
        Color.parseColor("#FFE0E0E0")
    } else {
        context.resources.getColor(R.color.note_card_stroke, context.theme)
    }
    val radius = 10 * resources.displayMetrics.density
    background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(surface)
        cornerRadius = radius
        setStroke((1 * resources.displayMetrics.density).toInt().coerceAtLeast(1), stroke)
    }
}

fun BaseSimpleActivity.requestUnlockNotes(notes: List<Note>, callback: (unlockedNotes: List<Note>) -> Unit) {
    val lockedNotes = notes.filter { it.isLocked() }
    if (lockedNotes.isNotEmpty()) {
        runOnUiThread {
            UnlockNotesDialog(this, lockedNotes, callback)
        }
    } else {
        callback(emptyList())
    }
}

fun Context.getAutomaticBackupIntent(): PendingIntent {
    val intent = Intent(this, AutomaticBackupReceiver::class.java)
    return PendingIntent.getBroadcast(this, AUTOMATIC_BACKUP_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.scheduleNextAutomaticBackup() {
    if (config.autoBackup) {
        val backupAtMillis = getNextAutoBackupTime().millis
        val pendingIntent = getAutomaticBackupIntent()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, backupAtMillis, pendingIntent)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Context.cancelScheduledAutomaticBackup() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(getAutomaticBackupIntent())
}

// onDone runs once on a background thread when nothing more is left to do, so receivers can finish goAsync()
fun Context.checkAndBackupNotesOnBoot(onDone: (() -> Unit)? = null) {
    if (config.autoBackup) {
        val previousRealBackupTime = config.lastAutoBackupTime
        val previousScheduledBackupTime = getPreviousAutoBackupTime().millis
        val missedPreviousBackup = previousRealBackupTime < previousScheduledBackupTime
        if (missedPreviousBackup) {
            // device was probably off at the scheduled time so backup now
            backupNotes(onDone = onDone)
            return
        }
    }
    onDone?.invoke()
}

// onDone runs once on a background thread after the backup attempt, whatever its result.
// notifySuccess shows a toast when the file was written, for backups the user started
fun Context.backupNotes(notifySuccess: Boolean = false, onDone: (() -> Unit)? = null) {
    if (!isRPlus()) {
        onDone?.invoke()
        return
    }

    ensureBackgroundThread {
        try {
            backupNotesSync(notifySuccess)
        } catch (e: Exception) {
            showErrorToast(e)
            scheduleNextAutomaticBackup()
        } finally {
            onDone?.invoke()
        }
    }
}

// Runs on a background thread: Room refuses main-thread queries and exportNotes reads notebooks
private fun Context.backupNotesSync(notifySuccess: Boolean) {
    val notesToBackup = NotesHelper(this).getNotesSync()
    if (notesToBackup.isEmpty()) {
        toast(com.simplemobiletools.commons.R.string.no_entries_for_exporting)
        config.lastAutoBackupTime = DateTime.now().millis
        scheduleNextAutomaticBackup()
        return
    }

    val now = DateTime.now()
    val year = now.year.toString()
    val month = now.monthOfYear.ensureTwoDigits()
    val day = now.dayOfMonth.ensureTwoDigits()
    val hours = now.hourOfDay.ensureTwoDigits()
    val minutes = now.minuteOfHour.ensureTwoDigits()
    val seconds = now.secondOfMinute.ensureTwoDigits()

    val filename = config.autoBackupFilename
        .replace("%Y", year, false)
        .replace("%M", month, false)
        .replace("%D", day, false)
        .replace("%h", hours, false)
        .replace("%m", minutes, false)
        .replace("%s", seconds, false)

    val outputFolder = File(config.autoBackupFolder).apply {
        mkdirs()
    }

    var exportFile = File(outputFolder, "$filename.json")
    var exportFilePath = exportFile.absolutePath
    val outputStream = try {
        if (hasProperStoredFirstParentUri(exportFilePath)) {
            val exportFileUri = createDocumentUriUsingFirstParentTreeUri(exportFilePath)
            if (!getDoesFilePathExist(exportFilePath)) {
                createSAFFileSdk30(exportFilePath)
            }
            applicationContext.contentResolver.openOutputStream(exportFileUri, "wt") ?: FileOutputStream(exportFile)
        } else {
            var num = 0
            while (getDoesFilePathExist(exportFilePath) && !exportFile.canWrite()) {
                num++
                exportFile = File(outputFolder, "${filename}_${num}.json")
                exportFilePath = exportFile.absolutePath
            }
            FileOutputStream(exportFile)
        }
    } catch (e: Exception) {
        showErrorToast(e)
        scheduleNextAutomaticBackup()
        return
    }

    // exportNotes closes the stream on every path
    val exportResult = NotesHelper(this).exportNotes(notesToBackup, outputStream)
    if (exportResult == ExportResult.EXPORT_OK) {
        config.lastAutoBackupTime = DateTime.now().millis
        if (notifySuccess) {
            toast(com.simplemobiletools.commons.R.string.exporting_successful)
        }
    } else {
        toast(com.simplemobiletools.commons.R.string.exporting_failed)
    }
    scheduleNextAutomaticBackup()
}
