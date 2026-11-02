package tomato.notes

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.simplemobiletools.commons.extensions.checkUseEnglish
import tomato.notes.extensions.config
import tomato.notes.helpers.NotebookUnlockSession

private const val UNLOCK_GRACE_MS = 60_000L

class App : Application() {
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        config.applyDefaultDarkRedThemeIfNeeded()
        config.applyDefaultWidgetColorsIfNeeded()
        registerActivityLifecycleCallbacks(UnlockSessionTracker())
    }

    // Forgets unlocked notebooks once the app was in the background for longer than UNLOCK_GRACE_MS, so coming back asks again.
    // The grace period keeps the share sheet, print UI and file pickers we open ourselves from re-locking the notebook.
    private inner class UnlockSessionTracker : ActivityLifecycleCallbacks {
        private var backgroundSince = 0L

        override fun onActivityStarted(activity: Activity) {
            if (startedActivities == 0 && backgroundSince != 0L && SystemClock.elapsedRealtime() - backgroundSince > UNLOCK_GRACE_MS) {
                NotebookUnlockSession.clear()
            }
            backgroundSince = 0L
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            if (startedActivities == 0 && !activity.isChangingConfigurations) {
                backgroundSince = SystemClock.elapsedRealtime()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
    }
}
