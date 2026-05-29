package com.victor.postly.dao

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.victor.postly.model.ChatMessage
import com.victor.postly.model.ChatThread

class ChatDao {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("chats")

    fun chatIdForUsers(firstUserId: String, secondUserId: String): String {
        return listOf(firstUserId, secondUserId).sorted().joinToString("_")
    }

    fun createOrGetChat(
        currentUserId: String,
        targetUserId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || currentUserId == targetUserId) {
            onError("Conversa invalida")
            return
        }

        val chatId = chatIdForUsers(currentUserId, targetUserId)
        val chatRef = collection.document(chatId)

        chatRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onSuccess(chatId)
                    return@addOnSuccessListener
                }

                val now = System.currentTimeMillis()
                val chat = ChatThread(
                    id = chatId,
                    participants = listOf(currentUserId, targetUserId).sorted(),
                    createdAt = now,
                    updatedAt = now
                )

                chatRef.set(chat)
                    .addOnSuccessListener { onSuccess(chatId) }
                    .addOnFailureListener { onError(it.message ?: "Erro ao criar conversa") }
            }
            .addOnFailureListener { onError(it.message ?: "Erro ao abrir conversa") }
    }

    fun getConversations(
        userId: String,
        onResult: (List<ChatThread>) -> Unit
    ) {
        if (userId.isBlank()) {
            onResult(emptyList())
            return
        }

        collection
            .whereArrayContains("participants", userId)
            .get()
            .addOnSuccessListener { result ->
                val conversations = result.mapNotNull { doc ->
                    doc.toObject(ChatThread::class.java).let { chat ->
                        if (chat.id.isBlank()) chat.copy(id = doc.id) else chat
                    }
                }.sortedByDescending { it.lastTimestamp }
                fillMissingLastSenders(conversations, onResult)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun getMessages(
        chatId: String,
        onResult: (List<ChatMessage>) -> Unit
    ) {
        if (chatId.isBlank()) {
            onResult(emptyList())
            return
        }

        collection.document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                val messages = result.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java).let { message ->
                        if (message.id.isBlank()) {
                            message.copy(id = doc.id, chatId = chatId)
                        } else {
                            message
                        }
                    }
                }
                onResult(messages)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
        onSuccess: (ChatMessage) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanText = text.trim()
        if (chatId.isBlank() || senderId.isBlank() || cleanText.isBlank()) {
            onError("Mensagem invalida")
            return
        }

        val now = System.currentTimeMillis()
        val chatRef = collection.document(chatId)
        val messageRef = chatRef.collection("messages").document()
        val message = ChatMessage(
            id = messageRef.id,
            chatId = chatId,
            senderId = senderId,
            text = cleanText,
            timestamp = now
        )

        db.runBatch { batch ->
            batch.set(messageRef, message)
            batch.update(
                chatRef,
                mapOf(
                    "lastMessage" to cleanText,
                    "lastSenderId" to senderId,
                    "lastTimestamp" to now,
                    "updatedAt" to now
                )
            )
        }
            .addOnSuccessListener { onSuccess(message) }
            .addOnFailureListener { onError(it.message ?: "Erro ao enviar mensagem") }
    }

    private fun fillMissingLastSenders(
        conversations: List<ChatThread>,
        onResult: (List<ChatThread>) -> Unit
    ) {
        val conversationsToFix = conversations.filter {
            it.lastMessage.isNotBlank() && it.lastSenderId.isBlank()
        }

        if (conversationsToFix.isEmpty()) {
            onResult(conversations)
            return
        }

        val updatedConversations = conversations.toMutableList()
        var pending = conversationsToFix.size

        fun finishOne() {
            pending--
            if (pending == 0) {
                onResult(updatedConversations.sortedByDescending { it.lastTimestamp })
            }
        }

        conversationsToFix.forEach { conversation ->
            collection.document(conversation.id)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { result ->
                    val senderId = result.documents.firstOrNull()
                        ?.toObject(ChatMessage::class.java)
                        ?.senderId
                        .orEmpty()

                    if (senderId.isNotBlank()) {
                        val index = updatedConversations.indexOfFirst { it.id == conversation.id }
                        if (index >= 0) {
                            updatedConversations[index] = updatedConversations[index].copy(
                                lastSenderId = senderId
                            )
                        }
                    }
                }
                .addOnCompleteListener {
                    finishOne()
                }
        }
    }
}
