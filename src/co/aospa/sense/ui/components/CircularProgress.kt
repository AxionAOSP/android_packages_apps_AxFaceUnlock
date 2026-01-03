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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.aospa.sense.ui.theme.SenseColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    strokeWidth: Dp = 10.dp,
    isError: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 100f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )

    var showError by remember { mutableStateOf(false) }
    val isComplete = progress >= 100f

    LaunchedEffect(isError) {
        if (isError) {
            showError = true
            delay(600)
            showError = false
        }
    }

    val completionScale by animateFloatAsState(
        targetValue = if (isComplete) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "completionScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val gradientRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientRotation"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val successColor = MaterialTheme.colorScheme.tertiary

    val progressColor by animateColorAsState(
        targetValue = when {
            showError -> errorColor
            isComplete -> successColor
            else -> activeColor
        },
        animationSpec = tween(200),
        label = "color"
    )

    val glowColor = when {
        showError -> SenseColors.GlowError
        isComplete -> SenseColors.GlowSuccess
        else -> SenseColors.GlowPrimary
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = completionScale; scaleY = completionScale }
    ) {
        val stroke = strokeWidth.toPx()
        val glowStroke = stroke * 2.5f
        val diameter = size.toPx() - glowStroke
        val topLeft = Offset(glowStroke / 2, glowStroke / 2)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val sweepAngle = animatedProgress * 3.6f
        if (sweepAngle > 0) {
            drawArc(
                color = glowColor.copy(alpha = glowAlpha * 0.6f),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = glowStroke, cap = StrokeCap.Round),
                blendMode = BlendMode.Plus
            )

            val gradientBrush = Brush.sweepGradient(
                0f to progressColor,
                0.5f to secondaryColor,
                1f to progressColor
            )
            
            rotate(gradientRotation - 90f, pivot = center) {
                drawArc(
                    brush = gradientBrush,
                    startAngle = 0f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            val endAngleRad = Math.toRadians((-90.0 + sweepAngle))
            val dotRadius = stroke / 2
            val arcRadius = diameter / 2
            val dotX = center.x + arcRadius * cos(endAngleRad).toFloat()
            val dotY = center.y + arcRadius * sin(endAngleRad).toFloat()
            
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = dotRadius * 0.6f,
                center = Offset(dotX, dotY),
                blendMode = BlendMode.Plus
            )
        }
    }
}

@Composable
fun FaceEnrollmentRing(
    progress: Float,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgress(
            progress = progress,
            size = 300.dp,
            strokeWidth = 14.dp,
            isError = isError
        )
    }
}
