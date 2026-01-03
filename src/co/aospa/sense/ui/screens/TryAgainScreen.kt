/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.aospa.sense.R
import co.aospa.sense.ui.components.TryAgainIllustration
import co.aospa.sense.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun TryAgainScreen(
    onTryAgain: () -> Unit,
    showButton: Boolean = true
) {
    var illustrationVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var descVisible by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        illustrationVisible = true
        delay(SenseMotion.StaggerDelayMs.toLong() * 2)
        titleVisible = true
        delay(SenseMotion.StaggerDelayMs.toLong())
        descVisible = true
        delay(SenseMotion.StaggerDelayMs.toLong() * 2)
        buttonVisible = true
    }

    val illustrationScale by animateFloatAsState(
        targetValue = if (illustrationVisible) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "illustrationScale"
    )
    val illustrationAlpha by animateFloatAsState(
        targetValue = if (illustrationVisible) 1f else 0f,
        animationSpec = tween(SenseMotion.EntranceDurationMs),
        label = "illustrationAlpha"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(SenseMotion.EntranceDurationMs, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (titleVisible) 0f else 24f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "titleOffset"
    )

    val descAlpha by animateFloatAsState(
        targetValue = if (descVisible) 1f else 0f,
        animationSpec = tween(SenseMotion.EntranceDurationMs, easing = FastOutSlowInEasing),
        label = "descAlpha"
    )
    val descOffset by animateFloatAsState(
        targetValue = if (descVisible) 0f else 24f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "descOffset"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (buttonVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "buttonScale"
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (buttonVisible) 1f else 0f,
        animationSpec = tween(SenseMotion.EntranceDurationMs),
        label = "buttonAlpha"
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
                    Spacer(modifier = Modifier.weight(0.8f))

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = illustrationAlpha
                                scaleX = illustrationScale
                                scaleY = illustrationScale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        TryAgainIllustration(
                            size = 180.dp,
                            animate = true
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = stringResource(R.string.face_try_again_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = titleAlpha
                                translationY = titleOffset
                            }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.face_try_again_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .graphicsLayer {
                                alpha = descAlpha
                                translationY = descOffset
                            }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (showButton) {
                        FilledTonalButton(
                            onClick = onTryAgain,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(56.dp)
                                .graphicsLayer {
                                    alpha = buttonAlpha
                                    scaleX = buttonScale
                                    scaleY = buttonScale
                                },
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.btn_try_again),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
