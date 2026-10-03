package org.SamilliMed.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

private const val PREFS_NAME = "samillimed_dashboard"
private const val FACILITY_NAME_KEY = "facility_name"
private const val DEFAULT_FACILITY_NAME = "SamilliMed Medical Centre"

// Studio Photo Colors
private val CANVAS_WARM_OAT = Color(0xFFF3EFE6)
private val ICON_SAGE = Color(0xFF3B6647)
private val TEXT_CHARCOAL = Color(0xFF1E231F)
private val AMBER_GLOW = Color(0xFFFFBF5A)
private val AMBER_RIM = Color(0xFFFFD588)

private enum class IconKind {
    Receiving,
    Dispensing,
    Inventory,
    Products,
    Expiry,
    Reports,
    Suppliers,
    Adjustments
}

private enum class DockKind {
    Person,
    Cart,
    Heart
}

private data class Feature(val title: String, val icon: IconKind, val route: String)

private val features = listOf(
    Feature("Goods\nReceiving", IconKind.Receiving, "receiving"),
    Feature("Dispensing", IconKind.Dispensing, "dispensing"),
    Feature("Inventory", IconKind.Inventory, "inventory"),
    Feature("Products", IconKind.Products, "products"),
    Feature("Expiry Alerts", IconKind.Expiry, "alerts"),
    Feature("Reports", IconKind.Reports, "reports"),
    Feature("Suppliers", IconKind.Suppliers, "suppliers"),
    Feature("Stock\nAdjustments", IconKind.Adjustments, "adjustments")
)

