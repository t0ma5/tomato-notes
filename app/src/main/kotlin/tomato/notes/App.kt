package tomato.notes

import android.app.Application
import com.simplemobiletools.commons.extensions.checkUseEnglish
import tomato.notes.extensions.config

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        config.applyDefaultDarkRedThemeIfNeeded()
        config.applyDefaultWidgetColorsIfNeeded()
    }
}
