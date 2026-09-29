package org.SamilliMed.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

enum class VitalityState { HEALTHY, WARNING, CRITICAL, GHOST }

@Composable
fun VitalityCard(
    medicineName: String,
    dosage: String,
    stock: Int,
    expiry: String,
    state: VitalityState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Organic, non-uniform corner radii for a physical, premium feel
    val organicShape = RoundedCornerShape(
        topStart = 32.dp, topEnd = 38.dp,
        bottomEnd = 34.dp, bottomStart = 40.dp
    )

    // Pulsing animation for Warning/Critical states
    val infiniteTransition = rememberInfiniteTransition(label = "vitalityPulse")
    val shadowElevation by infiniteTransition.animateFloat(
        initialValue = 15f,
        targetValue = if (state == VitalityState.CRITICAL) 35f else 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shadowPulse"
    )

    // Dynamic colors based on state
    val (bgBrush, textColor, glowColor) = when (state) {
        VitalityState.HEALTHY -> Triple(
            Brush.linearGradient(listOf(Color(0xFF5C7A65), Color(0xFF5C7A65).copy(alpha = 0.8f))),
            Color.White,
            Color.Transparent
        )
        VitalityState.WARNING -> Triple(
            Brush.linearGradient(listOf(Color(0xFFD4A373), Color(0xFFD4A373).copy(alpha = 0.8f))),
            Color.White,
            Color(0xFFD4A373).copy(alpha = 0.4f)
        )
        VitalityState.CRITICAL -> Triple(
            Brush.linearGradient(listOf(Color(0xFFC06C55), Color(0xFFC06C55).copy(alpha = 0.8f))),
            Color.White,
            Color(0xFFC06C55).copy(alpha = 0.5f)
        )
        VitalityState.GHOST -> Triple(
            Brush.linearGradient(listOf(Color(0xFFE5E5E5), Color(0xFFE5E5E5).copy(alpha = 0.5f))),
            Color(0xFF999999),
            Color.Transparent
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation.dp,
                shape = organicShape,
                ambientColor = glowColor,
                spotColor = glowColor
            )
            .clip(organicShape)
            .background(bgBrush)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = medicineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = dosage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column {
                Text(
                    text = "Stock: $stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.9f),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = "Exp: $expiry",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}