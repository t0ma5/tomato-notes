package tomato.notes.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.ImageView
import com.google.android.material.search.SearchView
import com.google.gson.Gson
import com.simplemobiletools.commons.extensions.adjustAlpha
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.getProperBackgroundColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.helpers.MEDIUM_ALPHA
import com.google.gson.reflect.TypeToken
import tomato.notes.R
import tomato.notes.extensions.notesDB
import tomato.notes.extensions.notebooksDB
import tomato.notes.models.ChecklistItem
import tomato.notes.models.CounterItem
import tomato.notes.models.Note
import tomato.notes.models.NoteType
import tomato.notes.models.Notebook

data class NoteSearchResult(
    val notebook: Notebook? = null,
    val note: Note? = null,
    val title: String,
    val subtitle: String
) {
    val isNotebook get() = notebook != null
}

// Paints a Material SearchView with the commons colors instead of the theme's surface colors, so it matches
// the rest of the app with any color scheme. Internal parts are looked up by name and skipped if missing.
fun SearchView.applyProperColors() {
    val backgroundColor = context.getProperBackgroundColor()
    val textColor = context.getProperTextColor()

    findMaterialView("open_search_view_background")?.setBackgroundColor(backgroundColor)
    findMaterialView("open_search_view_divider")?.setBackgroundColor(textColor.adjustAlpha(0.2f))
    (findMaterialView("open_search_view_clear_button") as? ImageView)?.applyColorFilter(textColor)
    editText.setTextColor(textColor)
    editText.setHintTextColor(textColor.adjustAlpha(MEDIUM_ALPHA))
    toolbar.navigationIcon?.applyColorFilter(textColor)
}

@SuppressLint("DiscouragedApi")
private fun SearchView.findMaterialView(name: String): View? {
    val id = resources.getIdentifier(name, "id", context.packageName)
    return if (id != 0) findViewById(id) else null
}

class NoteSearchHelper(private val context: Context) {
    fun search(query: String): List<NoteSearchResult> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            return emptyList()
        }

        val results = mutableListOf<NoteSearchResult>()
        val notebooks = context.notebooksDB.getNotebooks()
        val notebooksById = notebooks.associateBy { it.id }

        notebooks.filter { it.title.lowercase().contains(needle) }.forEach { notebook ->
            results.add(
                NoteSearchResult(
                    notebook = notebook,
                    title = notebook.title,
                    subtitle = context.getString(R.string.notebooks)
                )
            )
        }

        context.notesDB.getNotes().forEach { note ->
            val notebook = notebooksById[note.notebookId]
            // Notes in a locked notebook stay out of search, titles and tags included, until it is unlocked
            if (!NotebookUnlockSession.canShow(notebook)) {
                return@forEach
            }

            val searchableText = buildSearchableText(note, includeBody = notebook?.isLocked() != true && !note.isLocked())
            if (searchableText.contains(needle) || note.title.lowercase().contains(needle)) {
                results.add(
                    NoteSearchResult(
                        note = note,
                        title = note.title,
                        subtitle = notebook?.title ?: context.getString(R.string.notebooks)
                    )
                )
            }
        }

        return results
    }

    fun notesWithTag(tag: String): List<NoteSearchResult> {
        val needle = tag.trim().lowercase()
        if (needle.isEmpty()) {
            return emptyList()
        }

        val notebooksById = context.notebooksDB.getNotebooks().associateBy { it.id }
        return getVisibleNotes(notebooksById).filter { note ->
            note.tagList().any { it.lowercase() == needle }
        }.map { note ->
            NoteSearchResult(
                note = note,
                title = note.title,
                subtitle = listOfNotNull(notebooksById[note.notebookId]?.title, note.formattedTags().ifEmpty { null }).joinToString(" · ")
            )
        }
    }

    fun allTags(): List<String> {
        val notebooksById = context.notebooksDB.getNotebooks().associateBy { it.id }
        return getVisibleNotes(notebooksById)
            .flatMap { it.tagList() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    private fun getVisibleNotes(notebooksById: Map<Long?, Notebook>): List<Note> {
        return context.notesDB.getNotes().filter { NotebookUnlockSession.canShow(notebooksById[it.notebookId]) }
    }

    private fun buildSearchableText(note: Note, includeBody: Boolean): String {
        val builder = StringBuilder(note.title.lowercase())
        builder.append(' ').append(note.tags.lowercase())
        if (!includeBody) {
            return builder.toString()
        }

        val stored = note.getNoteStoredValue(context) ?: note.value
        when (note.type) {
            NoteType.TYPE_TEXT -> builder.append(' ').append(stored.lowercase())
            NoteType.TYPE_CHECKLIST -> {
                try {
                    val type = object : TypeToken<List<ChecklistItem>>() {}.type
                    val items = Gson().fromJson<List<ChecklistItem>>(stored.ifEmpty { "[]" }, type) ?: emptyList()
                    items.forEach { builder.append(' ').append(it.title.lowercase()) }
                } catch (_: Exception) {
                    builder.append(' ').append(stored.lowercase())
                }
            }
            NoteType.TYPE_COUNTER -> {
                try {
                    val type = object : TypeToken<List<CounterItem>>() {}.type
                    val items = Gson().fromJson<List<CounterItem>>(stored.ifEmpty { "[]" }, type) ?: emptyList()
                    items.forEach { builder.append(' ').append(it.title.lowercase()) }
                } catch (_: Exception) {
                    builder.append(' ').append(stored.lowercase())
                }
            }
        }
        return builder.toString()
    }
}
