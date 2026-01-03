/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.aospa.sense.R
import co.aospa.sense.ui.components.CameraPreview
import co.aospa.sense.ui.components.CircularProgress
import co.aospa.sense.ui.components.FaceDetectionOverlay
import co.aospa.sense.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun EnrollScreen(
    progress: Float,
    isError: Boolean,
    errorMessage: String?,
    isFaceDetected: Boolean,
    onBackPressed: () -> Unit
) {
    var titleVisible by remember { mutableStateOf(false) }
    var descVisible by remember { mutableStateOf(false) }
    var ringVisible by remember { mutableStateOf(false) }
    val isComplete = progress >= 100f

    LaunchedEffect(Unit) {
        delay(100)
        titleVisible = true
        delay(SenseMotion.StaggerDelayMs.toLong())
        descVisible = true
        delay(SenseMotion.StaggerDelayMs.toLong())
        ringVisible = true
    }

    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(SenseMotion.EntranceDurationMs, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (titleVisible) 0f else 30f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "titleOffset"
    )

    val descAlpha by animateFloatAsState(
        targetValue = if (descVisible) 1f else 0f,
        animationSpec = tween(SenseMotion.EntranceDurationMs, easing = FastOutSlowInEasing),
        label = "descAlpha"
    )
    val descOffset by animateFloatAsState(
        targetValue = if (descVisible) 0f else 30f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "descOffset"
    )

    val ringScale by animateFloatAsState(
        targetValue = if (ringVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "ringScale"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (ringVisible) 1f else 0f,
        animationSpec = tween(SenseMotion.EntranceDurationMs),
        label = "ringAlpha"
    )

    val completionScale by animateFloatAsState(
        targetValue = if (isComplete) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "completionScale"
    )

    SenseTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundGradient())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = stringResource(R.string.face_enroll_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = titleAlpha
                                translationY = titleOffset
                            }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.face_enroll_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                alpha = descAlpha
                                translationY = descOffset
                            }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .graphicsLayer {
                                alpha = ringAlpha
                                scaleX = ringScale * completionScale
                                scaleY = ringScale * completionScale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgress(
                            progress = progress,
                            size = 300.dp,
                            strokeWidth = 14.dp,
                            isError = isError
                        )

                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize()
                            )

                            FaceDetectionOverlay(
                                isFaceDetected = isFaceDetected,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    AnimatedVisibility(errorMessage != null && errorMessage.isNotBlank()) {
                        Surface(
                            color = if (isError) 
                                MaterialTheme.colorScheme.errorContainer 
                            else 
                                MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isError) 
                                    MaterialTheme.colorScheme.onErrorContainer 
                                else 
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AnimatedVisibility(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "visibility"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    if (alpha > 0f) {
        Box(
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
        ) {
            content()
        }
    }
}

data class EnrollUiState(
    val progress: Float = 0f,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val isFaceDetected: Boolean = false,
    val isComplete: Boolean = false
)
