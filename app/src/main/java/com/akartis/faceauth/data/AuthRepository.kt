package com.akartis.faceauth.data

import com.google.firebase.auth.FirebaseAuth
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    fun isLoggedIn(): Boolean = auth.currentUser != null

    suspend fun login(email: String, password: String): Result<Unit> = suspendCoroutine { cont ->
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    cont.resume(Result.success(Unit))
                } else {
                    cont.resume(Result.failure(task.exception ?: Exception("Échec de la connexion")))
                }
            }
    }

    suspend fun signup(email: String, password: String): Result<Unit> = suspendCoroutine { cont ->
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    cont.resume(Result.success(Unit))
                } else {
                    cont.resume(Result.failure(task.exception ?: Exception("Échec de l'inscription")))
                }
            }
    }

    fun logout() {
        auth.signOut()
    }
}
