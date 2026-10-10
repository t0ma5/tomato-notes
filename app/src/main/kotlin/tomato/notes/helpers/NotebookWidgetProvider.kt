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
import tomato.notes.extensions.notebooksDB
import tomato.notes.extensions.widgetsDB
import tomato.notes.models.Widget
import tomato.notes.services.WidgetService

class NotebookWidgetProvider : AppWidgetProvider() {
    private fun setupAppOpenIntent(context: Context, views: RemoteViews, id: Int, widget: Widget) {
        val intent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
        intent.putExtra(NOTEBOOK_ID, widget.notebookId)
        val pendingIntent = PendingIntent.getActivity(context, widget.widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(id, pendingIntent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        ensureBackgroundThread {
            for (widgetId in appWidgetIds) {
                val widget = context.widgetsDB.getWidgetWithWidgetId(widgetId) ?: continue
                if (!widget.isNotebookWidget()) {
                    continue
                }
                val views = RemoteViews(context.packageName, R.layout.widget)
                val notebook = context.notebooksDB.getNotebookWithId(widget.notebookId)
                views.applyColorFilter(R.id.notes_widget_background, widget.widgetBgColor)
                views.setTextColor(R.id.widget_note_title, widget.widgetTextColor)
                views.setText(R.id.widget_note_title, notebook?.title ?: "")
                views.setVisibleIf(R.id.widget_note_title, widget.widgetShowTitle)
                setupAppOpenIntent(context, views, R.id.notes_widget_holder, widget)

                Intent(context, WidgetService::class.java).apply {
                    putExtra(NOTEBOOK_ID, widget.notebookId)
                    putExtra(WIDGET_TEXT_COLOR, widget.widgetTextColor)
                    data = Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME))
                    views.setRemoteAdapter(R.id.notes_widget_listview, this)
                }

                val startActivityIntent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
                val startActivityPendingIntent =
                    PendingIntent.getActivity(context, widgetId, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
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
