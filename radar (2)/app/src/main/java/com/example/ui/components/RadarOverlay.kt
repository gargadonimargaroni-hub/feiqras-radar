package com.example.ui.components

import android.graphics.Rect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.example.ml.TrackedFaceUi
import com.example.ui.theme.RadarCyanPrimary
import com.example.ui.theme.RadarDangerRed
import com.example.ui.theme.RadarWarningAmber
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RadarOverlay(
    modifier: Modifier = Modifier,
    isScanning: Boolean = true,
    trackedFaces: List<TrackedFaceUi> = emptyList(),
    isDangerAlert: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DangerPulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxRadius = min(size.width, size.height) * 0.46f

        // Draw radar grid rings
        val ringCount = 4
        val gridColor = if (isDangerAlert) {
            RadarDangerRed.copy(alpha = 0.35f * pulseAlpha)
        } else {
            RadarCyanPrimary.copy(alpha = 0.22f)
        }

        for (i in 1..ringCount) {
            val r = maxRadius * (i.toFloat() / ringCount)
            drawCircle(
                color = gridColor,
                radius = r,
                center = Offset(centerX, centerY),
                style = Stroke(width = if (i == ringCount) 2.5f else 1.2f)
            )
        }

        // Draw crosshair axes
        drawLine(
            color = gridColor,
            start = Offset(centerX - maxRadius, centerY),
            end = Offset(centerX + maxRadius, centerY),
            strokeWidth = 1f
        )
        drawLine(
            color = gridColor,
            start = Offset(centerX, centerY - maxRadius),
            end = Offset(centerX, centerY + maxRadius),
            strokeWidth = 1f
        )

        // Draw rotating sweep beam when scanning
        if (isScanning) {
            val rad = Math.toRadians(sweepAngle.toDouble())
            val sweepEndX = (centerX + maxRadius * cos(rad)).toFloat()
            val sweepEndY = (centerY + maxRadius * sin(rad)).toFloat()

            // Sweep line
            val beamColor = if (isDangerAlert) RadarDangerRed else RadarCyanPrimary
            drawLine(
                color = beamColor.copy(alpha = 0.85f),
                start = Offset(centerX, centerY),
                end = Offset(sweepEndX, sweepEndY),
                strokeWidth = 2.5f
            )

            // Sweep wedge (phosphor trail)
            val trailPath = Path().apply {
                moveTo(centerX, centerY)
                val trailAngle = 38.0
                for (a in 0..trailAngle.toInt() step 4) {
                    val angleRad = Math.toRadians((sweepAngle - a).toDouble())
                    val x = (centerX + maxRadius * cos(angleRad)).toFloat()
                    val y = (centerY + maxRadius * sin(angleRad)).toFloat()
                    lineTo(x, y)
                }
                close()
            }

            drawPath(
                path = trailPath,
                color = beamColor.copy(alpha = 0.12f)
            )
        }

        // Center hub pip
        drawCircle(
            color = if (isDangerAlert) RadarDangerRed else RadarCyanPrimary,
            radius = 5f,
            center = Offset(centerX, centerY)
        )

        // Draw face reticles
        for (face in trackedFaces) {
            drawFaceReticle(face, isDangerAlert, pulseAlpha)
        }
    }
}

private fun DrawScope.drawFaceReticle(
    face: TrackedFaceUi,
    isDangerAlert: Boolean,
    pulseAlpha: Float
) {
    val box = face.boundingBox

    // Map camera coordinate box to Canvas coordinates
    val left = box.left.toFloat()
    val top = box.top.toFloat()
    val right = box.right.toFloat()
    val bottom = box.bottom.toFloat()
    val width = right - left
    val height = bottom - top

    if (width <= 0 || height <= 0) return

    val bracketLen = min(width, height) * 0.28f
    val strokeWidth = if (face.isRecognized) 4.5f else 2.5f
    val reticleColor = if (face.isRecognized) {
        RadarDangerRed.copy(alpha = pulseAlpha)
    } else {
        RadarCyanPrimary.copy(alpha = 0.65f)
    }

    // Top-Left corner
    drawLine(reticleColor, Offset(left, top + bracketLen), Offset(left, top), strokeWidth)
    drawLine(reticleColor, Offset(left, top), Offset(left + bracketLen, top), strokeWidth)

    // Top-Right corner
    drawLine(reticleColor, Offset(right - bracketLen, top), Offset(right, top), strokeWidth)
    drawLine(reticleColor, Offset(right, top), Offset(right, top + bracketLen), strokeWidth)

    // Bottom-Left corner
    drawLine(reticleColor, Offset(left, bottom - bracketLen), Offset(left, bottom), strokeWidth)
    drawLine(reticleColor, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeWidth)

    // Bottom-Right corner
    drawLine(reticleColor, Offset(right - bracketLen, bottom), Offset(right, bottom), strokeWidth)
    drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - bracketLen), strokeWidth)

    // If recognized, draw name banner and target reticle
    if (face.isRecognized && face.personName != null) {
        val bannerY = top - 18f
        val pct = (face.confidence * 100).toInt()
        val text = "TARGET: ${face.personName.uppercase()} ($pct%)"

        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 34f
                isFakeBoldText = true
                setShadowLayer(8f, 0f, 0f, android.graphics.Color.RED)
            }
            val bgPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(210, 220, 20, 60)
            }
            val textWidth = paint.measureText(text)
            drawRect(
                left,
                bannerY - 34f,
                left + textWidth + 24f,
                bannerY + 12f,
                bgPaint
            )
            drawText(text, left + 12f, bannerY, paint)
        }
    }
}
