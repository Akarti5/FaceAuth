package com.akartis.faceauth.ml

import kotlin.math.sqrt

object EmbeddingMath {

    /**
     * Average several embeddings then apply L2 normalization.
     * Used after collecting multiple FaceNet samples during enrollment.
     */
    fun averageAndL2Normalize(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "Aucun embedding à moyenner" }

        val size = embeddings.first().size
        require(embeddings.all { it.size == size }) {
            "Tous les embeddings doivent avoir la même dimension"
        }

        val averaged = FloatArray(size)
        for (embedding in embeddings) {
            for (i in 0 until size) {
                averaged[i] += embedding[i]
            }
        }
        val count = embeddings.size.toFloat()
        for (i in 0 until size) {
            averaged[i] /= count
        }

        return l2Normalize(averaged)
    }

    fun l2Normalize(embedding: FloatArray): FloatArray {
        var sumSquares = 0f
        for (value in embedding) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares)
        if (norm == 0f) return embedding.copyOf()

        return FloatArray(embedding.size) { embedding[it] / norm }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Dimensions d'embedding incompatibles" }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom == 0f) return 0f
        return dot / denom
    }
}
