package com.victor.postly.dao

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.victor.postly.model.Comment

class CommentDao {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Adiciona um comentário na subcoleção posts/{postId}/comments
     * e incrementa o campo commentCount no documento do post atomicamente.
     */
    fun addComment(
        postId: String,
        comment: Comment,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val postRef = db.collection("posts").document(postId)
        val commentsRef = postRef.collection("comments")
        val docRef = commentsRef.document()
        val commentFinal = comment.copy(id = docRef.id, postId = postId)

        db.runTransaction { tx ->
            tx.set(docRef, commentFinal)
            tx.update(postRef, "commentCount", FieldValue.increment(1))
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao comentar") }
    }

    /**
     * Remove um comentário e decrementa o commentCount do post atomicamente.
     * Garante que o contador não fique negativo (proteção básica).
     */
    fun deleteComment(
        postId: String,
        commentId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val postRef = db.collection("posts").document(postId)
        val commentRef = postRef.collection("comments").document(commentId)

        db.runTransaction { tx ->
            tx.delete(commentRef)
            tx.update(postRef, "commentCount", FieldValue.increment(-1))
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao excluir comentário") }
    }

    /**
     * Busca todos os comentários de um post, ordenados do mais antigo ao mais recente.
     */
    fun getComments(
        postId: String,
        onResult: (List<Comment>) -> Unit
    ) {
        db.collection("posts").document(postId)
            .collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                val comments = result.mapNotNull { it.toObject(Comment::class.java) }
                onResult(comments)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}
