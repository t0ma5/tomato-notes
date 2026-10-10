package tomato.notes.models

import android.content.Context
import android.net.Uri
import androidx.room.*
import com.simplemobiletools.commons.extensions.isBiometricIdAvailable
import com.simplemobiletools.commons.helpers.PROTECTION_FINGERPRINT
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Represents a note.
 *
 * @property value The content of the note. Could be plain text or [ChecklistItem]
 * @property type The type of the note. Should be one of the [NoteType] enum entries.
 */
@Serializable
@Entity(tableName = "notes", indices = [(Index(value = ["id"], unique = true))])
@TypeConverters(NoteTypeConverter::class)
data class Note(
    @PrimaryKey(autoGenerate = true) var id: Long? = null,
    @ColumnInfo(name = "notebook_id") var notebookId: Long = 1L,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "value") var value: String,
    @ColumnInfo(name = "type") var type: NoteType,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "protection_type") var protectionType: Int,
    @ColumnInfo(name = "protection_hash") var protectionHash: String,
    @ColumnInfo(name = "pinned", defaultValue = "0") var pinned: Int = 0,
    @ColumnInfo(name = "deleted_ts", defaultValue = "0") var deletedTs: Long = 0L,
    @ColumnInfo(name = "tags", defaultValue = "") var tags: String = "",
    @ColumnInfo(name = "is_read_only", defaultValue = "0") var isReadOnly: Boolean = false,
    @ColumnInfo(name = "sort_order", defaultValue = "0") var sortOrder: Int = 0
) {
    @Ignore
    var notebookTitle: String? = null

    fun getNoteStoredValue(context: Context): String? {
        return if (path.isNotEmpty()) {
            try {
                if (path.startsWith("content://")) {
                    val inputStream = context.contentResolver.openInputStream(Uri.parse(path))
                    inputStream?.bufferedReader().use { it!!.readText() }
                } else {
                    File(path).readText()
                }
            } catch (e: Exception) {
                null
            }
        } else {
            value
        }
    }

    fun isLocked() = protectionType != PROTECTION_NONE

    fun isPinned() = pinned != 0

    fun isDeleted() = deletedTs > 0L

    fun tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun formattedTags(): String = tagList().joinToString(" · ")

    fun setTagList(values: List<String>) {
        tags = values.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(",")
    }

    fun shouldBeUnlocked(context: Context): Boolean {
        return protectionType == PROTECTION_FINGERPRINT && !context.isBiometricIdAvailable()
    }
}
