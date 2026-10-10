package tomato.notes.helpers

import android.content.Context
import com.google.gson.Gson
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
            if (notebook?.isLocked() == true && !note.title.lowercase().contains(needle) && !note.tags.lowercase().contains(needle)) {
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
        return context.notesDB.getNotes().filter { note ->
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
        return context.notesDB.getNotes()
            .flatMap { it.tagList() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
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
