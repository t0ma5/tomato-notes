package tomato.notes.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.getLaunchIntent
import com.simplemobiletools.commons.extensions.setText
import com.simplemobiletools.commons.extensions.setVisibleIf
import com.simplemobiletools.commons.helpers.WIDGET_TEXT_COLOR
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import tomato.notes.R
import tomato.notes.activities.SplashActivity
import tomato.notes.extensions.config
import tomato.notes.extensions.notebooksDB
import tomato.notes.extensions.notesDB
import tomato.notes.extensions.widgetsDB
import tomato.notes.models.Widget
import tomato.notes.services.WidgetService

class MyWidgetProvider : AppWidgetProvider() {
    private fun setupAppOpenIntent(context: Context, views: RemoteViews, id: Int, widget: Widget) {
        val intent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
        intent.putExtra(OPEN_NOTE_ID, widget.noteId)
        val pendingIntent = PendingIntent.getActivity(context, widget.widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun getMissingWidget(context: Context, widgetId: Int) =
        Widget(null, widgetId, 0L, context.config.widgetBgColor, context.config.widgetTextColor, false)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        ensureBackgroundThread {
            for (widgetId in appWidgetIds) {
                // A widget without a stored row still gets drawn, showing the "deleted" state instead of stale content
                val widget = context.widgetsDB.getWidgetWithWidgetId(widgetId) ?: getMissingWidget(context, widgetId)
                if (widget.isNotebookWidget()) {
                    continue
                }
                val views = RemoteViews(context.packageName, R.layout.widget)
                val note = context.notesDB.getNoteWithId(widget.noteId)?.takeUnless { it.isDeleted() }
                // Titles of notes in a locked notebook stay hidden on the home screen
                val notebook = note?.let { context.notebooksDB.getNotebookWithId(it.notebookId) }
                val title = if (note == null || notebook?.isLocked() == true) "" else note.title
                views.applyColorFilter(R.id.notes_widget_background, widget.widgetBgColor)
                views.setTextColor(R.id.widget_note_title, widget.widgetTextColor)
                views.setText(R.id.widget_note_title, title)
                views.setVisibleIf(R.id.widget_note_title, widget.widgetShowTitle)
                setupAppOpenIntent(context, views, R.id.notes_widget_holder, widget)

                Intent(context, WidgetService::class.java).apply {
                    putExtra(NOTE_ID, widget.noteId)
                    putExtra(WIDGET_TEXT_COLOR, widget.widgetTextColor)
                    data = Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME))
                    views.setRemoteAdapter(R.id.notes_widget_listview, this)
                }

                val startActivityIntent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
                startActivityIntent.putExtra(OPEN_NOTE_ID, widget.noteId)
                val startActivityPendingIntent =
                    PendingIntent.getActivity(context, widgetId, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setPendingIntentTemplate(R.id.notes_widget_listview, startActivityPendingIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.notes_widget_listview)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        ensureBackgroundThread {
            appWidgetIds.forEach {
                context.widgetsDB.deleteWidgetId(it)
            }
        }
    }
}
