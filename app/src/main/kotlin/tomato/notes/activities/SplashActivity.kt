package tomato.notes.activities

import android.content.Intent
import com.simplemobiletools.commons.activities.BaseSplashActivity
import tomato.notes.extensions.config
import tomato.notes.helpers.NOTEBOOK_ID
import tomato.notes.helpers.OPEN_NOTE_ID

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        when {
            intent.extras?.containsKey(OPEN_NOTE_ID) == true -> {
                Intent(this, MainActivity::class.java).apply {
                    putExtra(OPEN_NOTE_ID, intent.getLongExtra(OPEN_NOTE_ID, -1L))
                    startActivity(this)
                }
            }
            intent.extras?.containsKey(NOTEBOOK_ID) == true -> {
                Intent(this, NotesListActivity::class.java).apply {
                    putExtra(NOTEBOOK_ID, intent.getLongExtra(NOTEBOOK_ID, 0L))
                    startActivity(this)
                }
            }
            config.showNotebooks -> startActivity(Intent(this, NotebooksActivity::class.java))
            else -> startActivity(Intent(this, NotesListActivity::class.java))
        }
        finish()
    }
}
