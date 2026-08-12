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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val REQUIRED_STEPS = 5

// ---- Tuning thresholds ----
private const val FRONT_YAW_ABS_DEG = 12f
// Étapes 2/3 : angles plus stricts + stabilité plus longue (évite captures trop rapides)
private const val LEFT_YAW_DEG = -22f
private const val RIGHT_YAW_DEG = 22f

private const val EYE_OPEN_MIN = 0.4f
// Étape 4 : clignement plus tolérant (un œil qui se ferme suffit)
private const val EYE_CLOSED_MAX = 0.45f

private const val FRONT_STABLE_MS = 2000L
private const val TURN_STABLE_MS = 2800L
private const val BLINK_CLOSED_MIN_MS = 180L

private const val STEP_COOLDOWN_MS = 800L

@Composable
fun RegisterFaceScreen(
    onRegistrationComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val faceNetHelper = remember { FaceNetHelper(context) }
    val embeddings = remember { mutableStateListOf<FloatArray>() }

    // Cache UID/email au démarrage pour éviter "Utilisateur non connecté" si la session expire pendant l'enrôlement
    val enrollmentUid = remember { AuthRepository.getCurrentUserId() }
    val enrollmentEmail = remember { AuthRepository.getCurrentUserEmail() }

    // stepIndex: 0..4, derived from embeddings.size but kept explicit for clarity
    var stepIndex by remember { mutableIntStateOf(0) }
    var embeddingProcessing by remember { mutableStateOf(false) }
    var firestoreSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Stability timers / blink state (not exposed to UI, to avoid recomposition spam)
    val stableSinceMs = remember { AtomicLong(0L) }
    val blinkClosedSinceMs = remember { AtomicLong(0L) }
    val blinkSawOpen = remember { AtomicBoolean(false) }
    val lastCaptureMs = remember { AtomicLong(0L) }

    fun resetStepState() {
        stableSinceMs.set(0L)
        blinkClosedSinceMs.set(0L)
        blinkSawOpen.set(false)
    }

    DisposableEffect(Unit) {
        onDispose { faceNetHelper.close() }
    }

    fun stepInstruction(index: Int): String = when (index) {
        0 -> "Regardez devant vous 🎯"
        1 -> "Tournez à droite ➡"
        2 -> "Tournez à gauche ⬅"
        3 -> "Clignez des yeux 👁"
        else -> "Regardez devant vous 🎯"
    }

    fun retryFirestoreSave() {
        if (embeddings.size < REQUIRED_STEPS || firestoreSaving || enrollmentUid == null) return
        firestoreSaving = true
        errorMessage = null
        scope.launch {
            saveFinalEmbedding(
                uid = enrollmentUid,
                email = enrollmentEmail,
                embeddings = embeddings.toList(),
                onSuccess = {
                    onRegistrationComplete()
                },
                onError = { message ->
                    errorMessage = message
                    firestoreSaving = false
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            enrollmentUid == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Session expirée. Créez d'abord votre compte, puis enregistrez votre visage.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            firestoreSaving -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sauvegarde de l'embedding final dans Firestore...",
                        textAlign = TextAlign.Center
                    )
                }
            }

            errorMessage != null && embeddings.size >= REQUIRED_STEPS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { retryFirestoreSave() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Réessayer la sauvegarde")
                    }
                }
            }

            else -> {
                FaceCaptureScreen(
                    instructionText = if (embeddingProcessing) {
                        "Analyse du visage..."
                    } else {
                        stepInstruction(stepIndex)
                    },
                    currentStep = stepIndex,
                    totalSteps = REQUIRED_STEPS,
                    captureEnabled = !embeddingProcessing && !firestoreSaving,
                    onFaceAnalyzed = { croppedBitmap, headEulerAngleY, leftEyeOpenProbability, rightEyeOpenProbability ->
                        if (embeddingProcessing || firestoreSaving) return@FaceCaptureScreen
                        if (embeddings.size >= REQUIRED_STEPS) return@FaceCaptureScreen

                        val now = System.currentTimeMillis()
                        if (now - lastCaptureMs.get() < STEP_COOLDOWN_MS) return@FaceCaptureScreen

                        // Eye probabilities can be null depending on ML Kit config.
                        val eyesOpen = when {
                            leftEyeOpenProbability == null || rightEyeOpenProbability == null -> false
                            else -> leftEyeOpenProbability > EYE_OPEN_MIN && rightEyeOpenProbability > EYE_OPEN_MIN
                        }
                        val eyesClosed = when {
                            leftEyeOpenProbability == null || rightEyeOpenProbability == null -> false
                            else -> leftEyeOpenProbability < EYE_CLOSED_MAX ||
                                rightEyeOpenProbability < EYE_CLOSED_MAX
                        }

                        fun yawOkFront(): Boolean {
                            if (headEulerAngleY == null) return false
                            return abs(headEulerAngleY) <= FRONT_YAW_ABS_DEG
                        }

                        fun yawOkLeft(): Boolean {
                            if (headEulerAngleY == null) return false
                            return headEulerAngleY <= LEFT_YAW_DEG
                        }

                        fun yawOkRight(): Boolean {
                            if (headEulerAngleY == null) return false
                            return headEulerAngleY >= RIGHT_YAW_DEG
                        }

                        val shouldCapture: Boolean = when (stepIndex) {
                            0 -> {
                                if (!yawOkFront() || !eyesOpen) {
                                    stableSinceMs.set(0L)
                                    false
                                } else {
                                    stabilityReached(stableSinceMs, now, FRONT_STABLE_MS)
                                }
                            }
                            1 -> {
                                if (!yawOkLeft() || !eyesOpen) {
                                    stableSinceMs.set(0L)
                                    false
                                } else {
                                    stabilityReached(stableSinceMs, now, TURN_STABLE_MS)
                                }
                            }
                            2 -> {
                                if (!yawOkRight() || !eyesOpen) {
                                    stableSinceMs.set(0L)
                                    false
                                } else {
                                    stabilityReached(stableSinceMs, now, TURN_STABLE_MS)
                                }
                            }
                            3 -> {
                                // Pré-remplir "yeux ouverts vus" si l'utilisateur arrive sur l'étape clignement
                                if (eyesOpen && !blinkSawOpen.get()) {
                                    blinkSawOpen.set(true)
                                }
                                blinkConditionReached(
                                    stableSinceMs = stableSinceMs,
                                    blinkClosedSinceMs = blinkClosedSinceMs,
                                    blinkSawOpen = blinkSawOpen,
                                    now = now,
                                    eyesOpen = eyesOpen,
                                    eyesClosed = eyesClosed,
                                    minClosedMs = BLINK_CLOSED_MIN_MS
                                )
                            }
                            else -> {
                                if (!yawOkFront() || !eyesOpen) {
                                    stableSinceMs.set(0L)
                                    false
                                } else {
                                    stabilityReached(stableSinceMs, now, TURN_STABLE_MS)
                                }
                            }
                        }

                        if (!shouldCapture) return@FaceCaptureScreen

                        // Lock until FaceNet finishes to prevent multi-capture spam.
                        lastCaptureMs.set(now)
                        embeddingProcessing = true
                        errorMessage = null
                        resetStepState()

                        val stepToCapture = stepIndex
                        scope.launch {
                            try {
                                val embedding = withContext(Dispatchers.Default) {
                                    faceNetHelper.getEmbedding(croppedBitmap)
                                }

                                embeddings.add(embedding)

                                if (stepToCapture >= REQUIRED_STEPS - 1) {
                                    firestoreSaving = true
                                    saveFinalEmbedding(
                                        uid = enrollmentUid,
                                        email = enrollmentEmail,
                                        embeddings = embeddings.toList(),
                                        onSuccess = onRegistrationComplete,
                                        onError = { message ->
                                            errorMessage = message
                                            firestoreSaving = false
                                            embeddingProcessing = false
                                        }
                                    )
                                } else {
                                    stepIndex = stepToCapture + 1
                                    embeddingProcessing = false
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errorMessage = e.message ?: "Erreur lors de l'analyse du visage"
                                embeddingProcessing = false
                                // Keep the camera visible; allow user to retry the step.
                            }
                        }
                    }
                )

                errorMessage?.takeIf { it.isNotBlank() && embeddings.size < REQUIRED_STEPS }?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun stabilityReached(
    stableSinceMs: AtomicLong,
    now: Long,
    requiredStableMs: Long
): Boolean {
    val since = stableSinceMs.get()
    if (since == 0L) {
        stableSinceMs.set(now)
        return false
    }
    return (now - since) >= requiredStableMs
}

private fun blinkConditionReached(
    stableSinceMs: AtomicLong,
    blinkClosedSinceMs: AtomicLong,
    blinkSawOpen: AtomicBoolean,
    now: Long,
    eyesOpen: Boolean,
    eyesClosed: Boolean,
    minClosedMs: Long
): Boolean {
    // Any valid open resets blink-closed timer.
    if (eyesOpen) {
        blinkSawOpen.set(true)
        blinkClosedSinceMs.set(0L)
        stableSinceMs.set(0L)
        return false
    }

    // Closed after having seen "open" long enough => count closed duration.
    if (blinkSawOpen.get() && eyesClosed) {
        val since = blinkClosedSinceMs.get()
        if (since == 0L) {
            blinkClosedSinceMs.set(now)
            return false
        }
        return (now - since) >= minClosedMs
    }

    // Not open, not closed => reset closed timer (but keep blinkSawOpen until next open frame).
    blinkClosedSinceMs.set(0L)
    return false
}

private suspend fun saveFinalEmbedding(
    uid: String?,
    email: String?,
    embeddings: List<FloatArray>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (uid == null) {
        onError("Utilisateur non connecté. Recréez le compte puis réessayez.")
        return
    }

    try {
        val finalEmbedding = withContext(Dispatchers.Default) {
            EmbeddingMath.averageAndL2Normalize(embeddings)
        }

        FaceEmbeddingRepository.saveFaceEmbedding(
            uid = uid,
            embedding = finalEmbedding,
            email = email
        ).getOrElse { throw it }

        onSuccess()
    } catch (e: Exception) {
        e.printStackTrace()
        onError(e.message ?: "Échec de la sauvegarde Firestore")
    }
}
