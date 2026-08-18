package com.akartis.faceauth.face

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akartis.faceauth.camera.FaceCaptureScreen
import com.akartis.faceauth.ui.theme.FaceAuthGreen
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

enum class DirectionalHint {
    NONE, LEFT, RIGHT, UP, DOWN
}

@Composable
fun SegmentedProgressRing(
    progress: Float, // 0f..1f
    segments: Int = 60,
    size: Dp = 320.dp,
    ringThickness: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val animated = animateFloatAsState(targetValue = progress)
    Canvas(modifier = modifier.size(size)) {
        val outer = size.toPx()
        val stroke = ringThickness.toPx()
        val gap = 2f // degrees
        val sweep = 360f / segments
        val radius = outer / 2f
        val rect = androidx.compose.ui.geometry.Rect(0f, 0f, outer, outer)
        val center = Offset(radius, radius)
        // Smooth per-segment fill with fractional progress for the current segment
        val segProgress = (animated.value.coerceIn(0f, 1f)) * segments
        val completed = kotlin.math.floor(segProgress).toInt()
        val frac = segProgress - completed

        for (i in 0 until segments) {
            val start = -90f + i * sweep + gap / 2f
            val sweepAngle = sweep - gap

            val color = when {
                i < completed -> FaceAuthGreen
                i == completed -> lerp(Color.White.copy(alpha = 0.28f), FaceAuthGreen, frac)
                else -> Color.White.copy(alpha = 0.28f)
            }

            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset.Zero,
                size = Size(outer, outer),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun DirectionalArc(
    direction: DirectionalHint,
    size: Dp = 320.dp,
    arcThickness: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    if (direction == DirectionalHint.NONE) return
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse)
    )
    val sway by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse)
    )

    Canvas(modifier = modifier.size(size)) {
        val outer = size.toPx()
        val stroke = arcThickness.toPx()
        val radius = outer / 2f
        val center = Offset(radius, radius)

        val color = Color(0xFF6C63FF) // subtle blue/purple
        val arcSweepBase = 40f

        when (direction) {
            DirectionalHint.LEFT -> {
                drawArc(
                    color = color.copy(alpha = alpha),
                    startAngle = 140f + sway / 2f,
                    sweepAngle = arcSweepBase + kotlin.math.abs(sway) / 2f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(outer, outer),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(8f))
                )
            }
            DirectionalHint.RIGHT -> {
                drawArc(
                    color = color.copy(alpha = alpha),
                    startAngle = -40f + sway / 2f,
                    sweepAngle = arcSweepBase + kotlin.math.abs(sway) / 2f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(outer, outer),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(8f))
                )
            }
            DirectionalHint.UP -> {
                drawArc(
                    color = color.copy(alpha = alpha),
                    startAngle = -140f + sway / 2f,
                    sweepAngle = arcSweepBase + kotlin.math.abs(sway) / 2f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(outer, outer),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(8f))
                )
            }
            DirectionalHint.DOWN -> {
                drawArc(
                    color = color.copy(alpha = alpha),
                    startAngle = 40f + sway / 2f,
                    sweepAngle = arcSweepBase + kotlin.math.abs(sway) / 2f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(outer, outer),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(8f))
                )
            }
            else -> Unit
        }
    }
}

@Composable
fun EnrollmentCircularLayout(
    instructionText: String,
    stepIndex: Int,
    progress: Float,
    captureEnabled: Boolean,
    onFaceAnalyzed: (croppedBitmap: android.graphics.Bitmap, headEulerAngleY: Float?, leftEyeOpenProbability: Float?, rightEyeOpenProbability: Float?) -> Unit
) {
    val dir = when (stepIndex) {
        1 -> DirectionalHint.RIGHT
        2 -> DirectionalHint.LEFT
        3 -> DirectionalHint.NONE // blink
        else -> DirectionalHint.NONE
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Camera clipped to a circle
                FaceCaptureScreen(
                    instructionText = instructionText,
                    currentStep = stepIndex,
                    totalSteps = 5,
                    captureEnabled = captureEnabled,
                    onFaceAnalyzed = onFaceAnalyzed,
                    modifier = Modifier
                        .size(320.dp)
                        .clip(CircleShape),
                    showHeader = false,
                    showSegmentBar = false
                )

                // Segmented ring overlay
                SegmentedProgressRing(progress = progress, size = 360.dp)

                // Directional hint overlay
                DirectionalArc(direction = dir, size = 360.dp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = instructionText,
                color = Color.White,
                fontSize = 18.sp,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Step ${stepIndex + 1} of 5",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
