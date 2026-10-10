package tomato.notes.extensions

import android.app.Activity
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.isBlackAndWhiteTheme

fun AlertDialog.applyNotesDialogChrome(activity: Activity) {
    val textColor = activity.getProperTextColor()
    val primaryColor = activity.getProperPrimaryColor()
    val buttonColor = if (activity.baseConfig.isUsingSystemTheme) {
        primaryColor
    } else if (primaryColor == activity.baseConfig.backgroundColor) {
        textColor
    } else {
        primaryColor
    }

    getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(buttonColor)
    getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
    getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(buttonColor)

    if (activity.baseConfig.isUsingSystemTheme) {
        return
    }

    val background = when {
        activity.isBlackAndWhiteTheme() -> activity.resources.getDrawable(
            com.simplemobiletools.commons.R.drawable.black_dialog_background,
            activity.theme
        )
        else -> activity.resources.getColoredDrawableWithColor(
            com.simplemobiletools.commons.R.drawable.dialog_bg,
            activity.baseConfig.backgroundColor
        )
    }
    window?.setBackgroundDrawable(background)
}
