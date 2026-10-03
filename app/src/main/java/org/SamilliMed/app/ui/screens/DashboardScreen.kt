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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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

private val OAT = Color(0xFFF4F1EA)
private val SAGE = Color(0xFF5C7A65)
private val AMBER = Color(0xFFD4A373)
private val CHARCOAL = Color(0xFF1A1A1A)

private enum class IconKind { Receiving, Dispensing, Inventory, Products, Expiry, Reports, Suppliers, Adjustments }
private enum class DockKind { Person, Cart, Heart }
private data class Feature(val title: String, val icon: IconKind, val route: String)

private val features = listOf(
    Feature("Goods Receiving", IconKind.Receiving, "receiving"),
    Feature("Dispensing", IconKind.Dispensing, "dispensing"),
    Feature("Inventory", IconKind.Inventory, "inventory"),
    Feature("Products", IconKind.Products, "products"),
    Feature("Expiry Alerts", IconKind.Expiry, "alerts"),
    Feature("Reports", IconKind.Reports, "reports"),
    Feature("Suppliers", IconKind.Suppliers, "suppliers"),
    Feature("Stock Adjustments", IconKind.Adjustments, "adjustments")
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
            .background(OAT)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            )
    ) {
        /*
         * The dashboard is divided into three visual regions rather than using
         * fixed "topReserve/bottomReserve" offsets. This keeps the artwork tied
         * to the available window on phones, tablets and landscape windows.
         */
        val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val compact = maxWidth < 390.dp
        val medium = maxWidth >= 390.dp && maxWidth < 700.dp
        val expanded = maxWidth >= 700.dp
        val landscape = aspect > 1.18f

        val columns = when {
            expanded || (medium && landscape) -> 4
            medium -> 3
            else -> 2
        }
        val rows = (features.size + columns - 1) / columns

        val horizontalInset = when {
            expanded -> 32.dp
            medium -> 24.dp
            else -> 16.dp
        }
        val gridGap = when {
            expanded -> 20.dp
            medium -> 14.dp
            else -> 10.dp
        }

        // Reserve actual visual regions for header and bottom artwork.
        val headerHeight = when {
            landscape -> 96.dp
            expanded -> 126.dp
            medium -> 122.dp
            else -> 126.dp
        }
        val bottomHeight = when {
            landscape -> 92.dp
            expanded -> 126.dp
            medium -> 116.dp
            else -> 112.dp
        }

        val availableGridWidth =
            (maxWidth - horizontalInset * 2 - gridGap * (columns - 1))
                .coerceAtLeast(1.dp)

        val availableGridHeight =
            (maxHeight - headerHeight - bottomHeight - gridGap * (rows - 1))
                .coerceAtLeast(1.dp)

        val tileFromWidth = availableGridWidth / columns
        val tileFromHeight = availableGridHeight / rows

        // Never force a minimum tile that can overflow a short window.
        val tile = tileFromWidth
            .coerceAtMost(tileFromHeight)
            .coerceAtMost(if (expanded) 196.dp else 188.dp)
            .coerceAtLeast(if (compact) 62.dp else 68.dp)

        val gridWidth = tile * columns + gridGap * (columns - 1)
        val gridHeight = tile * rows + gridGap * (rows - 1)

        val freeGridSpace =
            (maxHeight - headerHeight - bottomHeight - gridHeight)
                .coerceAtLeast(0.dp)

        val gridTop = headerHeight + freeGridSpace / 2f

        // On constrained devices the artwork is simplified without changing
        // its composition. The reference remains the visual target.
        val visualQuality = when {
            maxWidth < 340.dp || maxHeight < 520.dp -> 0
            maxWidth < 400.dp -> 1
            else -> 2
        }

        AmbientBackground(
            modifier = Modifier.fillMaxSize(),
            quality = visualQuality
        )

        // Fixed-height header envelope prevents a long editable facility name
        // from pushing the feature field around.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(headerHeight)
                .padding(
                    top = if (landscape) 10.dp else 16.dp,
                    start = horizontalInset,
                    end = horizontalInset
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Dashboard",
                fontSize = (tile.value * 0.155f).coerceIn(22f, 30f).sp,
                lineHeight = (tile.value * 0.185f).coerceIn(26f, 34f).sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp,
                color = CHARCOAL
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp, max = 54.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (editing) SAGE.copy(alpha = 0.07f)
                        else Color.Transparent
                    )
                    .border(
                        width = if (editing) 1.dp else 0.dp,
                        color = if (editing) SAGE.copy(alpha = 0.30f)
                        else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = ::beginEditing)
                    .semantics {
                        contentDescription =
                            if (editing) "Edit facility name" else "Facility name. Tap to edit"
                        role = Role.Button
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    BasicTextField(
                        value = if (editing) draft else facility,
                        onValueChange = { draft = it.take(48) },
                        readOnly = !editing,
                        enabled = true,
                        maxLines = 2,
                        textStyle = TextStyle(
                            fontSize = (tile.value * 0.098f).coerceIn(14f, 21f).sp,
                            lineHeight = (tile.value * 0.118f).coerceIn(17f, 24f).sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.15.sp,
                            color = CHARCOAL,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(SAGE),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { saveName() }),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                if (!state.isFocused && editing) saveName()
                            }
                    )

                    if (!editing) {
                        Spacer(Modifier.width(5.dp))
                        Canvas(Modifier.size(13.dp).alpha(0.52f)) {
                            val sw = size.minDimension * 0.12f
                            drawLine(
                                SAGE,
                                Offset(size.width * 0.20f, size.height * 0.75f),
                                Offset(size.width * 0.70f, size.height * 0.25f),
                                sw,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                SAGE,
                                Offset(size.width * 0.66f, size.height * 0.25f),
                                Offset(size.width * 0.79f, size.height * 0.38f),
                                sw,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                SAGE,
                                Offset(size.width * 0.16f, size.height * 0.79f),
                                Offset(size.width * 0.32f, size.height * 0.79f),
                                sw,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        // Proper adaptive grid: every tile is derived from the actual window,
        // not from hard-coded x/y positions.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = gridTop)
                .size(gridWidth, gridHeight)
        ) {
            features.forEachIndexed { index, feature ->
                val row = index / columns
                val column = index % columns

                GlassTile(
                    modifier = Modifier
                        .size(tile)
                        .offset(
                            x = (tile + gridGap) * column,
                            y = (tile + gridGap) * row
                        ),
                    title = feature.title,
                    icon = feature.icon,
                    onClick = { onFeatureClick(feature.route) },
                    variant = index % 3,
                    quality = visualQuality
                )
            }
        }

        // Bottom artwork is one composition. The dock is positioned inside
        // the same visual region as the liquid surface instead of using two
        // unrelated bottom offsets.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomHeight)
        ) {
            LiquidSurface(
                modifier = Modifier
                    .fillMaxSize(),
                quality = visualQuality
            )

            GlassDock(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        horizontal = horizontalInset.coerceAtLeast(18.dp),
                        bottom = if (landscape) 10.dp else 12.dp
                    ),
                iconSize = (tile * 0.255f)
                    .coerceIn(34.dp, 54.dp),
                user = { onFeatureClick("settings") },
                cart = { onFeatureClick("receiving") },
                heart = { onFeatureClick("dashboard") }
            )
        }
    }
}

