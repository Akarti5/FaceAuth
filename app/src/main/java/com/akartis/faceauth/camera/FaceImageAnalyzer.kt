package com.akartis.faceauth.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream

class FaceImageAnalyzer(
    private val onFaceCropped: (Bitmap) -> Unit,
    private val onNoFace: () -> Unit
) {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)

    @ExperimentalGetImage
    fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    // On prend le plus grand visage détecté (le plus proche de la caméra)
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    if (face != null) {
                        val fullBitmap = imageProxyToBitmap(imageProxy, rotationDegrees)
                        val cropped = cropFace(fullBitmap, face)
                        if (cropped != null) {
                            onFaceCropped(cropped)
                        } else {
                            onNoFace()
                        }
                    } else {
                        onNoFace()
                    }
                } else {
                    onNoFace()
                }
            }
            .addOnFailureListener {
                onNoFace()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        val imageBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Applique la rotation pour que le visage soit bien droit
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun cropFace(bitmap: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox

        // Ajoute une petite marge autour du visage (10%)
        val marginW = (box.width() * 0.1f).toInt()
        val marginH = (box.height() * 0.1f).toInt()

        val left = (box.left - marginW).coerceIn(0, bitmap.width)
        val top = (box.top - marginH).coerceIn(0, bitmap.height)
        val right = (box.right + marginW).coerceIn(0, bitmap.width)
        val bottom = (box.bottom + marginH).coerceIn(0, bitmap.height)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return null

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    fun close() {
        detector.close()
    }
}
