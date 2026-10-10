package tomato.notes.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplemobiletools.commons.helpers.PROTECTION_NONE
import tomato.notes.R
import tomato.notes.helpers.DEFAULT_WIDGET_TEXT_COLOR
import tomato.notes.interfaces.NotebooksDao
import tomato.notes.interfaces.NotesDao
import tomato.notes.interfaces.WidgetsDao
import tomato.notes.models.Notebook
import tomato.notes.models.Note
import tomato.notes.models.Widget
import java.util.concurrent.Executors

@Database(entities = [Note::class, Notebook::class, Widget::class], version = 10, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun NotebooksDao(): NotebooksDao

    abstract fun NotesDao(): NotesDao

    abstract fun WidgetsDao(): WidgetsDao

    companion object {
        private var db: NotesDatabase? = null
        private var defaultWidgetBgColor = 0

        fun getInstance(context: Context): NotesDatabase {
            defaultWidgetBgColor = context.resources.getColor(com.simplemobiletools.commons.R.color.default_widget_bg_color)
            if (db == null) {
                synchronized(NotesDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, NotesDatabase::class.java, "notes.db")
                            .addCallback(object : Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    insertFirstNote(context)
                                }
                            })
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .addMigrations(MIGRATION_4_5)
                            .addMigrations(MIGRATION_5_6)
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .build()
                        db!!.openHelper.setWriteAheadLoggingEnabled(true)
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }

        private fun insertFirstNote(context: Context) {
            Executors.newSingleThreadScheduledExecutor().execute {
                val defaultNotebookTitle = context.getString(R.string.app_launcher_name)
                db!!.openHelper.writableDatabase.execSQL(
                    "INSERT OR IGNORE INTO notebooks(id, title, protection_type, protection_hash, pinned, sort_order) VALUES(1, ?, $PROTECTION_NONE, '', 0, 0)",
                    arrayOf(defaultNotebookTitle)
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("ALTER TABLE widgets ADD COLUMN widget_bg_color INTEGER NOT NULL DEFAULT $defaultWidgetBgColor")
                    execSQL("ALTER TABLE widgets ADD COLUMN widget_text_color INTEGER NOT NULL DEFAULT $DEFAULT_WIDGET_TEXT_COLOR")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("ALTER TABLE notes ADD COLUMN protection_type INTEGER DEFAULT $PROTECTION_NONE NOT NULL")
                    execSQL("ALTER TABLE notes ADD COLUMN protection_hash TEXT DEFAULT '' NOT NULL")
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE widgets ADD COLUMN widget_show_title INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS notebooks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, protection_type INTEGER NOT NULL DEFAULT $PROTECTION_NONE, protection_hash TEXT NOT NULL DEFAULT '')"
                )
                database.execSQL("INSERT OR IGNORE INTO notebooks(id, title, protection_type, protection_hash) VALUES(1, 'General note', $PROTECTION_NONE, '')")
                database.execSQL("ALTER TABLE notes ADD COLUMN notebook_id INTEGER NOT NULL DEFAULT 1")
                database.execSQL("UPDATE notes SET notebook_id = 1 WHERE notebook_id IS NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!tableHasColumn(database, tableName = "notebooks", columnName = "pinned")) {
                    database.execSQL("ALTER TABLE notebooks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                }

                if (!tableHasColumn(database, tableName = "notebooks", columnName = "sort_order")) {
                    database.execSQL("ALTER TABLE notebooks ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
                }

                database.execSQL("UPDATE notebooks SET sort_order = id WHERE sort_order = 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!tableHasColumn(database, tableName = "notes", columnName = "pinned")) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                }
                if (!tableHasColumn(database, tableName = "notes", columnName = "deleted_ts")) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN deleted_ts INTEGER NOT NULL DEFAULT 0")
                }
                if (!tableHasColumn(database, tableName = "notebooks", columnName = "deleted_ts")) {
                    database.execSQL("ALTER TABLE notebooks ADD COLUMN deleted_ts INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!tableHasColumn(database, tableName = "notes", columnName = "tags")) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                }
                if (!tableHasColumn(database, tableName = "widgets", columnName = "notebook_id")) {
                    database.execSQL("ALTER TABLE widgets ADD COLUMN notebook_id INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!tableHasColumn(database, tableName = "notes", columnName = "is_read_only")) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN is_read_only INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!tableHasColumn(database, tableName = "notes", columnName = "sort_order")) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
                }
                database.execSQL("UPDATE notes SET sort_order = id WHERE sort_order = 0")
            }
        }

        private fun tableHasColumn(database: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            val cursor = database.query("PRAGMA table_info($tableName)")
            cursor.use {
                val nameColumnIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumnIndex) == columnName) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