@Composable
fun DashboardScreen(onFeatureClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var facility by rememberSaveable {
        mutableStateOf(
            prefs.getString(FACILITY_NAME_KEY, DEFAULT_FACILITY_NAME)
                ?.takeIf { it.isNotBlank() }
                ?.take(48)
                ?: DEFAULT_FACILITY_NAME
        )
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(facility) }

    fun beginEditing() {
        draft = facility
        editing = true
    }

    fun saveName() {
        val value = draft.trim().ifBlank { DEFAULT_FACILITY_NAME }.take(48)
        facility = value
        draft = value
        prefs.edit().putString(FACILITY_NAME_KEY, value).apply()
        editing = false
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CANVAS_WARM_OAT,
                        Color(0xFFEFE9DC),
                        Color(0xFFEAE2D3)
                    )
                )
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            )
    ) {
        val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val compact = maxWidth < 400.dp
        val medium = maxWidth in 400.dp..720.dp
        val expanded = maxWidth > 720.dp
        val landscape = aspect > 1.15f

        // When in widescreen / landscape / tablet: 4 columns x 2 rows (exactly like reference photo).
        // On narrow phones in portrait: 2 columns x 4 rows.
        val columns = when {
            expanded || landscape -> 4
            medium -> 4
            else -> 2
        }
        val rows = (features.size + columns - 1) / columns

        val horizontalInset = when {
            expanded -> 48.dp
            medium -> 28.dp
            else -> 16.dp
        }
        val gridGap = when {
            expanded -> 24.dp
            medium -> 16.dp
            else -> 12.dp
        }

        val headerHeight = when {
            landscape -> 84.dp
            expanded -> 116.dp
            medium -> 108.dp
            else -> 112.dp
        }
        val bottomHeight = when {
            landscape -> 88.dp
            expanded -> 118.dp
            medium -> 106.dp
            else -> 102.dp
        }

        val availableGridWidth = (maxWidth - horizontalInset * 2 - gridGap * (columns - 1)).coerceAtLeast(1.dp)
        val availableGridHeight = (maxHeight - headerHeight - bottomHeight - gridGap * (rows - 1)).coerceAtLeast(1.dp)

        val tileFromWidth = availableGridWidth / columns
        val tileFromHeight = availableGridHeight / rows

        val tile = tileFromWidth
            .coerceAtMost(tileFromHeight)
            .coerceAtMost(if (expanded) 190.dp else 170.dp)
            .coerceAtLeast(if (compact) 68.dp else 74.dp)

        val gridWidth = tile * columns + gridGap * (columns - 1)
        val gridHeight = tile * rows + gridGap * (rows - 1)

        val freeGridSpace = (maxHeight - headerHeight - bottomHeight - gridHeight).coerceAtLeast(0.dp)
        val gridTop = headerHeight + freeGridSpace / 2f

        // 1. Photographic 3D Spheres & Atmospheric lighting
        StudioAtmosphere(
            modifier = Modifier.fillMaxSize(),
            aspect = aspect
        )

        // 2. Clean Centered Header with Editable Facility Name
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(headerHeight)
                .padding(
                    top = if (landscape) 8.dp else 14.dp,
                    start = horizontalInset,
                    end = horizontalInset
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Dashboard",
                fontSize = (tile.value * 0.18f).coerceIn(24f, 32f).sp,
                lineHeight = (tile.value * 0.22f).coerceIn(28f, 38f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = TEXT_CHARCOAL
            )

            Spacer(Modifier.height(if (landscape) 2.dp else 4.dp))

            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .heightIn(min = 34.dp, max = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (editing) Color.White.copy(alpha = 0.70f)
                        else Color.Transparent
                    )
                    .border(
                        width = if (editing) 1.dp else 0.dp,
                        color = if (editing) ICON_SAGE.copy(alpha = 0.50f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(onClick = ::beginEditing)
                    .semantics {
                        contentDescription =
                            if (editing) "Edit facility name" else "Facility name: $facility. Tap to edit"
                        role = Role.Button
                    }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = if (editing) draft else facility,
                    onValueChange = { draft = it.take(48) },
                    readOnly = !editing,
                    enabled = true,
                    maxLines = 1,
                    textStyle = TextStyle(
                        fontSize = (tile.value * 0.14f).coerceIn(16f, 24f).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        color = TEXT_CHARCOAL,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(ICON_SAGE),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { saveName() }),
                    modifier = Modifier.onFocusChanged { state ->
                        if (!state.isFocused && editing) saveName()
                    }
                )
            }
        }

        // 3. Center 8 Frosted Glass Squircles with Warm Amber Rim Glow
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = gridTop)
                .size(gridWidth, gridHeight)
        ) {
            features.forEachIndexed { index, feature ->
                val row = index / columns
                val column = index % columns

                StudioGlassTile(
                    modifier = Modifier
                        .size(tile)
                        .offset(
                            x = (tile + gridGap) * column,
                            y = (tile + gridGap) * row
                        ),
                    title = feature.title,
                    icon = feature.icon,
                    onClick = { onFeatureClick(feature.route) }
                )
            }
        }

        // 4. Bottom Sculpted Molten-Glass Pedestal with Honey Caustics & Floating Dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomHeight)
        ) {
            MoltenGlassPedestal(
                modifier = Modifier.fillMaxSize()
            )

            // Translucent Floating Dock Capsule
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-4).dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.85f),
                                Color(0xFFFFE0A0).copy(alpha = 0.40f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy((tile * 0.22f).coerceIn(24.dp, 44.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockIcon(DockKind.Person, (tile * 0.24f).coerceIn(28.dp, 40.dp)) { onFeatureClick("settings") }
                DockIcon(DockKind.Cart, (tile * 0.24f).coerceIn(28.dp, 40.dp)) { onFeatureClick("receiving") }
                DockIcon(DockKind.Heart, (tile * 0.24f).coerceIn(28.dp, 40.dp)) { onFeatureClick("dashboard") }
            }
        }
    }
}

/**
 * 3D Photographed Studio Atmosphere with 4 Hero Spheres:
 * 1. Top-left Amber/Caramel Orb
 * 2. Mid-left Jade/Sage Orb
 * 3. Top-right Jade/Sage Orb
 * 4. Mid-right Amber/Caramel Orb
 */
