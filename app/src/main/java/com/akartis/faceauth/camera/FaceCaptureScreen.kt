package com.akartis.faceauth.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.akartis.faceauth.ui.theme.FaceAuthGreen
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun FaceCaptureScreen(
    instructionText: String = "Regardez devant vous",
    currentStep: Int = 0,
    totalSteps: Int = 5,
    captureEnabled: Boolean = true,
    onFaceAnalyzed: (croppedBitmap: Bitmap, headEulerAngleY: Float?, leftEyeOpenProbability: Float?, rightEyeOpenProbability: Float?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("La caméra est nécessaire pour continuer")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Autoriser la caméra")
                }
            }
        }
        return
    }

    var faceDetected by remember { mutableStateOf(false) }
    val callbackInFlight = remember { AtomicBoolean(false) }
    val captureEnabledRef = remember { AtomicBoolean(captureEnabled) }
    val onFaceAnalyzedRef = remember { AtomicReference(onFaceAnalyzed) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    LaunchedEffect(captureEnabled) {
        captureEnabledRef.set(captureEnabled)
    }

    LaunchedEffect(onFaceAnalyzed) {
        onFaceAnalyzedRef.set(onFaceAnalyzed)
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Aprem de la caméra
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analyzer = FaceImageAnalyzer(
                        onFaceAnalyzed = { bitmap, headEulerAngleY, leftEyeOpenProbability, rightEyeOpenProbability ->
                            mainExecutor.execute {
                                faceDetected = true
                                if (!captureEnabledRef.get()) return@execute
                                if (!callbackInFlight.compareAndSet(false, true)) return@execute
                                try {
                                    onFaceAnalyzedRef.get().invoke(
                                        bitmap,
                                        headEulerAngleY,
                                        leftEyeOpenProbability,
                                        rightEyeOpenProbability
                                    )
                                } finally {
                                    callbackInFlight.set(false)
                                }
                            }
                        },
                        onNoFace = {
                            mainExecutor.execute {
                                faceDetected = false
                            }
                        }
                    )

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                analyzer.analyze(imageProxy)
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. En-tête : Titre + Consigne (1/5 à 5/5 avec flèches)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 28.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enregistrement du visage",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "${currentStep + 1}/$totalSteps • $instructionText",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 15.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // 3. Cadre carré au milieu avec 4 angles blancs à 90°
        SquareCornerOverlay(
            modifier = Modifier.align(Alignment.Center)
        )

        // 4. Barre de progression horizontale à 5 segments en bas
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (faceDetected && captureEnabled) {
                Text(
                    text = "Visage détecté ✅",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FaceAuthGreen,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            SegmentedProgressBar(
                currentStep = currentStep,
                totalSteps = totalSteps
            )
        }
    }
}

/**
 * Cadre carré au milieu de l'écran composé uniquement de 4 angles blancs à 90°.
 */
@Composable
fun SquareCornerOverlay(
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    strokeWidth: Dp = 4.dp,
    cornerLength: Dp = 36.dp,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = strokeWidth.toPx()
        val len = cornerLength.toPx()

        // Coin haut - gauche
        drawLine(color, Offset(0f, 0f), Offset(len, 0f), strokeWidth = stroke)
        drawLine(color, Offset(0f, 0f), Offset(0f, len), strokeWidth = stroke)

        // Coin haut - droite
        drawLine(color, Offset(w, 0f), Offset(w - len, 0f), strokeWidth = stroke)
        drawLine(color, Offset(w, 0f), Offset(w, len), strokeWidth = stroke)

        // Coin bas - gauche
        drawLine(color, Offset(0f, h), Offset(len, h), strokeWidth = stroke)
        drawLine(color, Offset(0f, h), Offset(0f, h - len), strokeWidth = stroke)

        // Coin bas - droite
        drawLine(color, Offset(w, h), Offset(w - len, h), strokeWidth = stroke)
        drawLine(color, Offset(w, h), Offset(w, h - len), strokeWidth = stroke)
    }
}

/**
 * Barre de progression horizontale divisée en 5 segments égaux.
 * Remplit progressivement chaque segment en vert (1/5 -> 1er vert, 2/5 -> 1er et 2e vert, etc.)
 */
@Composable
fun SegmentedProgressBar(
    currentStep: Int,
    totalSteps: Int = 5,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isCompletedOrCurrent = i <= currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isCompletedOrCurrent) FaceAuthGreen else Color.White.copy(alpha = 0.35f)
                    )
            )
        }
    }
}
