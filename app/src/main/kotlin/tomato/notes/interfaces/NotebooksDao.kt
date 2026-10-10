package tomato.notes.interfaces

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tomato.notes.models.Notebook

@Dao
interface NotebooksDao {
    @Query("SELECT * FROM notebooks WHERE deleted_ts = 0 ORDER BY pinned DESC, sort_order ASC, title COLLATE UNICODE ASC")
    fun getNotebooks(): List<Notebook>

    @Query("SELECT * FROM notebooks WHERE deleted_ts > 0 ORDER BY deleted_ts DESC")
    fun getDeletedNotebooks(): List<Notebook>

    @Query("SELECT MAX(sort_order) FROM notebooks WHERE pinned = :pinned AND deleted_ts = 0")
    fun getMaxSortOrder(pinned: Int): Int?

    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun getNotebookWithId(id: Long): Notebook?

    @Query("SELECT id FROM notebooks WHERE title = :title COLLATE NOCASE AND deleted_ts = 0")
    fun getNotebookIdWithTitle(title: String): Long?

    @Query("SELECT id FROM notebooks WHERE title = :title AND deleted_ts = 0")
    fun getNotebookIdWithTitleCaseSensitive(title: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(notebook: Notebook): Long

    @Query("UPDATE notebooks SET pinned = :pinned WHERE id = :id")
    fun updatePinned(id: Long, pinned: Int)

    @Query("UPDATE notebooks SET sort_order = :sortOrder WHERE id = :id")
    fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE notebooks SET deleted_ts = :deletedTs WHERE id = :id")
    fun updateDeletedTs(id: Long, deletedTs: Long)

    @Query("UPDATE notebooks SET deleted_ts = 0 WHERE id = :id")
    fun restoreNotebook(id: Long)

    @Query("DELETE FROM notebooks WHERE deleted_ts > 0 AND deleted_ts < :olderThan")
    fun permanentlyDeleteNotebooksOlderThan(olderThan: Long)

    @Query("DELETE FROM notebooks WHERE deleted_ts > 0")
    fun permanentlyDeleteAllDeletedNotebooks()

    @Query("SELECT COUNT(*) FROM notebooks WHERE deleted_ts > 0")
    fun getDeletedNotebookCount(): Int

    @Delete
    fun deleteNotebook(notebook: Notebook)
}
