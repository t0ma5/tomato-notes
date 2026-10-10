package tomato.notes.models

data class RecycleBinItem(
    val note: Note? = null,
    val notebook: Notebook? = null,
    val title: String,
    val subtitle: String
) {
    val isNotebook get() = notebook != null
}
