package tomato.notes.activities

import android.content.Intent
import com.simplemobiletools.commons.activities.BaseSplashActivity
import tomato.notes.extensions.config
import tomato.notes.helpers.NOTEBOOK_ID
import tomato.notes.helpers.OPEN_NOTE_ID

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
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
}