@Composable
private fun StudioAtmosphere(
    modifier: Modifier,
    aspect: Float
) {
    val transition = rememberInfiniteTransition(label = "studioDrift")

    val driftA by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftA"
    )
    val driftB by transition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftB"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle studio floor bounce lighting
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f),
                    Color(0xFFE2D6C0).copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.52f),
                radius = w * 0.58f
            ),
            topLeft = Offset(w * 0.10f, h * 0.22f),
            size = Size(w * 0.80f, h * 0.65f)
        )

        // --- 1. Top-Left Amber Orb ---
        val orb1X = if (aspect > 1.2f) w * 0.21f else w * 0.16f
        val orb1Y = h * 0.25f + driftA
        val orb1R = (size.minDimension * 0.052f).coerceIn(26f, 44f)
        draw3DAmberSphere(Offset(orb1X, orb1Y), orb1R)

        // --- 2. Mid-Left Jade Orb (Large Hero) ---
        val orb2X = if (aspect > 1.2f) w * 0.15f else w * 0.08f
        val orb2Y = h * 0.53f + driftB
        val orb2R = (size.minDimension * 0.082f).coerceIn(40f, 68f)
        draw3DJadeSphere(Offset(orb2X, orb2Y), orb2R)

        // --- 3. Top-Right Jade Orb ---
        val orb3X = if (aspect > 1.2f) w * 0.80f else w * 0.88f
        val orb3Y = h * 0.20f - driftA
        val orb3R = (size.minDimension * 0.076f).coerceIn(38f, 62f)
        draw3DJadeSphere(Offset(orb3X, orb3Y), orb3R)

        // --- 4. Mid-Right Amber Orb ---
        val orb4X = if (aspect > 1.2f) w * 0.87f else w * 0.92f
        val orb4Y = h * 0.42f - driftB
        val orb4R = (size.minDimension * 0.066f).coerceIn(32f, 54f)
        draw3DAmberSphere(Offset(orb4X, orb4Y), orb4R)
    }
}

/**
 * Photorealistic 3D Jade Green Ceramic / Glass Sphere
 */
private fun DrawScope.draw3DJadeSphere(center: Offset, radius: Float) {
    // 1. Soft ambient drop shadow underneath
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF2C3E32).copy(alpha = 0.28f),
                Color(0xFF2C3E32).copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = Offset(center.x, center.y + radius * 1.08f),
            radius = radius * 0.95f
        ),
        topLeft = Offset(center.x - radius * 0.90f, center.y + radius * 0.72f),
        size = Size(radius * 1.80f, radius * 0.65f)
    )

    // 2. Primary 3D Sphere Shading (Keylight from upper-left)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF8BB79B), // Top highlight
                Color(0xFF6B997D), // Main body
                Color(0xFF4C755D), // Shadow transition
                Color(0xFF2B4736)  // Deep shadow edge
            ),
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.32f),
            radius = radius * 1.15f
        ),
        radius = radius,
        center = center
    )

    // 3. Opposite Subtle Rim Bounce (Bottom-Right)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xFF98C4A9).copy(alpha = 0.26f)
            ),
            center = Offset(center.x + radius * 0.40f, center.y + radius * 0.40f),
            radius = radius
        ),
        radius = radius,
        center = center
    )

    // 4. Crisp White Primary Specular Glint
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.60f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.34f, center.y - radius * 0.38f),
            radius = radius * 0.28f
        ),
        radius = radius * 0.28f,
        center = Offset(center.x - radius * 0.34f, center.y - radius * 0.38f)
    )

    // 5. Pin-point specular spot
    drawCircle(
        color = Color.White,
        radius = radius * 0.055f,
        center = Offset(center.x - radius * 0.36f, center.y - radius * 0.40f)
    )
}

/**
 * Photorealistic 3D Amber / Caramel Glass Sphere
 */
private fun DrawScope.draw3DAmberSphere(center: Offset, radius: Float) {
    // 1. Soft ambient drop shadow underneath
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF5A3B18).copy(alpha = 0.26f),
                Color(0xFF5A3B18).copy(alpha = 0.07f),
                Color.Transparent
            ),
            center = Offset(center.x, center.y + radius * 1.05f),
            radius = radius * 0.90f
        ),
        topLeft = Offset(center.x - radius * 0.85f, center.y + radius * 0.70f),
        size = Size(radius * 1.70f, radius * 0.60f)
    )

    // 2. Primary 3D Sphere Shading
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD59E), // Golden warm core
                Color(0xFFE59C45), // Rich amber body
                Color(0xFFB86B1E), // Deep caramel shade
                Color(0xFF6B3A0A)  // Dark occlusion rim
            ),
            center = Offset(center.x - radius * 0.30f, center.y - radius * 0.32f),
            radius = radius * 1.15f
        ),
        radius = radius,
        center = center
    )

    // 3. Opposite Golden Rim Reflection (Bottom-Right)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xFFFFD699).copy(alpha = 0.35f)
            ),
            center = Offset(center.x + radius * 0.38f, center.y + radius * 0.38f),
            radius = radius
        ),
        radius = radius,
        center = center
    )

    // 4. Crisp White Primary Specular Glint
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.55f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.34f, center.y - radius * 0.36f),
            radius = radius * 0.26f
        ),
        radius = radius * 0.26f,
        center = Offset(center.x - radius * 0.34f, center.y - radius * 0.36f)
    )

    // 5. Pin-point specular spot
    drawCircle(
        color = Color.White,
        radius = radius * 0.055f,
        center = Offset(center.x - radius * 0.35f, center.y - radius * 0.38f)
    )
}

