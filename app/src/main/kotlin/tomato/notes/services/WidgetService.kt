package tomato.notes.services

import android.content.Intent
import android.widget.RemoteViewsService
import tomato.notes.adapters.WidgetAdapter

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = WidgetAdapter(applicationContext, intent)
}
