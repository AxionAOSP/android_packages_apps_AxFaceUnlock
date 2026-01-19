/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package co.aospa.sense.ui.theme

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

object SenseColors {
    val ProgressTrack: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant

    val ProgressActive: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    val ProgressError: Color
        @Composable get() = MaterialTheme.colorScheme.error

    val GlowPrimary: Color
        @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)

    val GlowError: Color
        @Composable get() = MaterialTheme.colorScheme.error.copy(alpha = 0.4f)

    val GlowSuccess: Color
        @Composable get() = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
}

object SenseMotion {
    val EntranceSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val PopSpec: SpringSpec<Float> = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessLow
    )

    val GentleSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessVeryLow
    )

    const val StaggerDelayMs = 50
    const val EntranceDurationMs = 600
}

@Composable
fun backgroundGradient(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        colors = listOf(
            colorScheme.surface,
            colorScheme.surfaceContainerLowest
        )
    )
}

val SenseTypography: Typography
    @Composable get() {
        val defaultTypography = Typography()
        return Typography(
            displayLarge = defaultTypography.displayLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp
            ),
            displayMedium = defaultTypography.displayMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.25).sp
            ),
            displaySmall = defaultTypography.displaySmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp
            ),
            headlineLarge = defaultTypography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            headlineMedium = defaultTypography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            titleLarge = defaultTypography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            labelLarge = defaultTypography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        )
    }

@Composable
fun SenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalView.current.context
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = SenseTypography,
        content = content
    )
}
