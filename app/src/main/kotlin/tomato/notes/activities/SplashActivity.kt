package tomato.notes.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutManager
import com.simplemobiletools.commons.activities.BaseSplashActivity
import com.simplemobiletools.commons.helpers.isNougatMR1Plus
import tomato.notes.extensions.config
import tomato.notes.helpers.NOTEBOOK_ID
import tomato.notes.helpers.OPEN_NOTE_ID
import tomato.notes.helpers.SHORTCUT_NEW_CHECKLIST
import tomato.notes.helpers.SHORTCUT_NEW_TEXT_NOTE

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        removeLegacyDynamicShortcuts()

        // widgets send 0 when their note or notebook was deleted, open the app normally then
        val noteId = intent.getLongExtra(OPEN_NOTE_ID, -1L)
        val notebookId = intent.getLongExtra(NOTEBOOK_ID, 0L)
        when {
            noteId > 0L -> {
                Intent(this, MainActivity::class.java).apply {
                    putExtra(OPEN_NOTE_ID, noteId)
                    startActivity(this)
                }
            }
            notebookId > 0L -> {
                Intent(this, NotesListActivity::class.java).apply {
                    putExtra(NOTEBOOK_ID, notebookId)
                    startActivity(this)
                }
            }
            config.showNotebooks -> startActivity(Intent(this, NotebooksActivity::class.java))
            else -> startActivity(Intent(this, NotesListActivity::class.java))
        }
        finish()
    }

    // New note / New checklist are static shortcuts now (res/xml/shortcuts.xml), older versions published them
    // as dynamic ones, which would show up twice. Pinned copies keep working, removing only unlists them.
    @SuppressLint("NewApi")
    private fun removeLegacyDynamicShortcuts() {
        if (isNougatMR1Plus()) {
            try {
                getSystemService(ShortcutManager::class.java)?.removeDynamicShortcuts(listOf(SHORTCUT_NEW_TEXT_NOTE, SHORTCUT_NEW_CHECKLIST))
            } catch (ignored: Exception) {
            }
        }
    }
}
