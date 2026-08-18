package com.akartis.faceauth.face

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import com.akartis.faceauth.camera.FaceCaptureScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import com.akartis.faceauth.data.FaceEmbeddingRepository

sealed class FaceVerificationState {
    object Verifying : FaceVerificationState()
    object Success : FaceVerificationState()
    data class Error(val message: String) : FaceVerificationState()
}

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
    // On utilise l'email stocké s'il existe, sinon on prend celui passé en paramètre
    val localEmail = remember { com.akartis.faceauth.data.EncryptedCredentialStore.load(context)?.first }
    val effectiveEmail = if (email.isNotBlank()) email else (localEmail ?: "")
    
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

    var verificationState by remember { mutableStateOf<FaceVerificationState>(FaceVerificationState.Verifying) }
    var matchCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(color = Color.Black)) {
            // Simple circular single-scan camera for login
            Box(modifier = Modifier.align(Alignment.Center)) {
                if (verificationState is FaceVerificationState.Verifying) {
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

                                if (effectiveEmail.isBlank()) {
                                    throw Exception("Email requis. Veuillez vous connecter manuellement une première fois.")
                                }

                                // 1. Récupérer l'embedding depuis Firestore (par email)
                                val (uid, storedEmbedding) = FaceEmbeddingRepository.getFaceEmbeddingByEmail(effectiveEmail)
                                    .getOrThrow()

                                // 2. Normalisation et similarité locale
                                val liveNorm = com.akartis.faceauth.ml.EmbeddingMath.l2Normalize(liveEmbedding)
                                val storedNorm = com.akartis.faceauth.ml.EmbeddingMath.l2Normalize(storedEmbedding)
                                val similarity = com.akartis.faceauth.ml.EmbeddingMath.cosineSimilarity(liveNorm, storedNorm)

                                if (similarity < 0.65f) {
                                    // Failure: switch to Error state and show overlay
                                    val msg = "Visage non reconnu (sim: $similarity)"
                                    verificationState = FaceVerificationState.Error(msg)
                                    matchCallback = {
                                        isProcessing = false
                                        statusMessage = null
                                        failedAttempts += 1
                                        if (failedAttempts >= MAX_ATTEMPTS) {
                                            errorMessage = "$MAX_ATTEMPTS tentatives échouées. Connectez-vous avec votre mot de passe."
                                            onBack()
                                        } else {
                                            val remaining = MAX_ATTEMPTS - failedAttempts
                                            errorMessage = "Visage non reconnu (sim: $similarity) ($remaining tentative${if (remaining > 1) "s" else ""} restante${if (remaining > 1) "s" else ""})"
                                        }
                                    }
                                } else {
                                    // Success: show success state then continue login
                                    verificationState = FaceVerificationState.Success
                                    matchCallback = {
                                        scope.launch {
                                            try {
                                                val credentials = com.akartis.faceauth.data.EncryptedCredentialStore.load(context)
                                                if (credentials == null || credentials.first != effectiveEmail.trim().lowercase()) {
                                                    throw Exception("Identifiants locaux introuvables. Connectez-vous manuellement.")
                                                }

                                                com.akartis.faceauth.data.AuthRepository.login(credentials.first, credentials.second)
                                                    .getOrThrow()

                                                onLoginSuccess()
                                            } catch (e: Exception) {
                                                throw e
                                            }
                                        }
                                    }
                                }

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
                    },
                    modifier = Modifier
                        .size(260.dp)
                        .clip(CircleShape),
                    showHeader = false,
                    showSegmentBar = false
                )
                }

                // Match / Failure overlays and state handling
                    LaunchedEffect(verificationState) {
                        when (val st = verificationState) {
                            is FaceVerificationState.Success, is FaceVerificationState.Error -> {
                                delay(900)
                                matchCallback?.invoke()
                                matchCallback = null
                                if (st is FaceVerificationState.Error) {
                                    verificationState = FaceVerificationState.Verifying
                                }
                            }
                            else -> Unit
                        }
                    }

                    when (val st = verificationState) {
                        is FaceVerificationState.Success -> {
                            // Success screen only (camera already removed because we don't compose it when not Verifying)
                            val scale by animateFloatAsState(targetValue = 1f, animationSpec = tween(durationMillis = 350))
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier
                                        .size(240.dp)
                                        .scale(scale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00C853)), contentAlignment = Alignment.Center) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = "OK", tint = Color.White, modifier = Modifier.size(96.dp))
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(text = "Face Matched,\nyou are logged in", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        is FaceVerificationState.Error -> {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier
                                        .size(140.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD32F2F)), contentAlignment = Alignment.Center) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "NO", tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(text = (st as FaceVerificationState.Error).message, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        else -> Unit
                    }
                }

        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Only show verification message while verifying
            if (verificationState is FaceVerificationState.Verifying) {
                statusMessage?.let {
                    Text(
                        text = it,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
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