/**
 * Translucent Frosted Glass Squircle Card with the Signature Warm Golden Rim Glow
 */
@Composable
private fun StudioGlassTile(
    modifier: Modifier,
    title: String,
    icon: IconKind,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .semantics {
                contentDescription = title.replace("\n", " ")
                role = Role.Button
            }
            .clickable(onClick = onClick)
    ) {
        // --- A. Amber Rim Glow Aura on Top-Right Bevel ---
        Canvas(modifier = Modifier.matchParentSize()) {
            // Intense warm golden back-light flare along the upper-right corner
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AMBER_GLOW.copy(alpha = 0.65f),
                        AMBER_RIM.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.12f),
                    radius = size.width * 0.45f
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.88f, size.height * 0.12f)
            )

            // Secondary subtle flare on top-left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AMBER_RIM.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.12f),
                    radius = size.width * 0.30f
                ),
                radius = size.width * 0.30f,
                center = Offset(size.width * 0.15f, size.height * 0.12f)
            )
        }

        // --- B. Soft Ambient Occlusion Shadow ---
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 5.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = shape,
                    ambientColor = Color(0xFF85725E).copy(alpha = 0.20f),
                    spotColor = Color(0xFF6B5844).copy(alpha = 0.18f)
                )
        )

        // --- C. Frosted Translucent Glass Card Body ---
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFCFAF6).copy(alpha = 0.88f),
                            Color(0xFFF7F2E8).copy(alpha = 0.82f),
                            Color(0xFFEFE8DB).copy(alpha = 0.85f)
                        )
                    )
                )
                // Dual-tone border: top-right catches the golden studio rim; left and bottom remain frosted white
                .border(
                    width = 1.3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            AMBER_RIM.copy(alpha = 0.90f),
                            AMBER_GLOW.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.70f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = shape
                )
        ) {
            // Subtle top specular edge highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.50f),
                                Color.White.copy(alpha = 0.95f),
                                AMBER_RIM.copy(alpha = 0.80f)
                            )
                        )
                    )
            )

            // Content Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon (Clean Green Line Art matching Photo)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.58f),
                    contentAlignment = Alignment.Center
                ) {
                    StudioLineIcon(
                        kind = icon,
                        modifier = Modifier.fillMaxSize(0.44f)
                    )
                }

                // Label Text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.42f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = TEXT_CHARCOAL,
                        fontSize = (if (title.length > 14) 11.8f else 12.5f).sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Exact Icon Line Art from Reference Photo
 */
