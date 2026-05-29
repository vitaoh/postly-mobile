package com.victor.postly.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.victor.postly.R
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ItemConversationBinding
import com.victor.postly.model.ChatThread
import com.victor.postly.model.User
import com.victor.postly.utils.Base64Converter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val currentUid: String,
    private val onClick: (ChatThread) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    private val conversations: MutableList<ChatThread> = mutableListOf()
    private val userCache: MutableMap<String, User> = mutableMapOf()
    private val userDao = UserDao()
    private val converter = Base64Converter()

    class ConversationViewHolder(val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]
        val otherUserId = conversation.participants.firstOrNull { it != currentUid }.orEmpty()

        with(holder.binding) {
            root.setOnClickListener { onClick(conversation) }
            txtConversationTime.text = formatTime(conversation.lastTimestamp)

            val cachedUser = userCache[otherUserId]
            txtConversationMessage.text = formatLastMessage(this, conversation, cachedUser)
            if (cachedUser != null) {
                applyUser(this, cachedUser)
            } else {
                txtConversationName.text = root.context.getString(R.string.loading_user)
                imgConversationAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)

                if (otherUserId.isNotBlank()) {
                    userDao.getUser(otherUserId) { user ->
                        user ?: return@getUser
                        userCache[otherUserId] = user
                        val idx = conversations.indexOfFirst { it.id == conversation.id }
                        if (idx >= 0) notifyItemChanged(idx)
                    }
                }
            }
        }
    }

    private fun formatLastMessage(
        binding: ItemConversationBinding,
        conversation: ChatThread,
        otherUser: User?
    ): String {
        val message = conversation.lastMessage.ifBlank {
            return binding.root.context.getString(R.string.no_messages_yet)
        }

        return when {
            conversation.lastSenderId == currentUid -> {
                binding.root.context.getString(R.string.last_message_from_me, message)
            }
            conversation.lastSenderId.isBlank() -> message
            otherUser != null -> {
                val name = otherUser.name.ifBlank {
                    binding.root.context.getString(R.string.unknown_user)
                }
                binding.root.context.getString(R.string.last_message_from_user, name, message)
            }
            else -> message
        }
    }

    override fun getItemCount(): Int = conversations.size

    fun updateConversations(newConversations: List<ChatThread>) {
        conversations.clear()
        conversations.addAll(newConversations)
        notifyDataSetChanged()
    }

    private fun applyUser(binding: ItemConversationBinding, user: User) {
        binding.txtConversationName.text = user.name.ifBlank { binding.root.context.getString(R.string.unknown_user) }
        if (!user.photo.isNullOrEmpty()) {
            val bitmap = converter.stringToBitmap(user.photo)
            binding.imgConversationAvatar.setImageBitmap(bitmap)
        } else {
            binding.imgConversationAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""

        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000L -> "agora"
            diff < 3_600_000L -> "${diff / 60_000}min"
            diff < 86_400_000L -> "${diff / 3_600_000}h"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000}d"
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
