package com.victor.postly.adapter

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.victor.postly.R
import com.victor.postly.databinding.ItemMessageBinding
import com.victor.postly.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
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
            val showDate = position == 0 || !isSameDay(
                message.timestamp,
                messages[position - 1].timestamp
            )
            txtDateSeparator.visibility = if (showDate) View.VISIBLE else View.GONE
            if (showDate) {
                txtDateSeparator.text = formatDateSeparator(holder.binding.root.context, message.timestamp)
            }

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

    private fun formatDateSeparator(context: Context, timestamp: Long): String {
        val today = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        return when {
            isSameDay(date, today) -> context.getString(R.string.message_date_today)
            isSameDay(date, yesterday) -> context.getString(R.string.message_date_yesterday)
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun isSameDay(firstTimestamp: Long, secondTimestamp: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
        val second = Calendar.getInstance().apply { timeInMillis = secondTimestamp }
        return isSameDay(first, second)
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
