package com.akartis.faceauth.face

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akartis.faceauth.camera.FaceCaptureScreen
import com.akartis.faceauth.data.AuthRepository
import com.akartis.faceauth.data.FaceEmbeddingRepository
import com.akartis.faceauth.ml.EmbeddingMath
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FACE_MATCH_THRESHOLD = 0.65f
private const val FRONT_YAW_ABS_DEG = 15f
private const val EYE_OPEN_MIN = 0.35f
private const val FRONT_STABLE_MS = 1500L
private const val STEP_COOLDOWN_MS = 800L

/**
 * Connexion rapide par visage : 1 seule capture stable face caméra.
 * Compare avec l'embedding Firestore lié à l'email, puis connecte via Firebase Auth.
 */
@Composable
fun LoginFaceScreen(
    email: String,
    password: String,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val faceNetHelper = remember { FaceNetHelper(context) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val stableSinceMs = remember { AtomicLong(0L) }
    val lastAttemptMs = remember { AtomicLong(0L) }

    DisposableEffect(Unit) {
        onDispose { faceNetHelper.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaceCaptureScreen(
            instructionText = "Étape 1/1\nRegardez devant vous",
            captureEnabled = !isProcessing,
            onFaceAnalyzed = { croppedBitmap, headEulerAngleY, leftEyeOpenProbability, rightEyeOpenProbability ->
                if (isProcessing) return@FaceCaptureScreen

                val now = System.currentTimeMillis()
                if (now - lastAttemptMs.get() < STEP_COOLDOWN_MS) return@FaceCaptureScreen

                val yawOk = headEulerAngleY != null && abs(headEulerAngleY) <= FRONT_YAW_ABS_DEG
                val eyesOpen = leftEyeOpenProbability != null &&
                    rightEyeOpenProbability != null &&
                    leftEyeOpenProbability > EYE_OPEN_MIN &&
                    rightEyeOpenProbability > EYE_OPEN_MIN

                if (!yawOk || !eyesOpen) {
                    stableSinceMs.set(0L)
                    return@FaceCaptureScreen
                }

                val since = stableSinceMs.get()
                if (since == 0L) {
                    stableSinceMs.set(now)
                    return@FaceCaptureScreen
                }
                if (now - since < FRONT_STABLE_MS) return@FaceCaptureScreen

                lastAttemptMs.set(now)
                stableSinceMs.set(0L)
                isProcessing = true
                errorMessage = null
                statusMessage = "Vérification du visage..."

                scope.launch {
                    try {
                        val liveEmbedding = withContext(Dispatchers.Default) {
                            faceNetHelper.getEmbedding(croppedBitmap)
                        }

                        val (_, storedEmbedding) = FaceEmbeddingRepository
                            .getFaceEmbeddingByEmail(email)
                            .getOrElse { throw it }

                        val similarity = withContext(Dispatchers.Default) {
                            EmbeddingMath.cosineSimilarity(
                                EmbeddingMath.l2Normalize(liveEmbedding),
                                storedEmbedding
                            )
                        }

                        if (similarity < FACE_MATCH_THRESHOLD) {
                            errorMessage = "Visage non reconnu (score ${"%.2f".format(similarity)})"
                            isProcessing = false
                            statusMessage = null
                            return@launch
                        }

                        statusMessage = "Visage reconnu, connexion..."

                        AuthRepository.login(email.trim(), password)
                            .onSuccess { onLoginSuccess() }
                            .onFailure {
                                errorMessage = it.message ?: "Échec de la connexion"
                                isProcessing = false
                                statusMessage = null
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = e.message ?: "Erreur lors de la vérification"
                        isProcessing = false
                        statusMessage = null
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            statusMessage?.let {
                Text(text = it, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
            }
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            if (isProcessing) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator()
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBack, enabled = !isProcessing) {
                Text("Retour")
            }
        }
    }
}
