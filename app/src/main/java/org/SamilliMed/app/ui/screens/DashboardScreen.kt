package org.SamilliMed.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.SamilliMed.app.ui.theme.OatBackground
import org.SamilliMed.app.ui.theme.SagePrimary

private val AmberGlow = Color(0xFFD4A373)
private val GlassWhite = Color.White.copy(alpha = 0.35f)
private val TextDark = Color(0xFF1A1A1A)

@Composable
fun DashboardScreen(
    onFeatureClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OatBackground)
    ) {
        AmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 50.dp, bottom = 100.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Dashboard",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark
                )
                Text(
                    text = "SamilliMed Medical Centre",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF555555),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(dashboardFeatures) { feature ->
                    Glass3DTile(
                        title = feature.title,
                        icon = feature.icon,
                        onClick = { onFeatureClick(feature.route) }
                    )
                }
            }
        }

        GlassDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp),
            onNavigate = onFeatureClick
        )
    }
}

@Composable
private fun AmbientBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = (-70).dp, y = 100.dp)
                .blur(42.dp)
                .background(Color(0xFFD8E4DB).copy(alpha = 0.70f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(190.dp)
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = 280.dp)
                .blur(46.dp)
                .background(AmberGlow.copy(alpha = 0.52f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(170.dp)
                .align(Alignment.BottomStart)
                .offset(x = 70.dp, y = (-120).dp)
                .blur(48.dp)
                .background(Color(0xFFC9D9CC).copy(alpha = 0.58f), CircleShape)
        )
    }
}

@Composable
private fun Glass3DTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .shadow(
                elevation = 15.dp,
                shape = shape,
                ambientColor = SagePrimary.copy(alpha = 0.15f),
                spotColor = AmberGlow.copy(alpha = 0.10f)
            )
            .clip(shape)
            .background(GlassWhite)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.80f),
                        AmberGlow.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.20f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                ),
                shape = shape
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 24.dp,
                        topEnd = 24.dp
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.60f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = SagePrimary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun GlassDock(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
    val shape = RoundedCornerShape(40.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(65.dp)
            .shadow(
                elevation = 20.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.40f))
            .border(1.dp, Color.White.copy(alpha = 0.70f), shape),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockIcon(
                icon = Icons.Outlined.Dashboard,
                isActive = true,
                contentDescription = "Dashboard",
                onClick = { }
            )
            DockIcon(
                icon = Icons.Outlined.Notifications,
                isActive = false,
                contentDescription = "Alerts",
                onClick = { onNavigate("alerts") }
            )
            DockIcon(
                icon = Icons.Outlined.Settings,
                isActive = false,
                contentDescription = "Settings",
                onClick = { onNavigate("settings") }
            )
        }
    }
}

@Composable
private fun DockIcon(
    icon: ImageVector,
    isActive: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) SagePrimary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) Color.White else SagePrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

data class DashboardFeature(
    val title: String,
    val icon: ImageVector,
    val route: String
)

private val dashboardFeatures = listOf(
    DashboardFeature("Goods Receiving", Icons.Outlined.LocalShipping, "receiving"),
    DashboardFeature("Dispensing", Icons.Outlined.ShoppingCart, "dispensing"),
    DashboardFeature("Inventory", Icons.Outlined.Inventory, "inventory"),
    DashboardFeature("Products", Icons.Outlined.MedicalServices, "products"),
    DashboardFeature("Expiry Alerts", Icons.Outlined.Warning, "alerts"),
    DashboardFeature("Reports", Icons.Outlined.Assessment, "reports"),
    DashboardFeature("Suppliers", Icons.Outlined.People, "suppliers"),
    DashboardFeature("Adjustments", Icons.Outlined.Sync, "adjustments")
)
