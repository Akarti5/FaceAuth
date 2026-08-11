package com.akartis.faceauth.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Persists face embeddings under users/{uid}.
 * Never stores face images — embedding vectors only.
 */
object FaceEmbeddingRepository {

    private const val USERS = "users"
    private const val FIELD_EMBEDDING = "faceEmbedding"
    private const val FIELD_ENROLLED_AT = "faceEnrolledAt"
    private const val FIELD_EMAIL = "email"
    private const val EMBEDDING_SIZE = 192

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun saveFaceEmbedding(
        uid: String,
        embedding: FloatArray,
        email: String? = null
    ): Result<Unit> = runCatching {
        require(embedding.size == EMBEDDING_SIZE) {
            "Embedding invalide: attendu $EMBEDDING_SIZE, reçu ${embedding.size}"
        }

        val data = hashMapOf<String, Any>(
            FIELD_EMBEDDING to embedding.map { it.toDouble() },
            FIELD_ENROLLED_AT to Timestamp.now()
        )
        if (!email.isNullOrBlank()) {
            data[FIELD_EMAIL] = email
        }

        db.collection(USERS)
            .document(uid)
            .set(data, SetOptions.merge())
            .await()
    }

    /** Prepared for the next Login Face Auth step. */
    suspend fun getFaceEmbedding(uid: String): Result<FloatArray> = runCatching {
        val snapshot = db.collection(USERS)
            .document(uid)
            .get()
            .await()

        if (!snapshot.exists()) {
            error("Aucun embedding facial trouvé pour cet utilisateur")
        }

        @Suppress("UNCHECKED_CAST")
        val values = snapshot.get(FIELD_EMBEDDING) as? List<Number>
            ?: error("Embedding facial manquant ou invalide")

        require(values.size == EMBEDDING_SIZE) {
            "Embedding invalide: attendu $EMBEDDING_SIZE, reçu ${values.size}"
        }

        FloatArray(values.size) { values[it].toFloat() }
    }

    suspend fun hasFaceEmbedding(uid: String): Boolean {
        return getFaceEmbedding(uid).isSuccess
    }
}
