package com.victor.postly.dao

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.victor.postly.model.Post

class PostDao {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("posts")

    companion object {
        const val PAGE_SIZE = 5L
    }

    // Gera o ID antes de salvar, garantindo que post.id sempre existe
    fun addPost(
        post: Post,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val docRef = collection.document()
        val postWithId = post.copy(id = docRef.id)
        docRef.set(postWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao criar post") }
    }

    fun updatePost(
        post: Post,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        collection.document(post.id)
            .set(post)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao atualizar post") }
    }

    fun deletePost(
        postId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        collection.document(postId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao excluir post") }
    }

    // Primeira página — retorna o último DocumentSnapshot para usar como cursor
    fun getFirstPage(onResult: (List<Post>, DocumentSnapshot?) -> Unit) {
        collection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .get()
            .addOnSuccessListener { result ->
                val posts = result.mapNotNull { it.toObject(Post::class.java) }
                val lastDoc = result.documents.lastOrNull()
                onResult(posts, lastDoc)
            }
            .addOnFailureListener { e ->
                Log.e("PostDao", "Erro ao buscar posts: ${e.message}")
                onResult(emptyList(), null)
            }
    }

    // Páginas seguintes — recebe o cursor da página anterior
    fun getNextPage(
        lastDoc: DocumentSnapshot,
        onResult: (List<Post>, DocumentSnapshot?) -> Unit
    ) {
        collection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .startAfter(lastDoc)
            .limit(PAGE_SIZE)
            .get()
            .addOnSuccessListener { result ->
                val posts = result.mapNotNull { it.toObject(Post::class.java) }
                val newLastDoc = result.documents.lastOrNull()
                onResult(posts, newLastDoc)
            }
            .addOnFailureListener { e ->
                Log.e("PostDao", "Erro ao buscar mais posts: ${e.message}")
                onResult(emptyList(), null)
            }
    }
}