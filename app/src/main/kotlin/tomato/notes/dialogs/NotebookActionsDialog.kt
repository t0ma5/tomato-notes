package tomato.notes.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getProperBackgroundColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import tomato.notes.databinding.DialogNotebookActionsBinding
import tomato.notes.models.Notebook
import tomato.notes.R

class NotebookActionsDialog(
    activity: BaseSimpleActivity,
    notebook: Notebook,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
) {
    init {
        val binding = DialogNotebookActionsBinding.inflate(activity.layoutInflater)
        val background = activity.getProperBackgroundColor()
        val text = activity.getProperTextColor()
        val radius = 10 * activity.resources.displayMetrics.density
        binding.root.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(background)
            cornerRadius = radius
        }
        binding.actionRename.setTextColor(text)
        binding.actionPin.setTextColor(text)
        binding.actionLock.setTextColor(text)
        binding.actionDelete.setTextColor(text)
        binding.actionPin.setText(if (notebook.isPinned()) R.string.unpin else R.string.pin)
        binding.actionLock.setText(if (notebook.isLocked()) R.string.unlock else R.string.lock)

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        fun pick(action: () -> Unit) {
            dialog.dismiss()
            action()
        }
        binding.actionRename.setOnClickListener { pick(onRename) }
        binding.actionPin.setOnClickListener { pick(onPin) }
        binding.actionLock.setOnClickListener { pick(onLock) }
        binding.actionDelete.setOnClickListener { pick(onDelete) }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
