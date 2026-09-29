package org.mwangaza.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Clinical Zen Palette
val OatBackground = Color(0xFFF4F1EA)
val SagePrimary = Color(0xFF5C7A65)
val SageContainer = Color(0xFFD8E4DB)
val AmberWarning = Color(0xFFD4A373)
val TerracottaCritical = Color(0xFFC06C55)
val TextDark = Color(0xFF1A1A1A)
val TextLight = Color(0xFF555555)

private val ClinicalZenColorScheme = lightColorScheme(
    primary = SagePrimary,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = Color(0xFF1A3A25),

    secondary = AmberWarning,
    onSecondary = Color.White,

    error = TerracottaCritical,
    onError = Color.White,

    background = OatBackground,
    onBackground = TextDark,
    surface = Color.White,
    onSurface = TextDark,
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = TextLight,

    outline = Color(0xFFD1D1D1)
)

@Composable
fun MwangazaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClinicalZenColorScheme,
        content = content
    )
}
