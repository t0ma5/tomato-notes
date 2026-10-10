package tomato.notes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import tomato.notes.databinding.ItemRecycleBinBinding
import tomato.notes.models.RecycleBinItem

class RecycleBinAdapter(
    private var items: List<RecycleBinItem>,
    private val restoreClick: (RecycleBinItem) -> Unit,
    private val deleteClick: (RecycleBinItem) -> Unit
) : RecyclerView.Adapter<RecycleBinAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecycleBinBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecycleBinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.binding.recycleBinTitle.text = item.title
        holder.binding.recycleBinTitle.setTextColor(context.getProperPrimaryColor())
        holder.binding.recycleBinSubtitle.text = item.subtitle
        holder.binding.recycleBinSubtitle.setTextColor(context.getProperTextColor())
        holder.binding.recycleBinRestore.setTextColor(context.getProperPrimaryColor())
        holder.binding.recycleBinDelete.setTextColor(context.getProperTextColor())
        holder.binding.recycleBinRestore.setOnClickListener { restoreClick(item) }
        holder.binding.recycleBinDelete.setOnClickListener { deleteClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<RecycleBinItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
