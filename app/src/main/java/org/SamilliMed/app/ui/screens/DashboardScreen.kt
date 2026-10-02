package org.SamilliMed.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.input.KeyboardType

private const val PREFS_NAME = "samillimed_dashboard"
private const val FACILITY_NAME_KEY = "facility_name"
private const val DEFAULT_FACILITY_NAME = "SamilliMed Medical Centre"

private val WarmOat = Color(0xFFF4F1EA)
private val Sage = Color(0xFF5C7A65)
private val Amber = Color(0xFFD4A373)
private val Charcoal = Color(0xFF1A1A1A)
private val SoftGrey = Color(0xFF555555)

@Composable
fun DashboardScreen(
    onFeatureClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var facilityName by rememberSaveable {
        mutableStateOf(
            preferences.getString(FACILITY_NAME_KEY, DEFAULT_FACILITY_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_FACILITY_NAME
        )
    }
    var editingFacilityName by rememberSaveable { mutableStateOf(false) }
    var draftFacilityName by rememberSaveable { mutableStateOf(facilityName) }

    fun saveFacilityName() {
        val value = draftFacilityName.trim().ifBlank { DEFAULT_FACILITY_NAME }
        facilityName = value
        draftFacilityName = value
        preferences.edit().putString(FACILITY_NAME_KEY, value).apply()
        editingFacilityName = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmOat)
    ) {
        AmbientGlassEnvironment()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp, start = 20.dp, end = 20.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Dashboard",
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Charcoal,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            BasicTextField(
                value = draftFacilityName,
                onValueChange = { draftFacilityName = it },
                readOnly = !editingFacilityName,
                singleLine = false,
                maxLines = 2,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Charcoal,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Sage),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { saveFacilityName() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        draftFacilityName = facilityName
                        editingFacilityName = true
                    }
                    .onFocusChanged { state ->
                        if (!state.isFocused && editingFacilityName) saveFacilityName()
                    }
            )

            Spacer(modifier = Modifier.height(18.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        end = 0.dp,
                        top = 2.dp,
                        bottom = 92.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(dashboardFeatures, key = { it.route }) { feature ->
                        GlassDashboardTile(
                            title = feature.title,
                            icon = feature.icon,
                            onClick = { onFeatureClick(feature.route) }
                        )
                    }
                }
            }
        }

        LiquidGlassDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
            onUser = { onFeatureClick("settings") },
            onCart = { onFeatureClick("receiving") },
            onHeart = { onFeatureClick("dashboard") }
        )
    }
}

@Composable
private fun AmbientGlassEnvironment() {
    val transition = rememberInfiniteTransition(label = "ambient-orbs")
    val driftOne by transition.animateFloat(
        initialValue = -10f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse),
        label = "orb-one"
    )
    val driftTwo by transition.animateFloat(
        initialValue = 10f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
        label = "orb-two"
    )
    val driftThree by transition.animateFloat(
        initialValue = -8f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(11000), RepeatMode.Reverse),
        label = "orb-three"
    )
    val driftFour by transition.animateFloat(
        initialValue = 8f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(8000), RepeatMode.Reverse),
        label = "orb-four"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .offset(x = (-82).dp, y = 170.dp + driftOne.dp)
                .blur(58.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Sage.copy(alpha = 0.20f),
                            Sage.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(210.dp)
                .align(Alignment.TopEnd)
                .offset(x = 74.dp, y = 96.dp + driftTwo.dp)
                .blur(60.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Amber.copy(alpha = 0.18f),
                            Amber.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 74.dp, y = 40.dp + driftThree.dp)
                .blur(52.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Sage.copy(alpha = 0.17f),
                            Sage.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(170.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-48).dp, y = (-95).dp + driftFour.dp)
                .blur(54.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Amber.copy(alpha = 0.16f),
                            Amber.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGlassSphere(
                center = Offset(size.width * 0.14f, size.height * 0.47f),
                radius = size.minDimension * 0.075f,
                base = Sage
            )
            drawGlassSphere(
                center = Offset(size.width * 0.84f, size.height * 0.20f),
                radius = size.minDimension * 0.052f,
                base = Amber
            )
            drawGlassSphere(
                center = Offset(size.width * 0.20f, size.height * 0.25f),
                radius = size.minDimension * 0.040f,
                base = Amber
            )
            drawGlassSphere(
                center = Offset(size.width * 0.91f, size.height * 0.55f),
                radius = size.minDimension * 0.045f,
                base = Sage
            )
        }
    }
}

private fun DrawScope.drawGlassSphere(
    center: Offset,
    radius: Float,
    base: Color
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.88f),
                base.copy(alpha = 0.72f),
                base.copy(alpha = 0.38f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.32f, center.y - radius * 0.38f),
            radius = radius * 1.28f
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.42f),
        radius = radius * 0.17f,
        center = Offset(center.x - radius * 0.30f, center.y - radius * 0.38f)
    )
}

@Composable
private fun GlassDashboardTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        // Warm light spilling from behind the lower edge.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 4.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Amber.copy(alpha = 0.08f),
                            Amber.copy(alpha = 0.42f)
                        )
                    )
                )
                .blur(5.dp)
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 12.dp,
                    shape = shape,
                    ambientColor = Sage.copy(alpha = 0.10f),
                    spotColor = Sage.copy(alpha = 0.10f)
                )
                .clip(shape)
                .background(Color.White.copy(alpha = 0.35f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.60f),
                    shape = shape
                )
                .clickable(onClick = onClick)
        ) {
            // Glossy upper half.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.56f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomStart = 34.dp,
                            bottomEnd = 34.dp
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.60f),
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Amber refraction along the lower edge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Amber.copy(alpha = 0.08f),
                                Amber.copy(alpha = 0.40f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.60f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Sage,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Charcoal,
                        fontSize = 12.sp,
                        lineHeight = 14.4.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassDock(
    modifier: Modifier = Modifier,
    onUser: () -> Unit,
    onCart: () -> Unit,
    onHeart: () -> Unit
) {
    val shape = RoundedCornerShape(
        topStart = 40.dp,
        topEnd = 60.dp,
        bottomEnd = 30.dp,
        bottomStart = 50.dp
    )

    Box(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .height(82.dp)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Sage.copy(alpha = 0.10f),
                spotColor = Amber.copy(alpha = 0.14f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.12f),
                        Amber.copy(alpha = 0.18f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.48f), shape)
            .padding(horizontal = 28.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidDockIcon(Icons.Outlined.Person, "User", onUser)
            LiquidDockIcon(Icons.Outlined.ShoppingCart, "Cart", onCart)
            LiquidDockIcon(Icons.Outlined.Favorite, "Heart", onHeart)
        }
    }
}

@Composable
private fun LiquidDockIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = 7.dp,
                shape = shape,
                ambientColor = Sage.copy(alpha = 0.12f),
                spotColor = Amber.copy(alpha = 0.12f)
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.30f))
            .border(1.dp, Color.White.copy(alpha = 0.58f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Sage,
            modifier = Modifier.size(21.dp)
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
