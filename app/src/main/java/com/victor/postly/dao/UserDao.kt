package com.victor.postly.dao

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
}