@Composable
private fun AmbientBackground(
    modifier: Modifier,
    quality: Int
) {
    val transition = rememberInfiniteTransition(label = "ambient")

    val driftA by transition.animateFloat(
        initialValue = -11f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(
            animation = tween(8400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientA"
    )
    val driftB by transition.animateFloat(
        initialValue = 10f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(9800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientB"
    )
    val driftC by transition.animateFloat(
        initialValue = -7f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(11200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientC"
    )

    Box(modifier) {
        // The large washes establish the reference's soft, photographed
        // atmosphere behind the foreground glass objects.
        Box(
            Modifier
                .size(250.dp)
                .offset((-85).dp, 185.dp + driftA.dp)
                .blur(if (quality >= 1) 68.dp else 42.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            SAGE.copy(alpha = if (quality >= 2) 0.24f else 0.18f),
                            SAGE.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Box(
            Modifier
                .size(230.dp)
                .align(Alignment.TopEnd)
                .offset(65.dp, 65.dp + driftB.dp)
                .blur(if (quality >= 1) 72.dp else 44.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            AMBER.copy(alpha = if (quality >= 2) 0.22f else 0.16f),
                            AMBER.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        if (quality >= 1) {
            Box(
                Modifier
                    .size(190.dp)
                    .align(Alignment.BottomEnd)
                    .offset((-25).dp, (-35).dp + driftC.dp)
                    .blur(if (quality >= 2) 56.dp else 38.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AMBER.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val m = size.minDimension

            glassSphere(
                Offset(size.width * 0.125f, size.height * 0.455f),
                m * 0.082f,
                SAGE,
                quality
            )
            glassSphere(
                Offset(size.width * 0.825f, size.height * 0.175f),
                m * 0.065f,
                SAGE,
                quality
            )
            glassSphere(
                Offset(size.width * 0.185f, size.height * 0.215f),
                m * 0.048f,
                AMBER,
                quality
            )
            glassSphere(
                Offset(size.width * 0.915f, size.height * 0.475f),
                m * 0.055f,
                AMBER,
                quality
            )

            if (quality >= 1) {
                glassSphere(
                    Offset(size.width * 0.715f, size.height * 0.625f),
                    m * 0.030f,
                    AMBER.copy(alpha = 0.88f),
                    quality
                )
                glassSphere(
                    Offset(size.width * 0.08f, size.height * 0.68f),
                    m * 0.026f,
                    SAGE.copy(alpha = 0.75f),
                    quality
                )
            }
        }
    }
}

@Composable
private fun GlassTile(
    modifier: Modifier,
    title: String,
    icon: IconKind,
    onClick: () -> Unit,
    variant: Int,
    quality: Int
) {
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier
            .semantics {
                contentDescription = title
                role = Role.Button
            }
    ) {
        // A warm contact/reflection layer makes the tile read as a physical
        // translucent object resting above the background.
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 5.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            AMBER.copy(alpha = 0.10f + variant * 0.015f),
                            AMBER.copy(alpha = 0.34f + variant * 0.025f)
                        )
                    )
                )
                .blur(if (quality >= 1) 8.dp else 4.dp)
        )

        Box(
            Modifier
                .matchParentSize()
                .shadow(
                    elevation = if (quality >= 2) 15.dp else 10.dp,
                    shape = shape,
                    ambientColor = SAGE.copy(alpha = 0.13f),
                    spotColor = AMBER.copy(alpha = 0.20f)
                )
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.58f),
                            Color.White.copy(alpha = 0.46f),
                            Color.White.copy(alpha = 0.40f)
                        )
                    )
                )
                .border(
                    width = if (quality >= 2) 1.2.dp else 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.96f),
                            Color.White.copy(alpha = 0.62f),
                            AMBER.copy(alpha = 0.34f),
                            Color.White.copy(alpha = 0.72f)
                        )
                    ),
                    shape = shape
                )
                .clickable(onClick = onClick)
        ) {
            // Broad upper reflection — the most important visual cue that
            // separates these from ordinary Material cards.
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.56f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp,
                            bottomStart = 38.dp,
                            bottomEnd = 38.dp
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.78f),
                                Color.White.copy(alpha = 0.26f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Warm reflected light along the lower interior.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                AMBER.copy(alpha = 0.11f),
                                AMBER.copy(alpha = 0.30f)
                            )
                        )
                    )
            )

            // A thin bright top edge gives the tile the photographed,
            // polished-ceramic highlight visible in the reference.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.52f),
                                Color.White.copy(alpha = 0.96f),
                                Color.White.copy(alpha = 0.48f)
                            )
                        )
                    )
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.60f),
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(
                        kind = icon,
                        modifier = Modifier.fillMaxSize(0.37f)
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.40f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = CHARCOAL,
                        fontSize = (tileTextSize(title)).sp,
                        lineHeight = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun tileTextSize(title: String): Float =
    when {
        title.length >= 17 -> 11.2f
        title.length >= 14 -> 11.6f
        else -> 12f
    }

@Composable
private fun LiquidSurface(
    modifier: Modifier,
    quality: Int
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // The surface occupies the bottom visual region. Its upper contour
        // deliberately remains irregular rather than becoming a flat bar.
        val baseY = h * 0.43f

        val body = Path().apply {
            moveTo(-35f, baseY + 28f)
            cubicTo(
                w * 0.10f, baseY - 20f,
                w * 0.26f, baseY + 38f,
                w * 0.40f, baseY + 5f
            )
            cubicTo(
                w * 0.54f, baseY - 18f,
                w * 0.68f, baseY + 32f,
                w * 0.82f, baseY + 1f
            )
            cubicTo(
                w * 0.93f, baseY - 12f,
                w * 1.04f, baseY + 24f,
                w + 45f, baseY + 15f
            )
            lineTo(w + 45f, h + 25f)
            lineTo(-35f, h + 25f)
            close()
        }

        drawPath(
            body,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.62f),
                    AMBER.copy(alpha = 0.23f),
                    AMBER.copy(alpha = 0.39f),
                    Color.White.copy(alpha = 0.15f)
                )
            )
        )

        if (quality >= 1) {
            drawOval(
                brush = Brush.radialGradient(
                    listOf(
                        AMBER.copy(alpha = 0.27f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, baseY + 20f),
                    radius = w * 0.44f
                ),
                topLeft = Offset(w * 0.16f, baseY - 6f),
                size = Size(w * 0.68f, 52f)
            )
        }

        val edge = Path().apply {
            moveTo(-25f, baseY + 8f)
            cubicTo(
                w * 0.16f, baseY - 28f,
                w * 0.30f, baseY + 24f,
                w * 0.46f, baseY - 4f
            )
            cubicTo(
                w * 0.62f, baseY - 24f,
                w * 0.76f, baseY + 18f,
                w * 0.92f, baseY - 8f
            )
            cubicTo(
                w * 1.02f, baseY + 6f,
                w + 35f, baseY + 2f,
                w + 35f, baseY + 2f
            )
        }

        drawPath(
            edge,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.88f),
                    AMBER.copy(alpha = 0.48f),
                    Color.White.copy(alpha = 0.72f)
                )
            ),
            style = Stroke(
                width = if (quality >= 2) 5f else 3.5f,
                cap = StrokeCap.Round
            )
        )

        val highlight = Path().apply {
            moveTo(-18f, baseY + 16f)
            cubicTo(
                w * 0.18f, baseY,
                w * 0.36f, baseY + 34f,
                w * 0.54f, baseY + 12f
            )
            cubicTo(
                w * 0.72f, baseY - 4f,
                w * 0.86f, baseY + 26f,
                w + 22f, baseY + 10f
            )
        }

        drawPath(
            highlight,
            Color.White.copy(alpha = 0.58f),
            style = Stroke(
                width = if (quality >= 2) 2.1f else 1.5f,
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
private fun GlassDock(
    modifier: Modifier,
    iconSize: Dp,
    user: () -> Unit,
    cart: () -> Unit,
    heart: () -> Unit
) {
    val shape = RoundedCornerShape(42.dp)

    Box(
        modifier
            .widthIn(max = 350.dp)
            .fillMaxWidth()
            .height(iconSize + 32.dp)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = SAGE.copy(alpha = 0.13f),
                spotColor = AMBER.copy(alpha = 0.20f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.18f),
                        AMBER.copy(alpha = 0.20f)
                    )
                )
            )
            .border(
                width = 1.15.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.82f),
                        Color.White.copy(alpha = 0.42f),
                        AMBER.copy(alpha = 0.32f)
                    )
                ),
                shape = shape
            )
            .padding(horizontal = 26.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockIcon(DockKind.Person, iconSize, user)
            DockIcon(DockKind.Cart, iconSize, cart)
            DockIcon(DockKind.Heart, iconSize, heart)
        }
    }
}