@Composable
private fun StudioLineIcon(
    kind: IconKind,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = Stroke(
            width = (size.minDimension * 0.065f).coerceIn(2.2f, 4.2f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (kind) {
            // 1. Goods Receiving: Clipboard with top clip & 3 checklist lines
            IconKind.Receiving -> {
                // Board body
                drawRoundRect(
                    color = ICON_SAGE,
                    topLeft = Offset(w * 0.22f, h * 0.24f),
                    size = Size(w * 0.56f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = s
                )
                // Top clip
                val clipPath = Path().apply {
                    moveTo(w * 0.36f, h * 0.24f)
                    lineTo(w * 0.36f, h * 0.16f)
                    arcTo(
                        rect = Rect(w * 0.36f, h * 0.12f, w * 0.64f, h * 0.24f),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    lineTo(w * 0.64f, h * 0.24f)
                }
                drawPath(clipPath, ICON_SAGE, style = s)
                // 3 Checklist horizontal lines
                drawLine(ICON_SAGE, Offset(w * 0.34f, h * 0.42f), Offset(w * 0.66f, h * 0.42f), s.width)
                drawLine(ICON_SAGE, Offset(w * 0.34f, h * 0.56f), Offset(w * 0.66f, h * 0.56f), s.width)
                drawLine(ICON_SAGE, Offset(w * 0.34f, h * 0.70f), Offset(w * 0.54f, h * 0.70f), s.width)
            }

            // 2. Dispensing: Hospital Utility Trolley / Cart with wheels
            IconKind.Dispensing -> {
                // Rear vertical handle
                val handlePath = Path().apply {
                    moveTo(w * 0.24f, h * 0.22f)
                    lineTo(w * 0.30f, h * 0.22f)
                    lineTo(w * 0.32f, h * 0.68f)
                    lineTo(w * 0.76f, h * 0.68f)
                }
                drawPath(handlePath, ICON_SAGE, style = s)

                // Upper tray box
                drawRoundRect(
                    color = ICON_SAGE,
                    topLeft = Offset(w * 0.32f, h * 0.36f),
                    size = Size(w * 0.40f, h * 0.28f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                    style = s
                )

                // Bottom 2 Wheels
                drawCircle(ICON_SAGE, radius = w * 0.075f, center = Offset(w * 0.40f, h * 0.78f), style = s)
                drawCircle(ICON_SAGE, radius = w * 0.075f, center = Offset(w * 0.68f, h * 0.78f), style = s)
            }

            // 3. Inventory: Cardboard Box with Top Flaps
            IconKind.Inventory -> {
                // Front rectangular face
                drawRoundRect(
                    color = ICON_SAGE,
                    topLeft = Offset(w * 0.20f, h * 0.42f),
                    size = Size(w * 0.60f, h * 0.44f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = s
                )
                // Top open V-notch flaps
                val flapPath = Path().apply {
                    moveTo(w * 0.20f, h * 0.42f)
                    lineTo(w * 0.38f, h * 0.24f)
                    lineTo(w * 0.50f, h * 0.38f)
                    lineTo(w * 0.62f, h * 0.24f)
                    lineTo(w * 0.80f, h * 0.42f)
                }
                drawPath(flapPath, ICON_SAGE, style = s)

                // Center tape seam
                drawLine(ICON_SAGE, Offset(w * 0.50f, h * 0.42f), Offset(w * 0.50f, h * 0.62f), s.width)
            }

            // 4. Products: Medical Bottle & Carton
            IconKind.Products -> {
                // Medicine Bottle (left)
                drawRoundRect(
                    color = ICON_SAGE,
                    topLeft = Offset(w * 0.20f, h * 0.46f),
                    size = Size(w * 0.30f, h * 0.40f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = s
                )
                // Bottle cap
                drawRoundRect(
                    color = ICON_SAGE,
                    topLeft = Offset(w * 0.26f, h * 0.36f),
                    size = Size(w * 0.18f, h * 0.10f),
                    cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
                    style = s
                )

                // Medicine Carton (right)
                drawRoundRect(
                    color = ICON_SAGE,
                    topLeft = Offset(w * 0.54f, h * 0.54f),
                    size = Size(w * 0.28f, h * 0.32f),
                    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
                    style = s
                )

                // Angled dropper / pill above carton
                val pillPath = Path().apply {
                    moveTo(w * 0.62f, h * 0.38f)
                    lineTo(w * 0.74f, h * 0.26f)
                }
                drawPath(pillPath, ICON_SAGE, style = s)
            }

            // 5. Expiry Alerts: Clock Dial / Gauge with Accent Tick
            IconKind.Expiry -> {
                // Outer circle
                drawCircle(ICON_SAGE, radius = w * 0.31f, center = Offset(w * 0.50f, h * 0.52f), style = s)

                // Clock hands (12:00 and 02:10)
                drawLine(ICON_SAGE, Offset(w * 0.50f, h * 0.52f), Offset(w * 0.50f, h * 0.32f), s.width)
                drawLine(ICON_SAGE, Offset(w * 0.50f, h * 0.52f), Offset(w * 0.67f, h * 0.56f), s.width)

                // Accent tick mark outside at 10 o'clock
                drawLine(ICON_SAGE, Offset(w * 0.26f, h * 0.28f), Offset(w * 0.20f, h * 0.22f), s.width)
            }

            // 6. Reports: Document Checklist Sheet
            IconKind.Reports -> {
                // Folded document body
                val docPath = Path().apply {
                    moveTo(w * 0.24f, h * 0.84f)
                    lineTo(w * 0.24f, h * 0.18f)
                    lineTo(w * 0.60f, h * 0.18f)
                    lineTo(w * 0.76f, h * 0.34f)
                    lineTo(w * 0.76f, h * 0.84f)
                    close()
                }
                drawPath(docPath, ICON_SAGE, style = s)

                // Dog-ear corner fold
                val foldPath = Path().apply {
                    moveTo(w * 0.60f, h * 0.18f)
                    lineTo(w * 0.60f, h * 0.34f)
                    lineTo(w * 0.76f, h * 0.34f)
                }
                drawPath(foldPath, ICON_SAGE, style = s)

                // 3 checklist rows (dot + line)
                drawCircle(ICON_SAGE, radius = s.width * 0.8f, center = Offset(w * 0.35f, h * 0.46f))
                drawLine(ICON_SAGE, Offset(w * 0.43f, h * 0.46f), Offset(w * 0.66f, h * 0.46f), s.width)

                drawCircle(ICON_SAGE, radius = s.width * 0.8f, center = Offset(w * 0.35f, h * 0.60f))
                drawLine(ICON_SAGE, Offset(w * 0.43f, h * 0.60f), Offset(w * 0.66f, h * 0.60f), s.width)

                drawCircle(ICON_SAGE, radius = s.width * 0.8f, center = Offset(w * 0.35f, h * 0.74f))
                drawLine(ICON_SAGE, Offset(w * 0.43f, h * 0.74f), Offset(w * 0.58f, h * 0.74f), s.width)
            }

            // 7. Suppliers: Warehouse / Facility Gable Building
            IconKind.Suppliers -> {
                // Pitched Gable Roof
                val roofPath = Path().apply {
                    moveTo(w * 0.50f, h * 0.22f)
                    lineTo(w * 0.18f, h * 0.48f)
                    moveTo(w * 0.50f, h * 0.22f)
                    lineTo(w * 0.82f, h * 0.48f)
                }
                drawPath(roofPath, ICON_SAGE, style = s)

                // Exterior walls & floor
                val housePath = Path().apply {
                    moveTo(w * 0.26f, h * 0.48f)
                    lineTo(w * 0.26f, h * 0.82f)
                    lineTo(w * 0.74f, h * 0.82f)
                    lineTo(w * 0.74f, h * 0.48f)
                }
                drawPath(housePath, ICON_SAGE, style = s)

                // Center Open Arch / Doorway
                val doorPath = Path().apply {
                    moveTo(w * 0.42f, h * 0.82f)
                    lineTo(w * 0.42f, h * 0.60f)
                    arcTo(
                        rect = Rect(w * 0.42f, h * 0.52f, w * 0.58f, h * 0.68f),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    lineTo(w * 0.58f, h * 0.82f)
                }
                drawPath(doorPath, ICON_SAGE, style = s)
            }

            // 8. Stock Adjustments: Concentric Target / Calibration Dial
            IconKind.Adjustments -> {
                // Outer ring
                drawCircle(ICON_SAGE, radius = w * 0.32f, center = Offset(w * 0.50f, h * 0.50f), style = s)
                // Middle ring
                drawCircle(ICON_SAGE, radius = w * 0.21f, center = Offset(w * 0.50f, h * 0.50f), style = s)
                // Center bullseye solid dot
                drawCircle(ICON_SAGE, radius = s.width * 1.1f, center = Offset(w * 0.50f, h * 0.50f))

                // Calibration notch at top
                drawLine(ICON_SAGE, Offset(w * 0.50f, h * 0.12f), Offset(w * 0.50f, h * 0.18f), s.width)
            }
        }
    }
}

/**
 * Organic Molten-Glass Pedestal with Honey Caustics Pooling in Center Trough
 */
@Composable
private fun MoltenGlassPedestal(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Organic molten-glass ribbon contour
        val startY = h * 0.48f

        // 1. Amber / Honey Liquid Caustics Pooling in Trough
        val causticPath = Path().apply {
            moveTo(w * 0.25f, startY + 18f)
            cubicTo(
                w * 0.38f, startY + 36f,
                w * 0.58f, startY + 36f,
                w * 0.75f, startY + 18f
            )
            cubicTo(
                w * 0.68f, startY + 44f,
                w * 0.35f, startY + 44f,
                w * 0.25f, startY + 18f
            )
            close()
        }
        drawPath(
            path = causticPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE59C38).copy(alpha = 0.75f),
                    Color(0xFFD47C1E).copy(alpha = 0.45f),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, startY + 28f),
                radius = w * 0.28f
            )
        )

        // 2. Sculpted Clear Glass Ribbon
        val glassWave = Path().apply {
            moveTo(w * 0.18f, startY + 16f)
            cubicTo(
                w * 0.26f, startY - 24f,
                w * 0.36f, startY + 4f,
                w * 0.50f, startY + 22f
            )
            cubicTo(
                w * 0.64f, startY + 40f,
                w * 0.74f, startY - 18f,
                w * 0.82f, startY + 16f
            )
            cubicTo(
                w * 0.75f, startY + 34f,
                w * 0.55f, startY + 38f,
                w * 0.40f, startY + 32f
            )
            cubicTo(
                w * 0.28f, startY + 26f,
                w * 0.22f, startY + 24f,
                w * 0.18f, startY + 16f
            )
            close()
        }

        drawPath(
            path = glassWave,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color(0xFFFFDFA8).copy(alpha = 0.35f),
                    Color.White.copy(alpha = 0.65f),
                    Color(0xFFE29B35).copy(alpha = 0.40f),
                    Color.White.copy(alpha = 0.50f)
                ),
                start = Offset(w * 0.20f, startY),
                end = Offset(w * 0.80f, startY + 35f)
            )
        )

        // 3. Crisp Glass Edge Specular Highlight
        val glassEdge = Path().apply {
            moveTo(w * 0.18f, startY + 16f)
            cubicTo(
                w * 0.26f, startY - 24f,
                w * 0.36f, startY + 4f,
                w * 0.50f, startY + 22f
            )
            cubicTo(
                w * 0.64f, startY + 40f,
                w * 0.74f, startY - 18f,
                w * 0.82f, startY + 16f
            )
        }

        drawPath(
            path = glassEdge,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.90f),
                    Color(0xFFFFE0A0).copy(alpha = 0.80f),
                    Color.White.copy(alpha = 0.95f)
                )
            ),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Clean Line Icons for the Bottom Floating Dock
 */
@Composable
private fun DockIcon(
    kind: DockKind,
    iconSize: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(iconSize + 10.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = when (kind) {
                    DockKind.Person -> "Settings"
                    DockKind.Cart -> "Goods receiving"
                    DockKind.Heart -> "Dashboard"
                }
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(iconSize * 0.72f)) {
            val stroke = (size.minDimension * 0.075f).coerceIn(2.0f, 3.4f)
            val s = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

            when (kind) {
                DockKind.Person -> {
                    // Head circle
                    drawCircle(ICON_SAGE, size.minDimension * 0.19f, Offset(size.width * 0.50f, size.height * 0.28f), style = s)
                    // Shoulder curve
                    drawArc(
                        color = ICON_SAGE,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.22f, size.height * 0.44f),
                        size = Size(size.width * 0.56f, size.height * 0.50f),
                        style = s
                    )
                }
                DockKind.Cart -> {
                    // Handle and bed
                    drawLine(ICON_SAGE, Offset(size.width * 0.16f, size.height * 0.28f), Offset(size.width * 0.28f, size.height * 0.28f), stroke)
                    val path = Path().apply {
                        moveTo(size.width * 0.28f, size.height * 0.28f)
                        lineTo(size.width * 0.36f, size.height * 0.68f)
                        lineTo(size.width * 0.76f, size.height * 0.68f)
                        lineTo(size.width * 0.82f, size.height * 0.42f)
                        lineTo(size.width * 0.32f, size.height * 0.42f)
                    }
                    drawPath(path, ICON_SAGE, style = s)
                    // Wheels
                    drawCircle(ICON_SAGE, size.minDimension * 0.08f, Offset(size.width * 0.42f, size.height * 0.82f), style = s)
                    drawCircle(ICON_SAGE, size.minDimension * 0.08f, Offset(size.width * 0.72f, size.height * 0.82f), style = s)
                }
                DockKind.Heart -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.50f, size.height * 0.83f)
                        cubicTo(size.width * 0.12f, size.height * 0.58f, size.width * 0.14f, size.height * 0.24f, size.width * 0.34f, size.height * 0.24f)
                        cubicTo(size.width * 0.45f, size.height * 0.24f, size.width * 0.50f, size.height * 0.34f, size.width * 0.50f, size.height * 0.34f)
                        cubicTo(size.width * 0.50f, size.height * 0.34f, size.width * 0.55f, size.height * 0.24f, size.width * 0.66f, size.height * 0.24f)
                        cubicTo(size.width * 0.86f, size.height * 0.24f, size.width * 0.88f, size.height * 0.58f, size.width * 0.50f, size.height * 0.83f)
                    }
                    drawPath(path, ICON_SAGE, style = s)
                }
            }
        }
    }
}
