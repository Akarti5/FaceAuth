package com.akartis.faceauth.face

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akartis.faceauth.camera.FaceCaptureScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import com.akartis.faceauth.data.FaceEmbeddingRepository

private const val FRONT_YAW_ABS_DEG = 15f
private const val EYE_OPEN_MIN = 0.35f
private const val FRONT_STABLE_MS = 1500L
private const val STEP_COOLDOWN_MS = 800L
private const val MAX_ATTEMPTS = 3

/**
 * Login par FaceAuth local :
 *  - App calcule embedding FaceNet
 *  - App récupère l'embedding stocké depuis Firestore
 *  - App calcule la similarité cosinus
 *  - Si match, récupère le mot de passe dans EncryptedCredentialStore
 *  - App fait `signInWithEmailAndPassword` (via AuthRepository)
 *
 * Après MAX_ATTEMPTS échecs, on revient sur Login pour que l'utilisateur saisisse email/mdp manuellement.
 */
@Composable
fun LoginFaceScreen(
    email: String,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val faceNetHelper = remember { FaceNetHelper(context) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }

    val stableSinceMs = remember { AtomicLong(0L) }
    val lastAttemptMs = remember { AtomicLong(0L) }

    DisposableEffect(Unit) {
        onDispose { faceNetHelper.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaceCaptureScreen(
            instructionText = "Regardez devant vous",
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

                        // 1. Récupérer l'embedding depuis Firestore (par email)
                        val (uid, storedEmbedding) = FaceEmbeddingRepository.getFaceEmbeddingByEmail(email)
                            .getOrThrow()

                        // 2. Normalisation et similarité locale
                        val liveNorm = com.akartis.faceauth.ml.EmbeddingMath.l2Normalize(liveEmbedding)
                        val storedNorm = com.akartis.faceauth.ml.EmbeddingMath.l2Normalize(storedEmbedding)
                        val similarity = com.akartis.faceauth.ml.EmbeddingMath.cosineSimilarity(liveNorm, storedNorm)

                        if (similarity < 0.65f) {
                            throw Exception("Visage non reconnu (sim: $similarity)")
                        }

                        statusMessage = "Visage reconnu ✅ Connexion..."

                        // 3. Récupérer le mot de passe depuis le coffre-fort local
                        val credentials = com.akartis.faceauth.data.EncryptedCredentialStore.load(context)
                        if (credentials == null || credentials.first != email.trim().lowercase()) {
                            throw Exception("Identifiants locaux introuvables. Connectez-vous manuellement.")
                        }

                        // 4. Connexion Firebase classique
                        com.akartis.faceauth.data.AuthRepository.login(credentials.first, credentials.second)
                            .getOrThrow()

                        onLoginSuccess()

                    } catch (e: Exception) {
                        e.printStackTrace()
                        isProcessing = false
                        statusMessage = null
                        failedAttempts += 1
                        if (failedAttempts >= MAX_ATTEMPTS) {
                            errorMessage = "$MAX_ATTEMPTS tentatives échouées. Connectez-vous avec votre mot de passe."
                            onBack()
                        } else {
                            val remaining = MAX_ATTEMPTS - failedAttempts
                            errorMessage = "${e.message ?: "Erreur inconnue"} ($remaining tentative${if (remaining > 1) "s" else ""} restante${if (remaining > 1) "s" else ""})"
                        }
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            statusMessage?.let {
                Text(
                    text = it,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
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
            // Indicateur tentatives (hors erreur, quand la caméra est active)
            if (!isProcessing && errorMessage == null && failedAttempts > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val remaining = MAX_ATTEMPTS - failedAttempts
                Text(
                    text = "Tentatives restantes : $remaining / $MAX_ATTEMPTS",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBack, enabled = !isProcessing) {
                Text("Retour")
            }
        }
    }
}
