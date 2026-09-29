package com.forgebuild.engine

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens

/** Starter placeholder shown until the generating AI writes the real UI. */
@Composable
fun StarterScreen() {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(SpacingTokens.Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(EngineIcons.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(SpacingTokens.Spacing.md))
            Text("ForgeBuild Engine ready", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(SpacingTokens.Spacing.xs))
            Text(
                "Replace StarterScreen() with this app's real UI. Material 3 Expressive " +
                "(dynamic color, shape scale, tonal elevation, type scale, spacing and " +
                "motion tokens) and Material Symbols icons are wired.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
