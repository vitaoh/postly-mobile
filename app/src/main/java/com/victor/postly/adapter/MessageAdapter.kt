package com.victor.postly.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.victor.postly.R
import com.victor.postly.databinding.ItemMessageBinding
import com.victor.postly.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUid: String
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages: MutableList<ChatMessage> = mutableListOf()

    class MessageViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val isOwn = message.senderId == currentUid
        val ownColor = ContextCompat.getColor(holder.binding.root.context, R.color.primary)
        val ownTextColor = ContextCompat.getColor(holder.binding.root.context, R.color.secundary)
        val otherColor = MaterialColors.getColor(
            holder.binding.root,
            com.google.android.material.R.attr.colorSurfaceVariant
        )
        val otherTextColor = MaterialColors.getColor(
            holder.binding.root,
            com.google.android.material.R.attr.colorOnSurface
        )

        with(holder.binding) {
            messageRow.gravity = if (isOwn) Gravity.END else Gravity.START
            messageBubble.setCardBackgroundColor(if (isOwn) ownColor else otherColor)
            txtMessageText.text = message.text
            txtMessageText.setTextColor(if (isOwn) ownTextColor else otherTextColor)
            txtMessageTime.text = formatTime(message.timestamp)
            txtMessageTime.setTextColor(if (isOwn) ownTextColor else otherTextColor)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages.distinctBy { it.id })
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        if (messages.any { it.id == message.id }) return
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
