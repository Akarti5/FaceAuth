package com.akartis.faceauth.data


import com.google.firebase.firestore.FirebaseFirestore

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    fun testFirestore(
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val data = hashMapOf(
            "test" to true,
            "message" to "FaceAuth Firestore fonctionne"
        )

        db.collection("test")
            .document("connection")
            .set(data)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }
}