package tomato.notes.dialogs

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import tomato.notes.R

class DateTimePatternInfoDialog(activity: BaseSimpleActivity) {

    init {
        val view = activity.layoutInflater.inflate(R.layout.datetime_pattern_info_layout, null)
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ -> { } }
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
