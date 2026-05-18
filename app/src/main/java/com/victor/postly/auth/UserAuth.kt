package com.victor.postly.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class UserAuth {

    private val auth = FirebaseAuth.getInstance()

    fun login(email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                callback(task.isSuccessful, task.exception?.message)
            }
    }

    fun register(email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                callback(task.isSuccessful, task.exception?.message)
            }
    }

    fun sendPasswordReset(email: String, callback: (Boolean) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task -> callback(task.isSuccessful) }
    }

    /**
     * Reautentica o usuário com a senha atual e depois atualiza para a nova senha.
     * É necessário reautenticar antes de operações sensíveis no Firebase.
     */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
            ?: run { callback(false, "Usuário não autenticado"); return }

        val email = user.email
            ?: run { callback(false, "E-mail não encontrado"); return }

        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnCompleteListener { reauth ->
                if (!reauth.isSuccessful) {
                    callback(false, reauth.exception?.message ?: "Senha atual incorreta")
                    return@addOnCompleteListener
                }
                user.updatePassword(newPassword)
                    .addOnCompleteListener { update ->
                        callback(update.isSuccessful, update.exception?.message)
                    }
            }
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun logout() = auth.signOut()
}