package tomato.notes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import tomato.notes.databinding.ItemSearchResultBinding
import tomato.notes.helpers.NoteSearchResult

class SearchResultsAdapter(
    private var items: List<NoteSearchResult>,
    private val itemClick: (NoteSearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.searchResultTitle.text = item.title
        holder.binding.searchResultTitle.setTextColor(holder.itemView.context.getProperPrimaryColor())
        holder.binding.searchResultSubtitle.text = item.subtitle
        holder.binding.searchResultSubtitle.setTextColor(holder.itemView.context.getProperTextColor())
        holder.itemView.setOnClickListener { itemClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<NoteSearchResult>) {
        items = newItems
        notifyDataSetChanged()
    }
}
