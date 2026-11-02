package tomato.notes.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import tomato.notes.extensions.backupNotes

class AutomaticBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "simplenotes:automaticbackupreceiver")
        // The timeout is only a safety net, the lock is released as soon as the backup finishes
        wakelock.acquire(60_000L)
        // Keep the process alive until the background backup is done
        val pendingResult = goAsync()
        try {
            context.backupNotes {
                if (wakelock.isHeld) {
                    wakelock.release()
                }
                pendingResult.finish()
            }
        } catch (e: Exception) {
            if (wakelock.isHeld) {
                wakelock.release()
            }
            pendingResult.finish()
        }
    }
}
