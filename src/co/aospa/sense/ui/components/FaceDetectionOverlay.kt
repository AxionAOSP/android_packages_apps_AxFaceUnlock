/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

@Composable
fun FaceDetectionOverlay(
    isFaceDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val detectedColor = MaterialTheme.colorScheme.primary
    val searchingColor = MaterialTheme.colorScheme.outlineVariant

    val infiniteTransition = rememberInfiniteTransition(label = "overlay")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val currentScale by animateFloatAsState(
        targetValue = if (isFaceDetected) 1f else breathingScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val cornerAlpha by animateFloatAsState(
        targetValue = if (isFaceDetected) 1f else pulseAlpha,
        animationSpec = tween(300),
        label = "cornerAlpha"
    )

    val cornerColor by animateColorAsState(
        targetValue = if (isFaceDetected) detectedColor else searchingColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cornerColor"
    )

    val glowRadius by animateFloatAsState(
        targetValue = if (isFaceDetected) 20f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glowRadius"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val padding = 32.dp.toPx()
        val cornerLength = 48.dp.toPx()
        val strokeWidth = 5.dp.toPx()
        val cornerRadius = 16.dp.toPx()
        
        val rectLeft = padding
        val rectTop = padding
        val rectRight = size.width - padding
        val rectBottom = size.height - padding
        val rectWidth = rectRight - rectLeft
        val rectHeight = rectBottom - rectTop

        val color = cornerColor.copy(alpha = cornerAlpha)

        scale(currentScale, pivot = center) {
            if (glowRadius > 0) {
                val glowColor = detectedColor.copy(alpha = 0.15f * (glowRadius / 20f))
                drawRoundRect(
                    color = glowColor,
                    topLeft = Offset(rectLeft - glowRadius, rectTop - glowRadius),
                    size = Size(rectWidth + glowRadius * 2, rectHeight + glowRadius * 2),
                    cornerRadius = CornerRadius(cornerRadius + glowRadius),
                    style = Stroke(width = glowRadius * 2),
                    blendMode = BlendMode.Plus
                )
            }

            val path = Path().apply {
                moveTo(rectLeft + cornerRadius, rectTop)
                lineTo(rectLeft + cornerLength, rectTop)
                moveTo(rectLeft, rectTop + cornerRadius)
                arcTo(
                    rect = Rect(
                        rectLeft, rectTop,
                        rectLeft + cornerRadius * 2, rectTop + cornerRadius * 2
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                moveTo(rectLeft, rectTop + cornerLength)
                lineTo(rectLeft, rectTop + cornerRadius)
            }

            path.apply {
                moveTo(rectRight - cornerLength, rectTop)
                lineTo(rectRight - cornerRadius, rectTop)
                arcTo(
                    rect = Rect(
                        rectRight - cornerRadius * 2, rectTop,
                        rectRight, rectTop + cornerRadius * 2
                    ),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(rectRight, rectTop + cornerLength)
            }

            path.apply {
                moveTo(rectLeft, rectBottom - cornerLength)
                lineTo(rectLeft, rectBottom - cornerRadius)
                arcTo(
                    rect = Rect(
                        rectLeft, rectBottom - cornerRadius * 2,
                        rectLeft + cornerRadius * 2, rectBottom
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(rectLeft + cornerLength, rectBottom)
            }

            path.apply {
                moveTo(rectRight - cornerLength, rectBottom)
                lineTo(rectRight - cornerRadius, rectBottom)
                arcTo(
                    rect = Rect(
                        rectRight - cornerRadius * 2, rectBottom - cornerRadius * 2,
                        rectRight, rectBottom
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(rectRight, rectBottom - cornerLength)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            if (isFaceDetected) {
                val dotPositions = listOf(
                    Offset(rectLeft + cornerRadius, rectTop + cornerRadius),
                    Offset(rectRight - cornerRadius, rectTop + cornerRadius),
                    Offset(rectLeft + cornerRadius, rectBottom - cornerRadius),
                    Offset(rectRight - cornerRadius, rectBottom - cornerRadius)
                )
                dotPositions.forEach { pos ->
                    drawCircle(
                        color = detectedColor.copy(alpha = 0.6f),
                        radius = 4.dp.toPx(),
                        center = pos
                    )
                }
            }
        }
    }
}
