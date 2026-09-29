@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.engine.ui.components

import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Engine shared Material 3 Expressive progress / loading indicators.
 *
 * These are the OFFICIAL M3 Expressive components (no hand-rolled
 * approximations): the wavy/"squiggly" linear + circular progress indicators
 * and the morphing-shape [LoadingIndicator]. Generated apps should use these
 * instead of the legacy flat LinearProgressIndicator/CircularProgressIndicator
 * when they want the expressive look (the legacy ones still exist for plain
 * use cases; these are the expressive defaults for the Engine).
 */

/** Expressive determinate wavy linear progress indicator. [progress] in 0f..1f. */
@Composable
fun EngineLinearWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    LinearWavyProgressIndicator(progress = progress, modifier = modifier)
}

/** Expressive indeterminate wavy linear progress indicator. */
@Composable
fun EngineLinearWavyProgress(
    modifier: Modifier = Modifier
) {
    LinearWavyProgressIndicator(modifier = modifier)
}

/** Expressive determinate wavy circular progress indicator. [progress] in 0f..1f. */
@Composable
fun EngineCircularWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    CircularWavyProgressIndicator(progress = progress, modifier = modifier)
}

/** Expressive indeterminate wavy circular progress indicator. */
@Composable
fun EngineCircularWavyProgress(
    modifier: Modifier = Modifier
) {
    CircularWavyProgressIndicator(modifier = modifier)
}

/** Expressive indeterminate loading indicator (morphing-shape spinner). */
@Composable
fun EngineLoadingIndicator(
    modifier: Modifier = Modifier
) {
    LoadingIndicator(modifier = modifier)
}

/** Expressive determinate loading indicator. [progress] in 0f..1f. */
@Composable
fun EngineLoadingIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    LoadingIndicator(progress = progress, modifier = modifier)
}
