package tomato.notes.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import tomato.notes.extensions.checkAndBackupNotesOnBoot

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureBackgroundThread {
            context.apply {
                checkAndBackupNotesOnBoot()
            }
        }
    }
}
