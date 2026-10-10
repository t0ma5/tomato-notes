package tomato.notes.interfaces

import androidx.room.*
import tomato.notes.models.Note

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes WHERE deleted_ts = 0 ORDER BY sort_order ASC, title COLLATE UNICODE ASC")
    fun getNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE notebook_id = :notebookId AND deleted_ts = 0 ORDER BY sort_order ASC, title COLLATE UNICODE ASC")
    fun getNotesInNotebook(notebookId: Long): List<Note>

    @Query("SELECT MAX(sort_order) FROM notes WHERE deleted_ts = 0")
    fun getMaxSortOrder(): Int?

    @Query("SELECT * FROM notes WHERE deleted_ts > 0 ORDER BY deleted_ts DESC")
    fun getDeletedNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteWithId(id: Long): Note?

    @Query("SELECT id FROM notes WHERE path = :path AND deleted_ts = 0")
    fun getNoteIdWithPath(path: String): Long?

    @Query("SELECT id FROM notes WHERE title = :title COLLATE NOCASE AND deleted_ts = 0")
    fun getNoteIdWithTitle(title: String): Long?

    @Query("SELECT id FROM notes WHERE notebook_id = :notebookId AND title = :title COLLATE NOCASE AND deleted_ts = 0")
    fun getNoteIdWithTitleInNotebook(title: String, notebookId: Long): Long?

    @Query("SELECT id FROM notes WHERE title = :title AND deleted_ts = 0")
    fun getNoteIdWithTitleCaseSensitive(title: String): Long?

    @Query("SELECT id FROM notes WHERE notebook_id = :notebookId AND title = :title AND deleted_ts = 0")
    fun getNoteIdWithTitleCaseSensitiveInNotebook(title: String, notebookId: Long): Long?

    @Query("UPDATE notes SET notebook_id = :targetNotebookId WHERE notebook_id = :sourceNotebookId")
    fun moveNotesToNotebook(sourceNotebookId: Long, targetNotebookId: Long)

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    fun updatePinned(id: Long, pinned: Int)

    @Query("UPDATE notes SET sort_order = :sortOrder WHERE id = :id")
    fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE notes SET deleted_ts = :deletedTs WHERE id = :id")
    fun updateDeletedTs(id: Long, deletedTs: Long)

    @Query("UPDATE notes SET deleted_ts = :deletedTs WHERE notebook_id = :notebookId AND deleted_ts = 0")
    fun markNotesDeletedInNotebook(notebookId: Long, deletedTs: Long)

    @Query("UPDATE notes SET deleted_ts = 0 WHERE id = :id")
    fun restoreNote(id: Long)

    @Query("UPDATE notes SET deleted_ts = 0 WHERE notebook_id = :notebookId AND deleted_ts = :deletedTs")
    fun restoreNotesInNotebook(notebookId: Long, deletedTs: Long)

    @Query("DELETE FROM notes WHERE deleted_ts > 0 AND deleted_ts < :olderThan")
    fun permanentlyDeleteNotesOlderThan(olderThan: Long)

    @Query("DELETE FROM notes WHERE deleted_ts > 0")
    fun permanentlyDeleteAllDeletedNotes()

    @Query("DELETE FROM notes WHERE notebook_id = :notebookId")
    fun permanentlyDeleteNotesInNotebook(notebookId: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE deleted_ts > 0")
    fun getDeletedNoteCount(): Int

    @Query(
        """
        INSERT INTO notes (notebook_id, title, value, type, path, protection_type, protection_hash)
        SELECT :notebookId, :title, :value, :type, :path, :protectionType, :protectionHash
        WHERE NOT EXISTS (SELECT 1 FROM notes WHERE notebook_id = :notebookId AND deleted_ts = 0)
        """
    )
    fun insertNoteIfNotebookEmpty(
        notebookId: Long,
        title: String,
        value: String,
        type: Int,
        path: String,
        protectionType: Int,
        protectionHash: String
    )

    @Query(
        """
        DELETE FROM notes
        WHERE notebook_id = :notebookId
          AND title = :title COLLATE NOCASE
          AND value = ''
          AND path = ''
          AND type = :type
          AND deleted_ts = 0
          AND id NOT IN (
              SELECT MIN(id)
              FROM notes
              WHERE notebook_id = :notebookId
                AND title = :title COLLATE NOCASE
                AND value = ''
                AND path = ''
                AND type = :type
                AND deleted_ts = 0
          )
        """
    )
    fun deleteDuplicateEmptyNotesInNotebook(
        notebookId: Long,
        title: String,
        type: Int
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(note: Note): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(notes: List<Note>): List<Long>

    @Delete
    fun deleteNote(note: Note)
}
