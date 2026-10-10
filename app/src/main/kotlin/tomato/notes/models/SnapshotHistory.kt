package tomato.notes.models

class SnapshotHistory {
    private val past = ArrayDeque<String>()
    private val future = ArrayDeque<String>()

    fun canUndo() = past.isNotEmpty()

    fun canRedo() = future.isNotEmpty()

    fun push(current: String) {
        if (past.lastOrNull() == current) {
            return
        }
        past.addLast(current)
        future.clear()
        while (past.size > 50) {
            past.removeFirst()
        }
    }

    fun undo(current: String): String? {
        if (past.isEmpty()) {
            return null
        }
        val previous = past.removeLast()
        future.addFirst(current)
        return previous
    }

    fun redo(current: String): String? {
        if (future.isEmpty()) {
            return null
        }
        val next = future.removeFirst()
        past.addLast(current)
        return next
    }
}
