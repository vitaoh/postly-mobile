package com.victor.postly.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ItemCommentBinding
import com.victor.postly.model.Comment
import com.victor.postly.model.User
import com.victor.postly.utils.Base64Converter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentAdapter(
    private val currentUid: String,
    private val onDelete: (Comment) -> Unit,
    private val onAuthorClick: (String) -> Unit = {}
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val comments: MutableList<Comment> = mutableListOf()
    private val converter = Base64Converter()
    private val userDao = UserDao()
    private val userCache: MutableMap<String, User> = mutableMapOf()

    class CommentViewHolder(val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]

        holder.binding.txtCommentText.text = comment.text
        holder.binding.txtCommentTime.text = formatTime(comment.timestamp)
        bindAuthorClick(holder.binding, comment.userId)

        // Botão excluir — visível só para o autor do comentário
        val isOwner = comment.userId == currentUid
        holder.binding.btnDeleteComment.visibility = if (isOwner) View.VISIBLE else View.GONE
        holder.binding.btnDeleteComment.setOnClickListener { onDelete(comment) }

        // Dados do autor via cache / Firestore
        val cached = userCache[comment.userId]
        if (cached != null) {
            applyUser(holder.binding, cached)
        } else {
            holder.binding.txtCommentName.text = "..."
            holder.binding.txtCommentUsername.text = ""
            holder.binding.imgCommentAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)

            userDao.getUser(comment.userId) { user ->
                user ?: return@getUser
                userCache[comment.userId] = user
                val idx = comments.indexOfFirst { it.id == comment.id }
                if (idx >= 0) notifyItemChanged(idx)
            }
        }
    }

    private fun applyUser(binding: ItemCommentBinding, user: User) {
        binding.txtCommentName.text = user.name
        binding.txtCommentUsername.text = "@${user.username}"
        if (!user.photo.isNullOrEmpty()) {
            val bmp = converter.stringToBitmap(user.photo)
            binding.imgCommentAvatar.setImageBitmap(bmp)
        } else {
            binding.imgCommentAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }
    }

    private fun bindAuthorClick(binding: ItemCommentBinding, userId: String) {
        val hasUser = userId.isNotBlank()
        val listener = View.OnClickListener {
            onAuthorClick(userId)
        }

        listOf<View>(binding.imgCommentAvatar, binding.txtCommentName, binding.txtCommentUsername)
            .forEach { view ->
                view.isClickable = hasUser
                view.isFocusable = hasUser
                view.setOnClickListener(if (hasUser) listener else null)
            }
    }

    override fun getItemCount(): Int = comments.size

    fun setComments(list: List<Comment>) {
        comments.clear()
        comments.addAll(
            list.distinctBy { comment ->
                comment.id.ifBlank { "${comment.userId}:${comment.timestamp}:${comment.text}" }
            }
        )
        notifyDataSetChanged()
    }

    fun addComment(comment: Comment) {
        val alreadyAdded = if (comment.id.isNotBlank()) {
            comments.any { it.id == comment.id }
        } else {
            comments.any {
                it.userId == comment.userId &&
                    it.timestamp == comment.timestamp &&
                    it.text == comment.text
            }
        }
        if (alreadyAdded) return

        comments.add(comment)
        notifyItemInserted(comments.size - 1)
    }

    fun removeComment(comment: Comment) {
        val idx = comments.indexOfFirst { it.id == comment.id }
        if (idx >= 0) {
            comments.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000L -> "agora"
            diff < 3_600_000L -> "${diff / 60_000}min"
            diff < 86_400_000L -> "${diff / 3_600_000}h"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000}d"
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                .format(Date(timestamp))
        }
    }
}
