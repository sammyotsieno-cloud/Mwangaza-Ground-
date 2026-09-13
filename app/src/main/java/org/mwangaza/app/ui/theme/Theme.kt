package org.mwangaza.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MwangazaDarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),          // Bright blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),

    secondary = Color(0xFFFBBF24),        // Soft gold
    onSecondary = Color(0xFF1C1917),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),

    tertiary = Color(0xFF60A5FA),
    background = Color(0xFF020617),       // Almost black
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0F172A),          // Dark navy cards
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),

    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color.White
)

@Composable
fun MwangazaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MwangazaDarkColorScheme,
        content = content
    )
}
