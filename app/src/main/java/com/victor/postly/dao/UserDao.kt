package com.victor.postly.dao

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.victor.postly.model.User

class UserDao {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users")

    fun save(
        user: User,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        collection.document(user.uid)
            .set(user)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao salvar usuário") }
    }

    fun getUser(
        uid: String,
        onResult: (User?) -> Unit
    ) {
        collection.document(uid)
            .get()
            .addOnSuccessListener { doc -> onResult(doc.toObject(User::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    /**
     * Busca um usuário pelo username (campo "username" no Firestore).
     * Retorna null se não encontrado.
     */
    fun getUserByUsername(
        username: String,
        onResult: (User?) -> Unit
    ) {
        collection
            .whereEqualTo("username", username.trim().lowercase())
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                onResult(result.documents.firstOrNull()?.toObject(User::class.java))
            }
            .addOnFailureListener { onResult(null) }
    }

    fun isFollowing(
        currentUserId: String,
        targetUserId: String,
        onResult: (Boolean) -> Unit
    ) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) {
            onResult(false)
            return
        }

        collection.document(currentUserId)
            .collection("following")
            .document(targetUserId)
            .get()
            .addOnSuccessListener { doc -> onResult(doc.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    fun getFollowersCount(
        userId: String,
        onResult: (Int) -> Unit
    ) {
        collection.document(userId)
            .collection("followers")
            .get()
            .addOnSuccessListener { result -> onResult(result.size()) }
            .addOnFailureListener { onResult(0) }
    }

    fun getFollowingCount(
        userId: String,
        onResult: (Int) -> Unit
    ) {
        collection.document(userId)
            .collection("following")
            .get()
            .addOnSuccessListener { result -> onResult(result.size()) }
            .addOnFailureListener { onResult(0) }
    }

    fun toggleFollow(
        currentUserId: String,
        targetUserId: String,
        isFollowing: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) {
            onError("Usuario nao autenticado")
            return
        }
        if (currentUserId == targetUserId) {
            onError("Nao e possivel seguir a si mesmo")
            return
        }

        val currentFollowingRef = collection.document(currentUserId)
            .collection("following")
            .document(targetUserId)
        val targetFollowerRef = collection.document(targetUserId)
            .collection("followers")
            .document(currentUserId)

        val batch = db.batch()
        if (isFollowing) {
            batch.delete(currentFollowingRef)
            batch.delete(targetFollowerRef)
        } else {
            batch.set(
                currentFollowingRef,
                mapOf(
                    "userId" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                targetFollowerRef,
                mapOf(
                    "userId" to currentUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao seguir usuario") }
    }
}
