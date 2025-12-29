package com.ahla.pasiflonetmobile.ui
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ahla.pasiflonetmobile.R
import com.ahla.pasiflonetmobile.databinding.ItemMessageBinding
import com.ahla.pasiflonetmobile.td.TelegramMsg
class MessageAdapter(private val onEditClick: (TelegramMsg) -> Unit) : ListAdapter<TelegramMsg, MessageAdapter.MsgViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MsgViewHolder = MsgViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: MsgViewHolder, position: Int) = holder.bind(getItem(position))
    inner class MsgViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: TelegramMsg) {
            binding.tvMessage.text = msg.text
            binding.tvDate.text = msg.date
            binding.tvType.text = if (msg.type == "media") binding.root.context.getString(R.string.media_type) else binding.root.context.getString(R.string.text_type)
            binding.btnEdit.setOnClickListener { onEditClick(msg) }
        }
    }
    class DiffCallback : DiffUtil.ItemCallback<TelegramMsg>() {
        override fun areItemsTheSame(old: TelegramMsg, new: TelegramMsg) = old.id == new.id
        override fun areContentsTheSame(old: TelegramMsg, new: TelegramMsg) = old == new
    }
}
