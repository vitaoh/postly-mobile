package com.victor.postly.dao

import android.util.Log
import com.google.firebase.firestore.FieldValue
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

    fun toggleLike(
        post: Post,
        userId: String,
        onSuccess: (Post) -> Unit,
        onError: (String) -> Unit
    ) {
        if (post.id.isBlank() || userId.isBlank()) {
            onError("Usuario nao autenticado")
            return
        }

        val isLiked = post.likedBy.contains(userId)
        val postRef = collection.document(post.id)
        val updatedPost = if (isLiked) {
            post.copy(
                likeCount = (post.likeCount - 1).coerceAtLeast(0),
                likedBy = post.likedBy.filterNot { it == userId }
            )
        } else {
            post.copy(
                likeCount = post.likeCount + 1,
                likedBy = post.likedBy + userId
            )
        }

        db.runTransaction { tx ->
            tx.update(
                postRef,
                mapOf(
                    "likeCount" to FieldValue.increment(if (isLiked) -1 else 1),
                    "likedBy" to if (isLiked) {
                        FieldValue.arrayRemove(userId)
                    } else {
                        FieldValue.arrayUnion(userId)
                    }
                )
            )
        }
            .addOnSuccessListener { onSuccess(updatedPost) }
            .addOnFailureListener { onError(it.message ?: "Erro ao curtir post") }
    }

    fun getPost(
        postId: String,
        onResult: (Post?) -> Unit
    ) {
        collection.document(postId)
            .get()
            .addOnSuccessListener { doc ->
                val post = doc.toObject(Post::class.java)?.let {
                    if (it.id.isBlank()) it.copy(id = doc.id) else it
                }
                onResult(post)
            }
            .addOnFailureListener { e ->
                Log.e("PostDao", "Erro ao buscar post: ${e.message}")
                onResult(null)
            }
    }

    // Busca os posts de um usuario para o perfil publico.
    fun getPostsByUser(
        userId: String,
        onResult: (List<Post>) -> Unit
    ) {
        collection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                val posts = result
                    .mapNotNull { it.toObject(Post::class.java) }
                    .sortedByDescending { it.timestamp }
                onResult(posts)
            }
            .addOnFailureListener { e ->
                Log.e("PostDao", "Erro ao buscar posts do usuario: ${e.message}")
                onResult(emptyList())
            }
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
