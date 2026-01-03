/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun TryAgainIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    animate: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.error
    val secondaryColor = MaterialTheme.colorScheme.errorContainer
    val outlineColor = MaterialTheme.colorScheme.outline

    val infiniteTransition = rememberInfiniteTransition(label = "sadFace")

    val wobble by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble"
    )

    val tearOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tear"
    )

    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val currentWobble = if (animate) wobble else 0f
    val currentBreathe = if (animate) breathe else 1f
    val currentTearOffset = if (animate) tearOffset else 0f

    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = size.toPx()
        val center = Offset(canvasSize / 2, canvasSize / 2)
        val faceRadius = canvasSize * 0.4f
        val strokeWidth = canvasSize * 0.04f

        rotate(currentWobble, pivot = center) {
            drawCircle(
                color = secondaryColor,
                radius = faceRadius * currentBreathe,
                center = center
            )

            drawCircle(
                color = primaryColor,
                radius = faceRadius * currentBreathe,
                center = center,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val eyeY = center.y - faceRadius * 0.15f
            val eyeSpacing = faceRadius * 0.35f
            val eyeWidth = faceRadius * 0.2f
            val eyeHeight = faceRadius * 0.12f

            val leftEyeCenter = Offset(center.x - eyeSpacing, eyeY)
            val rightEyeCenter = Offset(center.x + eyeSpacing, eyeY)

            drawArc(
                color = primaryColor,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(leftEyeCenter.x - eyeWidth/2, leftEyeCenter.y - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round)
            )

            drawArc(
                color = primaryColor,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(rightEyeCenter.x - eyeWidth/2, rightEyeCenter.y - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round)
            )

            val mouthY = center.y + faceRadius * 0.35f
            val mouthWidth = faceRadius * 0.5f
            val mouthHeight = faceRadius * 0.25f

            val mouthPath = Path().apply {
                moveTo(center.x - mouthWidth/2, mouthY)
                quadraticTo(
                    center.x, mouthY - mouthHeight,
                    center.x + mouthWidth/2, mouthY
                )
            }

            drawPath(
                path = mouthPath,
                color = primaryColor,
                style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round)
            )

            if (animate) {
                val tearStartY = leftEyeCenter.y + eyeHeight
                val tearEndY = tearStartY + faceRadius * 0.4f
                val tearY = tearStartY + (tearEndY - tearStartY) * currentTearOffset
                val tearAlpha = if (currentTearOffset < 0.2f) currentTearOffset / 0.2f
                              else if (currentTearOffset > 0.8f) (1f - currentTearOffset) / 0.2f
                              else 1f

                drawCircle(
                    color = primaryColor.copy(alpha = tearAlpha * 0.6f),
                    radius = strokeWidth * 0.6f,
                    center = Offset(leftEyeCenter.x + eyeWidth * 0.1f, tearY)
                )
            }
        }
    }
}

@Composable
fun SuccessFaceIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.primaryContainer

    val infiniteTransition = rememberInfiniteTransition(label = "happyFace")

    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = size.toPx()
        val center = Offset(canvasSize / 2, canvasSize / 2 - bounce * 8f)
        val faceRadius = canvasSize * 0.4f
        val strokeWidth = canvasSize * 0.04f

        drawCircle(
            color = secondaryColor,
            radius = faceRadius,
            center = center
        )

        drawCircle(
            color = primaryColor,
            radius = faceRadius,
            center = center,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        val eyeY = center.y - faceRadius * 0.1f
        val eyeSpacing = faceRadius * 0.35f
        val eyeRadius = faceRadius * 0.08f

        drawCircle(
            color = primaryColor,
            radius = eyeRadius,
            center = Offset(center.x - eyeSpacing, eyeY)
        )
        drawCircle(
            color = primaryColor,
            radius = eyeRadius,
            center = Offset(center.x + eyeSpacing, eyeY)
        )

        val smileY = center.y + faceRadius * 0.2f
        val smileWidth = faceRadius * 0.6f
        val smileHeight = faceRadius * 0.3f

        val smilePath = Path().apply {
            moveTo(center.x - smileWidth/2, smileY)
            quadraticTo(
                center.x, smileY + smileHeight,
                center.x + smileWidth/2, smileY
            )
        }

        drawPath(
            path = smilePath,
            color = primaryColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
