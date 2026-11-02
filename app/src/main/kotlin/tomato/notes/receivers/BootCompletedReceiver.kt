package tomato.notes.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import tomato.notes.extensions.checkAndBackupNotesOnBoot
import tomato.notes.extensions.config
import tomato.notes.extensions.scheduleNextAutomaticBackup

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Keep the process alive until the background work is done
        val pendingResult = goAsync()
        ensureBackgroundThread {
            try {
                context.apply {
                    // Alarms are cleared on reboot. Same gate as the settings screen: Android 11+ with auto backups on
                    if (isRPlus() && config.autoBackup) {
                        scheduleNextAutomaticBackup()
                    }
                    checkAndBackupNotesOnBoot {
                        pendingResult.finish()
                    }
                }
            } catch (e: Exception) {
                pendingResult.finish()
            }
        }
    }
}
