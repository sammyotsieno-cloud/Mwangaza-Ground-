package org.SamilliMed.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.SamilliMed.app.ui.theme.OatBackground
import org.SamilliMed.app.ui.theme.SagePrimary

private val Amber = Color(0xFFD39A55)
private val AmberLight = Color(0xFFF0C987)
private val Sage = Color(0xFF64856A)
private val SageLight = Color(0xFF9EB9A1)
private val TextDark = Color(0xFF171714)
private val TileWhite = Color(0xFFF8F5EC).copy(alpha = 0.78f)

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
        ReferenceBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Dashboard",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
                textAlign = TextAlign.Center
            )

            Text(
                text = "SamilliMed Medical Centre",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .wrapContentHeight()
            ) {
                items(dashboardFeatures) { feature ->
                    ReferenceTile(
                        title = feature.title,
                        icon = feature.icon,
                        onClick = { onFeatureClick(feature.route) }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            ReferenceDock(
                modifier = Modifier.widthIn(max = 390.dp),
                onNavigate = onFeatureClick
            )
        }
    }
}

@Composable
private fun ReferenceBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Very soft cream atmospheric lighting.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                        radius = 900f,
                        center = Offset(760f, 300f)
                    )
                )
        )

        // Blurred ambient glow behind the floating spheres.
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-105).dp, y = 250.dp)
                .blur(65.dp)
                .background(SageLight.copy(alpha = 0.35f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .offset(x = 110.dp, y = 170.dp)
                .blur(70.dp)
                .background(AmberLight.copy(alpha = 0.25f), CircleShape)
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Green glass spheres.
            drawSphere(
                center = Offset(size.width * 0.17f, size.height * 0.48f),
                radius = size.minDimension * 0.085f,
                base = Sage
            )
            drawSphere(
                center = Offset(size.width * 0.79f, size.height * 0.18f),
                radius = size.minDimension * 0.065f,
                base = Sage
            )

            // Amber glass spheres.
            drawSphere(
                center = Offset(size.width * 0.22f, size.height * 0.25f),
                radius = size.minDimension * 0.045f,
                base = Amber
            )
            drawSphere(
                center = Offset(size.width * 0.89f, size.height * 0.42f),
                radius = size.minDimension * 0.055f,
                base = Amber
            )

            // Translucent water-like sweep beneath the dashboard.
            val y = size.height * 0.88f
            val ribbon = Path().apply {
                moveTo(size.width * 0.27f, y)
                cubicTo(
                    size.width * 0.38f, y - 42f,
                    size.width * 0.44f, y + 28f,
                    size.width * 0.56f, y - 8f
                )
                cubicTo(
                    size.width * 0.67f, y - 42f,
                    size.width * 0.73f, y + 26f,
                    size.width * 0.75f, y - 2f
                )
                cubicTo(
                    size.width * 0.64f, y + 42f,
                    size.width * 0.48f, y + 38f,
                    size.width * 0.27f, y
                )
                close()
            }
            drawPath(
                path = ribbon,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.70f),
                        Color(0xFFD6D0BC).copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.55f)
                    )
                )
            )

            val lowerRibbon = Path().apply {
                moveTo(size.width * 0.34f, y + 20f)
                cubicTo(
                    size.width * 0.47f, y - 2f,
                    size.width * 0.55f, y + 55f,
                    size.width * 0.68f, y + 10f
                )
                cubicTo(
                    size.width * 0.59f, y + 62f,
                    size.width * 0.45f, y + 65f,
                    size.width * 0.34f, y + 20f
                )
                close()
            }
            drawPath(
                path = lowerRibbon,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        Amber.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.12f)
                    )
                )
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSphere(
    center: Offset,
    radius: Float,
    base: Color
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.92f),
                base.copy(alpha = 0.92f),
                base.copy(alpha = 0.78f),
                Color.Black.copy(alpha = 0.16f)
            ),
            center = Offset(center.x - radius * 0.30f, center.y - radius * 0.34f),
            radius = radius * 1.35f
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.38f),
        radius = radius * 0.18f,
        center = Offset(center.x - radius * 0.30f, center.y - radius * 0.38f)
    )
}

@Composable
private fun ReferenceTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = Sage.copy(alpha = 0.16f),
                spotColor = Amber.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(TileWhite)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        AmberLight.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.48f)
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = SagePrimary,
                modifier = Modifier.size(39.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                color = TextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ReferenceDock(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .height(66.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.13f)
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.78f), shape)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockIcon(Icons.Outlined.Dashboard, true, "Dashboard") {}
            DockIcon(Icons.Outlined.Notifications, false, "Alerts") {
                onNavigate("alerts")
            }
            DockIcon(Icons.Outlined.Settings, false, "Settings") {
                onNavigate("settings")
            }
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
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) SagePrimary.copy(alpha = 0.16f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = SagePrimary,
            modifier = Modifier.size(25.dp)
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
    DashboardFeature("Stock Adjustments", Icons.Outlined.Sync, "adjustments")
)
