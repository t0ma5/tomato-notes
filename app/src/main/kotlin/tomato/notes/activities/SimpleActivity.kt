package tomato.notes.activities

import android.content.Intent
import androidx.appcompat.widget.Toolbar
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.activities.CustomizationActivity
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.helpers.APP_ICON_IDS
import com.simplemobiletools.commons.helpers.APP_LAUNCHER_NAME
import com.simplemobiletools.commons.helpers.LICENSE_GSON
import com.simplemobiletools.commons.helpers.LICENSE_KOTLINX_SERIALIZATION
import com.simplemobiletools.commons.helpers.LICENSE_MARKWON
import com.simplemobiletools.commons.helpers.LICENSE_RTL
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.models.FAQItem
import tomato.notes.BuildConfig
import tomato.notes.R

open class SimpleActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = arrayListOf(
            R.mipmap.ic_launcher_red,
            R.mipmap.ic_launcher_pink,
            R.mipmap.ic_launcher_purple,
            R.mipmap.ic_launcher_deep_purple,
            R.mipmap.ic_launcher_indigo,
            R.mipmap.ic_launcher_blue,
            R.mipmap.ic_launcher_light_blue,
            R.mipmap.ic_launcher_cyan,
            R.mipmap.ic_launcher_teal,
            R.mipmap.ic_launcher_green,
            R.mipmap.ic_launcher_light_green,
            R.mipmap.ic_launcher_lime,
            R.mipmap.ic_launcher_yellow,
            R.mipmap.ic_launcher_amber,
            R.mipmap.ic_launcher,
            R.mipmap.ic_launcher_deep_orange,
            R.mipmap.ic_launcher_brown,
            R.mipmap.ic_launcher_blue_grey,
            R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    fun setupPrimaryToolbar(toolbar: Toolbar, navigationIcon: NavigationIcon = NavigationIcon.None) {
        setupToolbar(toolbar, navigationIcon, getProperPrimaryColor())
    }

    fun openColorCustomization() {
        Intent(this, CustomizationActivity::class.java).apply {
            putExtra(APP_ICON_IDS, getAppIconIDs())
            putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
            startActivity(this)
        }
    }

    fun launchAbout() {
        val licenses = LICENSE_RTL or LICENSE_GSON or LICENSE_MARKWON or LICENSE_KOTLINX_SERIALIZATION
        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(com.simplemobiletools.commons.R.string.faq_1_title_commons, com.simplemobiletools.commons.R.string.faq_1_text_commons),
        )
        if (!resources.getBoolean(com.simplemobiletools.commons.R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(com.simplemobiletools.commons.R.string.faq_2_title_commons, com.simplemobiletools.commons.R.string.faq_2_text_commons))
            faqItems.add(FAQItem(com.simplemobiletools.commons.R.string.faq_6_title_commons, com.simplemobiletools.commons.R.string.faq_6_text_commons))
        }
        startAboutActivity(R.string.app_name, licenses, "${BuildConfig.VERSION_NAME} (${aboutMonthYear()})", faqItems, true)
    }

    private fun aboutMonthYear(): String {
        val calendar = java.util.Calendar.getInstance()
        val month = java.text.DateFormatSymbols(java.util.Locale.US).months[calendar.get(java.util.Calendar.MONTH)]
        return "$month ${calendar.get(java.util.Calendar.YEAR)}"
    }
}