@Composable
private fun DockIcon(
    kind: DockKind,
    iconSize: Dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        Modifier
            .size(iconSize)
            .shadow(
                elevation = 9.dp,
                shape = shape,
                ambientColor = SAGE.copy(alpha = 0.15f),
                spotColor = AMBER.copy(alpha = 0.15f)
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.42f))
            .border(1.05.dp, Color.White.copy(alpha = 0.70f), shape)
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
        Canvas(Modifier.fillMaxSize(0.58f)) {
            val stroke = size.minDimension * 0.085f
            val s = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            when (kind) {
                DockKind.Person -> {
                    drawCircle(
                        SAGE,
                        size.minDimension * 0.18f,
                        Offset(size.width * 0.50f, size.height * 0.27f),
                        style = s
                    )
                    drawArc(
                        SAGE,
                        205f,
                        130f,
                        false,
                        Offset(size.width * 0.24f, size.height * 0.43f),
                        Size(size.width * 0.52f, size.height * 0.50f),
                        style = s
                    )
                }

                DockKind.Cart -> {
                    drawLine(
                        SAGE,
                        Offset(size.width * 0.18f, size.height * 0.27f),
                        Offset(size.width * 0.30f, size.height * 0.27f),
                        stroke
                    )
                    val path = Path().apply {
                        moveTo(size.width * 0.30f, size.height * 0.27f)
                        lineTo(size.width * 0.38f, size.height * 0.68f)
                        lineTo(size.width * 0.75f, size.height * 0.68f)
                        lineTo(size.width * 0.82f, size.height * 0.40f)
                        lineTo(size.width * 0.34f, size.height * 0.40f)
                    }
                    drawPath(path, SAGE, style = s)
                    drawCircle(
                        SAGE,
                        size.minDimension * 0.07f,
                        Offset(size.width * 0.43f, size.height * 0.83f),
                        style = s
                    )
                    drawCircle(
                        SAGE,
                        size.minDimension * 0.07f,
                        Offset(size.width * 0.72f, size.height * 0.83f),
                        style = s
                    )
                }

                DockKind.Heart -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.50f, size.height * 0.83f)
                        cubicTo(
                            size.width * 0.12f, size.height * 0.58f,
                            size.width * 0.13f, size.height * 0.24f,
                            size.width * 0.34f, size.height * 0.24f
                        )
                        cubicTo(
                            size.width * 0.45f, size.height * 0.24f,
                            size.width * 0.50f, size.height * 0.34f,
                            size.width * 0.50f, size.height * 0.34f
                        )
                        cubicTo(
                            size.width * 0.50f, size.height * 0.34f,
                            size.width * 0.56f, size.height * 0.24f,
                            size.width * 0.66f, size.height * 0.24f
                        )
                        cubicTo(
                            size.width * 0.88f, size.height * 0.24f,
                            size.width * 0.89f, size.height * 0.58f,
                            size.width * 0.50f, size.height * 0.83f
                        )
                    }
                    drawPath(path, SAGE, style = s)
                }
            }
        }
    }
}
