package com.akartis.faceauth.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceNetHelper(context: Context) {

    companion object {
        private const val MODEL_FILE = "mobilefacenet.tflite"
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 192
    }

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context, MODEL_FILE)

        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }

        interpreter = Interpreter(model, options)
    }

    fun getEmbedding(bitmap: Bitmap): FloatArray {

        // Redimensionner le visage en 112x112
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            INPUT_SIZE,
            INPUT_SIZE,
            true
        )

        // FLOAT32 : 1 x 112 x 112 x 3
        val inputBuffer = ByteBuffer.allocateDirect(
            4 * INPUT_SIZE * INPUT_SIZE * 3
        ).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

        resizedBitmap.getPixels(
            pixels,
            0,
            INPUT_SIZE,
            0,
            0,
            INPUT_SIZE,
            INPUT_SIZE
        )

        for (pixel in pixels) {

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Normalisation [0,255] -> [-1,1]
            inputBuffer.putFloat((r - 127.5f) / 127.5f)
            inputBuffer.putFloat((g - 127.5f) / 127.5f)
            inputBuffer.putFloat((b - 127.5f) / 127.5f)
        }

        inputBuffer.rewind()

        val output = Array(1) {
            FloatArray(EMBEDDING_SIZE)
        }

        interpreter.run(inputBuffer, output)

        // Normalisation L2 de l'embedding
        return normalizeEmbedding(output[0])
    }

    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {

        var sum = 0f

        for (value in embedding) {
            sum += value * value
        }

        val norm = sqrt(sum)

        if (norm == 0f) {
            return embedding
        }

        return FloatArray(embedding.size) { index ->
            embedding[index] / norm
        }
    }

    private fun loadModelFile(
        context: Context,
        modelName: String
    ): ByteBuffer {

        val fileDescriptor =
            context.assets.openFd(modelName)

        val inputStream =
            FileInputStream(fileDescriptor.fileDescriptor)

        val fileChannel =
            inputStream.channel

        val startOffset =
            fileDescriptor.startOffset

        val declaredLength =
            fileDescriptor.declaredLength

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    fun close() {
        interpreter.close()
    }
}