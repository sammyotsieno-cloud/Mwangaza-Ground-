package org.mwangaza.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SamilliMedSplashScreen(
    onSplashFinished: () -> Unit
) {
    // 1. Animation States
    var startAnimation by remember { mutableStateOf(false) }
    
    // Background fade in
    val bgAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "bgAlpha"
    )

    // Logo scale and bounce (Spring physics for premium feel)
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 200),
        label = "logoAlpha"
    )

    // Text slide up and fade in
    val textOffset by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 20.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "textOffset"
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 400),
        label = "textAlpha"
    )

    // Vitality Pulse (Heartbeat effect behind logo)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // 2. Trigger Animation & Handle Exit
    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500) // Show splash for 2.5 seconds
        
        // Exit animation (scale up and fade out)
        // In a real app, you might animate this out, but for simplicity, we callback
        onSplashFinished()
    }

    // 3. UI Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF4F1EA), // Warm Oat center
                        Color(0xFFE8E4DB)  // Slightly darker oat edge
                    )
                )
            )
            .alpha(bgAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- THE LOGO ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(logoScale)
                    .alpha(logoAlpha)
            ) {
                // Pulsing Vitality Glow behind logo
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF5C7A65).copy(alpha = 0.3f))
                )
                
                // Main Logo Shape (Abstract 'S' / Stacked Leaf)
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF5C7A65)) // Sage Green
                        .padding(24.dp)
                ) {
                    // Abstract stacked layers / 'S' shape in Warm Oat
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF4F1EA))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .align(Alignment.End)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF4F1EA))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFD4A373)) // Amber accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- THE TEXT ---
            androidx.compose.material3.Text(
                text = "SamilliMed",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.05.em,
                color = Color(0xFF1A1A1A),
                modifier = Modifier
                    .offset(y = textOffset)
                    .alpha(textAlpha)
            )
            
            androidx.compose.material3.Text(
                text = "Clinical Inventory, Simplified",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF555555),
                letterSpacing = 0.1.em,
                modifier = Modifier
                    .offset(y = textOffset)
                    .alpha(textAlpha)
                    .padding(top = 8.dp)
            )
        }
    }
}