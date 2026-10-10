package tomato.notes.helpers

import android.content.Context
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import tomato.notes.extensions.config
import tomato.notes.models.ChecklistItem
import tomato.notes.models.CounterItem
import tomato.notes.models.Note
import tomato.notes.models.NoteType

private const val PREVIEW_LINE_COUNT = 4

fun Note.previewLines(context: Context): List<String> {
    if (isLocked()) {
        return emptyList()
    }

    val lines = when (type) {
        NoteType.TYPE_TEXT -> {
            getNoteStoredValue(context)
                ?.lineSequence()
                ?.map { it.trimEnd() }
                ?.filter { it.isNotBlank() }
                ?.toList()
                .orEmpty()
        }
        NoteType.TYPE_CHECKLIST -> checklistPreviewItems(context).map { "• ${it.title}" }
        NoteType.TYPE_COUNTER -> {
            val counterItemType = object : TypeToken<List<CounterItem>>() {}.type
            val rawValue = getNoteStoredValue(context)?.ifEmpty { "[]" } ?: "[]"
            val items = Gson().fromJson<List<CounterItem>>(rawValue, counterItemType) ?: listOf()
            items.map { "${it.title}: ${it.count}" }
        }
    }
    return lines.take(PREVIEW_LINE_COUNT)
}

fun Note.previewText(context: Context): CharSequence? {
    if (isLocked()) {
        return null
    }

    return when (type) {
        NoteType.TYPE_TEXT,
        NoteType.TYPE_COUNTER -> previewLines(context).joinToString(separator = "\n").ifBlank { null }
        NoteType.TYPE_CHECKLIST -> {
            val items = checklistPreviewItems(context)
            if (items.isEmpty()) {
                return null
            }
            val linePrefix = "• "
            val stringifiedItems = items.joinToString(separator = System.lineSeparator()) {
                "${linePrefix}${it.title}"
            }
            val formattedText = SpannableString(stringifiedItems)
            var currentPos = 0
            items.forEach { item ->
                currentPos += linePrefix.length
                if (item.isDone) {
                    formattedText.setSpan(StrikethroughSpan(), currentPos, currentPos + item.title.length, 0)
                }
                currentPos += item.title.length
                currentPos += System.lineSeparator().length
            }
            formattedText
        }
    }
}

private fun Note.checklistPreviewItems(context: Context): List<ChecklistItem> {
    val checklistItemType = object : TypeToken<List<ChecklistItem>>() {}.type
    var items = Gson().fromJson<List<ChecklistItem>>(getNoteStoredValue(context), checklistItemType) ?: listOf()
    items = ChecklistItem.sorted(
        items.filter { it.title != null },
        context.config.getChecklistSorting(id),
        context.config.moveDoneChecklistItems
    )
    return items.take(PREVIEW_LINE_COUNT)
}
