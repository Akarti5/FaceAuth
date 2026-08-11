package com.akartis.faceauth.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceNetHelper(context: Context) {

    private var interpreter: Interpreter
    private val inputSize = 112 // taille attendue par mobilefacenet.tflite
    private val embeddingSize = 192

    init {
        val model = loadModelFile(context, "mobilefacenet.tflite")
        val options = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(model, options)
    }

    /**
     * Prend un Bitmap de visage déjà recadré et retourne son embedding (vecteur de 192 valeurs)
     */
    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        val inputBuffer = convertBitmapToByteBuffer(resized)

        val output = Array(1) { FloatArray(embeddingSize) }
        interpreter.run(inputBuffer, output)

        return output[0]
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            // Normalisation [-1, 1], standard pour les modèles type MobileFaceNet
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)

            byteBuffer.putFloat((r - 127.5f) / 127.5f)
            byteBuffer.putFloat((g - 127.5f) / 127.5f)
            byteBuffer.putFloat((b - 127.5f) / 127.5f)
        }

        return byteBuffer
    }

    /**
     * Compare deux embeddings avec la similarité cosinus
     * Retourne une valeur entre -1 et 1 (proche de 1 = même visage)
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in embedding1.indices) {
            dot += embedding1[i] * embedding2[i]
            normA += embedding1[i] * embedding1[i]
            normB += embedding2[i] * embedding2[i]
        }
        return dot / (sqrt(normA) * sqrt(normB))
    }

    fun close() {
        interpreter.close()
    }
}
