package com.victor.postly.notifications

import android.content.Context
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.victor.postly.R
import com.victor.postly.dao.UserDao
import com.victor.postly.model.ChatThread
import com.victor.postly.model.Comment
import com.victor.postly.model.Post

class LocalNotificationWatcher(
    context: Context,
    private val currentUid: String
) {
    private val appContext = context.applicationContext
    private val db = FirebaseFirestore.getInstance()
    private val userDao = UserDao()
    private val registrations = mutableListOf<ListenerRegistration>()
    private val commentRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val knownPostLikes = mutableMapOf<String, Set<String>>()
    private val knownChatTimestamps = mutableMapOf<String, Long>()
    private val startedAt = System.currentTimeMillis()

    fun start() {
        if (currentUid.isBlank()) return

        listenToMessages()
        listenToOwnPosts()
    }

    fun stop() {
        registrations.forEach { it.remove() }
        registrations.clear()
        commentRegistrations.values.forEach { it.remove() }
        commentRegistrations.clear()
        knownPostLikes.clear()
        knownChatTimestamps.clear()
    }

    private fun listenToMessages() {
        var initialLoaded = false
        val registration = db.collection("chats")
            .whereArrayContains("participants", currentUid)
            .addSnapshotListener { snapshot, _ ->
                snapshot ?: return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    val chat = change.document.toObject(ChatThread::class.java).let {
                        if (it.id.isBlank()) it.copy(id = change.document.id) else it
                    }

                    val previousTimestamp = knownChatTimestamps[chat.id] ?: 0L
                    knownChatTimestamps[chat.id] = chat.lastTimestamp

                    if (!initialLoaded) return@forEach
                    if (chat.lastMessage.isBlank()) return@forEach
                    if (chat.lastSenderId.isBlank() || chat.lastSenderId == currentUid) return@forEach
                    if (chat.lastTimestamp <= previousTimestamp || chat.lastTimestamp < startedAt) return@forEach

                    notifyMessage(chat)
                }

                initialLoaded = true
            }

        registrations.add(registration)
    }

    private fun listenToOwnPosts() {
        val registration = db.collection("posts")
            .whereEqualTo("userId", currentUid)
            .addSnapshotListener { snapshot, _ ->
                snapshot ?: return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    val post = change.document.toObject(Post::class.java).let {
                        if (it.id.isBlank()) it.copy(id = change.document.id) else it
                    }

                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            knownPostLikes[post.id] = post.likedBy.toSet()
                            attachCommentsListener(post.id)
                        }

                        DocumentChange.Type.MODIFIED -> {
                            val oldLikes = knownPostLikes[post.id].orEmpty()
                            val newLikes = post.likedBy.toSet()
                            val newLikers = newLikes.minus(oldLikes).filter { it != currentUid }

                            knownPostLikes[post.id] = newLikes
                            newLikers.forEach { likerId -> notifyLike(post.id, likerId) }
                            attachCommentsListener(post.id)
                        }

                        DocumentChange.Type.REMOVED -> {
                            knownPostLikes.remove(post.id)
                            commentRegistrations.remove(post.id)?.remove()
                        }
                    }
                }
            }

        registrations.add(registration)
    }

    private fun attachCommentsListener(postId: String) {
        if (postId.isBlank() || commentRegistrations.containsKey(postId)) return

        val registration = db.collection("posts")
            .document(postId)
            .collection("comments")
            .whereGreaterThan("timestamp", startedAt)
            .addSnapshotListener { snapshot, _ ->
                snapshot ?: return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED) return@forEach

                    val comment = change.document.toObject(Comment::class.java).let {
                        val commentPostId = it.postId.ifBlank { postId }
                        if (it.id.isBlank()) {
                            it.copy(id = change.document.id, postId = commentPostId)
                        } else {
                            it.copy(postId = commentPostId)
                        }
                    }

                    if (comment.userId.isBlank() || comment.userId == currentUid) return@forEach
                    if (comment.timestamp < startedAt) return@forEach

                    notifyComment(postId, comment)
                }
            }

        commentRegistrations[postId] = registration
    }

    private fun notifyMessage(chat: ChatThread) {
        userDao.getUser(chat.lastSenderId) { user ->
            val title = displayName(user?.name, user?.username)
            NotificationHelper.showNotification(
                context = appContext,
                title = title,
                body = chat.lastMessage,
                type = NotificationHelper.TYPE_MESSAGE,
                data = mapOf(
                    "chatId" to chat.id,
                    "senderId" to chat.lastSenderId
                )
            )
        }
    }

    private fun notifyComment(postId: String, comment: Comment) {
        userDao.getUser(comment.userId) { user ->
            val name = displayName(user?.name, user?.username)
            NotificationHelper.showNotification(
                context = appContext,
                title = appContext.getString(R.string.notification_new_comment_title),
                body = appContext.getString(R.string.notification_new_comment_body, name, comment.text),
                type = NotificationHelper.TYPE_COMMENT,
                data = mapOf("postId" to postId)
            )
        }
    }

    private fun notifyLike(postId: String, likerId: String) {
        userDao.getUser(likerId) { user ->
            val name = displayName(user?.name, user?.username)
            NotificationHelper.showNotification(
                context = appContext,
                title = appContext.getString(R.string.notification_new_like_title),
                body = appContext.getString(R.string.notification_new_like_body, name),
                type = NotificationHelper.TYPE_LIKE,
                data = mapOf("postId" to postId)
            )
        }
    }

    private fun displayName(name: String?, username: String?): String {
        return name?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: appContext.getString(R.string.unknown_user)
    }
}
