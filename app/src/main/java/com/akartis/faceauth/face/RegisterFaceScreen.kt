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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REQUIRED_CAPTURES = 5

@Composable
fun RegisterFaceScreen(
    onRegistrationComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val faceNetHelper = remember { FaceNetHelper(context) }
    val embeddings = remember { mutableStateListOf<FloatArray>() }

    var captureCount by remember { mutableIntStateOf(0) }
    var captureSession by remember { mutableIntStateOf(0) }
    var isBusy by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { faceNetHelper.close() }
    }

    fun retryFirestoreSave() {
        if (embeddings.size < REQUIRED_CAPTURES || isSaving) return
        isSaving = true
        errorMessage = null
        scope.launch {
            saveFinalEmbedding(
                embeddings = embeddings.toList(),
                onSuccess = onRegistrationComplete,
                onError = { message ->
                    errorMessage = message
                    isSaving = false
                }
            )
        }
    }

    val nextCaptureIndex = (captureCount + 1).coerceAtMost(REQUIRED_CAPTURES)
    val instructionText = when {
        isBusy -> "Analyse Capture $nextCaptureIndex/$REQUIRED_CAPTURES..."
        else -> "Capture $nextCaptureIndex/$REQUIRED_CAPTURES — Place ton visage devant la caméra"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isSaving -> {
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
                        text = "Enregistrement de l'embedding dans Firestore...",
                        textAlign = TextAlign.Center
                    )
                }
            }

            errorMessage != null && captureCount >= REQUIRED_CAPTURES -> {
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
                    Button(onClick = { retryFirestoreSave() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Réessayer la sauvegarde")
                    }
                }
            }

            else -> {
                FaceCaptureScreen(
                    instructionText = instructionText,
                    captureSession = captureSession,
                    captureEnabled = !isBusy && captureCount < REQUIRED_CAPTURES,
                    onFaceCaptured = { bitmap: Bitmap ->
                        if (isBusy || isSaving || captureCount >= REQUIRED_CAPTURES) {
                            captureSession++
                            return@FaceCaptureScreen
                        }

                        isBusy = true
                        errorMessage = null

                        scope.launch {
                            try {
                                val embedding = withContext(Dispatchers.Default) {
                                    faceNetHelper.getEmbedding(bitmap)
                                }

                                embeddings.add(embedding)
                                captureCount = embeddings.size

                                if (embeddings.size >= REQUIRED_CAPTURES) {
                                    isSaving = true
                                    saveFinalEmbedding(
                                        embeddings = embeddings.toList(),
                                        onSuccess = onRegistrationComplete,
                                        onError = { message ->
                                            errorMessage = message
                                            isSaving = false
                                            isBusy = false
                                        }
                                    )
                                } else {
                                    isBusy = false
                                    captureSession++
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errorMessage = e.message ?: "Erreur lors de l'analyse du visage"
                                isBusy = false
                                captureSession++
                            }
                        }
                    }
                )

                errorMessage?.takeIf { captureCount < REQUIRED_CAPTURES }?.let { error ->
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

private suspend fun saveFinalEmbedding(
    embeddings: List<FloatArray>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val uid = AuthRepository.getCurrentUserId()
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
            email = AuthRepository.getCurrentUserEmail()
        ).getOrElse { throw it }

        onSuccess()
    } catch (e: Exception) {
        e.printStackTrace()
        onError(e.message ?: "Échec de la sauvegarde Firestore")
    }
}
