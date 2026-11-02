package tomato.notes.activities

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    // Keeps locked content out of screenshots, screen recordings and the recents preview
    fun setSecureContent(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Commons only applies the top inset. Pads the view by the navigation bar, display cutout and keyboard
    // insets on the bottom, left and right, on top of its own padding from the layout.
    fun applyBottomInsetsAsPadding(view: View) {
        val baseLeft = view.paddingLeft
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(baseLeft + bars.left, v.paddingTop, baseRight + bars.right, baseBottom + maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    // Same as above for floating buttons, using their margins
    fun applyBottomInsetsAsMargin(view: View) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val baseLeft = params.leftMargin
        val baseRight = params.rightMargin
        val baseBottom = params.bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.leftMargin = baseLeft + bars.left
                it.rightMargin = baseRight + bars.right
                it.bottomMargin = baseBottom + maxOf(bars.bottom, ime.bottom)
                v.layoutParams = it
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)
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
