package tomato.notes.helpers

import tomato.notes.models.Notebook

// Notebooks unlocked since the app last went to the background. Cleared by App when the process stops.
object NotebookUnlockSession {
    private val unlocked = mutableSetOf<Long>()

    @Synchronized
    fun markUnlocked(notebookId: Long?) {
        notebookId?.let { unlocked.add(it) }
    }

    @Synchronized
    fun isUnlocked(notebookId: Long?): Boolean = notebookId != null && notebookId in unlocked

    // True when the notebook's contents may be shown right now
    fun canShow(notebook: Notebook?): Boolean = notebook == null || !notebook.isLocked() || isUnlocked(notebook.id)

    @Synchronized
    fun clear() {
        unlocked.clear()
    }
}
