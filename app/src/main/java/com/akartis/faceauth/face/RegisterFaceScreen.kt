package com.akartis.faceauth.face

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.akartis.faceauth.camera.FaceCaptureScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RegisterFaceScreen(
    onRegistrationComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val faceNetHelper = remember {
        FaceNetHelper(context)
    }

    var isProcessing by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("Place ton visage devant la caméra")
    }

    DisposableEffect(Unit) {
        onDispose {
            faceNetHelper.close()
        }
    }

    if (isProcessing) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()

                Text(
                    text = "Analyse du visage...",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

    } else {

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            FaceCaptureScreen(
                instructionText = message,
                onFaceCaptured = { bitmap: Bitmap ->

                    if (!isProcessing) {

                        isProcessing = true

                        scope.launch {

                            try {

                                val embedding =
                                    withContext(Dispatchers.Default) {
                                        faceNetHelper.getEmbedding(bitmap)
                                    }

                                println(
                                    "FACE EMBEDDING SIZE = ${embedding.size}"
                                )

                                println(
                                    "FIRST VALUES = ${
                                        embedding.take(5)
                                    }"
                                )

                                message =
                                    "Visage analysé avec succès ✅"

                                isProcessing = false

                                onRegistrationComplete()

                            } catch (e: Exception) {

                                e.printStackTrace()

                                message =
                                    "Erreur lors de l'analyse du visage"

                                isProcessing = false
                            }
                        }
                    }
                }
            )
        }
    }
}