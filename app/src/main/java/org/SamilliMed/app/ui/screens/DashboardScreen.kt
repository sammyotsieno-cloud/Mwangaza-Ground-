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
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var facility by rememberSaveable {
        mutableStateOf(prefs.getString(FACILITY_NAME_KEY, DEFAULT_FACILITY_NAME)?.takeIf { it.isNotBlank() } ?: DEFAULT_FACILITY_NAME)
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(facility) }

    fun saveName() {
        val value = draft.trim().ifBlank { DEFAULT_FACILITY_NAME }
        facility = value
        draft = value
        prefs.edit().putString(FACILITY_NAME_KEY, value).apply()
        editing = false
    }

    BoxWithConstraints(
        modifier.fillMaxSize().background(OAT)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        val wide = maxWidth > maxHeight * 1.18f || maxWidth >= 600.dp
        val cols = if (wide) 4 else 2
        val rows = 8 / cols
        val side = if (wide) 28.dp else 18.dp
        val gap = if (wide) 18.dp else 12.dp
        val topReserve = if (wide) 112.dp else 124.dp
        val bottomReserve = if (wide) 112.dp else 108.dp
        val widthTile = (maxWidth - side * 2 - gap * (cols - 1)) / cols
        val heightTile = (maxHeight - topReserve - bottomReserve - gap * (rows - 1)) / rows
        val tile = widthTile.coerceAtMost(heightTile).coerceIn(76.dp, 188.dp)
        val gridW = tile * cols + gap * (cols - 1)
        val gridH = tile * rows + gap * (rows - 1)
        val gridTop = topReserve + ((maxHeight - topReserve - bottomReserve - gridH) / 2f).coerceAtLeast(0.dp)

        AmbientBackground(Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 16.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Dashboard",
                fontSize = (tile.value * .16f).coerceIn(23f, 30f).sp,
                lineHeight = (tile.value * .19f).coerceIn(27f, 35f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = CHARCOAL
            )
            BasicTextField(
                value = if (editing) draft else facility,
                onValueChange = { draft = it },
                readOnly = !editing,
                maxLines = 2,
                textStyle = TextStyle(
                    fontSize = (tile.value * .105f).coerceIn(15f, 22f).sp,
                    lineHeight = (tile.value * .125f).coerceIn(18f, 25f).sp,
                    fontWeight = FontWeight.Bold,
                    color = CHARCOAL,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(SAGE),
                keyboardOptions = KeyboardOptions(KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveName() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { draft = facility; editing = true }
                    .onFocusChanged { if (!it.isFocused && editing) saveName() }
            )
        }

        Box(
            Modifier.align(Alignment.TopCenter).padding(top = gridTop).size(gridW, gridH)
        ) {
            features.forEachIndexed { i, feature ->
                val r = i / cols
                val c = i % cols
                GlassTile(
                    Modifier.size(tile).offset(x = (tile + gap) * c, y = (tile + gap) * r),
                    feature.title, feature.icon
                ) { onFeatureClick(feature.route) }
            }
        }

        LiquidSurface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height((tile * .78f).coerceIn(94.dp, 145.dp)).offset(y = (-12).dp)
        )

        GlassDock(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).offset(y = (-24).dp),
            (tile * .25f).coerceIn(38.dp, 52.dp),
            { onFeatureClick("settings") }, { onFeatureClick("receiving") }, { onFeatureClick("dashboard") }
        )
    }
}

@Composable
private fun AmbientBackground(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "ambient")
    val a by t.animateFloat(-9f, 9f, infiniteRepeatable(tween(7600), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(8f, -10f, infiniteRepeatable(tween(9100), RepeatMode.Reverse), label = "b")
    Box(modifier) {
        Box(Modifier.size(210.dp).offset((-74).dp, 180.dp + a.dp).blur(56.dp)
            .background(Brush.radialGradient(listOf(SAGE.copy(.18f), SAGE.copy(.05f), Color.Transparent)), CircleShape))
        Box(Modifier.size(210.dp).align(Alignment.TopEnd).offset(68.dp, 80.dp + b.dp).blur(58.dp)
            .background(Brush.radialGradient(listOf(AMBER.copy(.17f), AMBER.copy(.05f), Color.Transparent)), CircleShape))
        Canvas(Modifier.fillMaxSize()) {
            val m = size.minDimension
            sphere(Offset(size.width*.145f, size.height*.47f), m*.073f, SAGE)
            sphere(Offset(size.width*.80f, size.height*.20f), m*.051f, SAGE)
            sphere(Offset(size.width*.21f, size.height*.24f), m*.040f, AMBER)
            sphere(Offset(size.width*.90f, size.height*.50f), m*.044f, AMBER)
        }
    }
}

private fun DrawScope.sphere(center: Offset, radius: Float, base: Color) {
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(.92f), base.copy(.78f), base.copy(.44f), Color.Transparent),
            Offset(center.x-radius*.30f, center.y-radius*.36f), radius*1.30f
        ), radius, center
    )
    drawCircle(Color.White.copy(.48f), radius*.17f, Offset(center.x-radius*.30f, center.y-radius*.37f))
}

@Composable
private fun GlassTile(modifier: Modifier, title: String, icon: IconKind, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier) {
        Box(Modifier.matchParentSize().offset(y = 4.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color.Transparent, AMBER.copy(.10f), AMBER.copy(.36f)))).blur(5.dp))
        Box(
            Modifier.matchParentSize().shadow(11.dp, shape, ambientColor = SAGE.copy(.10f), spotColor = AMBER.copy(.15f))
                .clip(shape).background(Color.White.copy(.48f)).border(1.dp, Color.White.copy(.72f), shape)
                .clickable(onClick)
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(.58f)
                .clip(RoundedCornerShape(24.dp, 24.dp, 38.dp, 38.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(.68f), Color.White.copy(.20f), Color.Transparent))))
            Box(Modifier.fillMaxWidth().height(34.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, AMBER.copy(.09f), AMBER.copy(.32f))))
            Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(.61f), Alignment.Center) { LineIcon(icon, Modifier.fillMaxSize(.34f)) }
                Box(Modifier.fillMaxWidth().weight(.39f), Alignment.Center) {
                    Text(title, color = CHARCOAL, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LineIcon(kind: IconKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension * .055f
        val s = Stroke(w, StrokeCap.Round, StrokeJoin.Round)
        val x = size.width; val y = size.height
        when (kind) {
            IconKind.Receiving -> {
                drawRoundRect(SAGE, Offset(x*.18f,y*.22f), Size(x*.64f,y*.64f), x*.07f, style=s)
                drawRoundRect(SAGE, Offset(x*.38f,y*.12f), Size(x*.24f,y*.15f), x*.04f, style=s)
                repeat(3) { i -> drawLine(SAGE, Offset(x*.32f,y*(.45f+i*.10f)), Offset(x*.68f,y*(.45f+i*.10f)), w) }
            }
            IconKind.Dispensing -> {
                drawRoundRect(SAGE, Offset(x*.39f,y*.18f), Size(x*.22f,y*.48f), x*.04f, style=s)
                drawLine(SAGE, Offset(x*.50f,y*.66f), Offset(x*.50f,y*.80f), w)
                drawLine(SAGE, Offset(x*.29f,y*.80f), Offset(x*.71f,y*.80f), w)
                drawCircle(SAGE,x*.06f,Offset(x*.35f,y*.84f),style=s); drawCircle(SAGE,x*.06f,Offset(x*.65f,y*.84f),style=s)
            }
            IconKind.Inventory -> {
                drawRoundRect(SAGE, Offset(x*.22f,y*.29f), Size(x*.56f,y*.51f), x*.04f, style=s)
                val p=Path().apply{moveTo(x*.22f,y*.30f);lineTo(x*.50f,y*.12f);lineTo(x*.78f,y*.30f)}
                drawPath(p,SAGE,style=s); drawLine(SAGE,Offset(x*.50f,y*.12f),Offset(x*.50f,y*.30f),w)
            }
            IconKind.Products -> {
                drawRoundRect(SAGE,Offset(x*.24f,y*.46f),Size(x*.30f,y*.34f),x*.03f,style=s)
                drawRoundRect(SAGE,Offset(x*.57f,y*.51f),Size(x*.21f,y*.29f),x*.03f,style=s)
                drawLine(SAGE,Offset(x*.36f,y*.46f),Offset(x*.44f,y*.25f),w); drawLine(SAGE,Offset(x*.44f,y*.25f),Offset(x*.49f,y*.31f),w)
            }
            IconKind.Expiry -> {
                drawCircle(SAGE,x*.31f,Offset(x*.50f,y*.54f),style=s)
                drawLine(SAGE,Offset(x*.50f,y*.54f),Offset(x*.50f,y*.39f),w); drawLine(SAGE,Offset(x*.50f,y*.54f),Offset(x*.61f,y*.61f),w)
                drawLine(SAGE,Offset(x*.20f,y*.20f),Offset(x*.70f,y*.70f),w)
            }
            IconKind.Reports -> {
                val p=Path().apply{moveTo(x*.27f,y*.16f);lineTo(x*.61f,y*.16f);lineTo(x*.76f,y*.31f);lineTo(x*.76f,y*.84f);lineTo(x*.27f,y*.84f);close()}
                drawPath(p,SAGE,style=s); drawLine(SAGE,Offset(x*.61f,y*.16f),Offset(x*.61f,y*.31f),w); drawLine(SAGE,Offset(x*.61f,y*.31f),Offset(x*.76f,y*.31f),w)
                repeat(3){i->drawLine(SAGE,Offset(x*.37f,y*(.45f+i*.10f)),Offset(x*.65f,y*(.45f+i*.10f)),w)}
            }
            IconKind.Suppliers -> {
                val p=Path().apply{moveTo(x*.18f,y*.42f);lineTo(x*.50f,y*.15f);lineTo(x*.82f,y*.42f)}
                drawPath(p,SAGE,style=s); drawLine(SAGE,Offset(x*.27f,y*.40f),Offset(x*.27f,y*.83f),w); drawLine(SAGE,Offset(x*.73f,y*.40f),Offset(x*.73f,y*.83f),w)
                drawLine(SAGE,Offset(x*.27f,y*.83f),Offset(x*.73f,y*.83f),w); drawLine(SAGE,Offset(x*.50f,y*.58f),Offset(x*.50f,y*.83f),w)
            }
            IconKind.Adjustments -> {
                drawCircle(SAGE,x*.28f,Offset(x*.50f,y*.50f),style=s); drawCircle(SAGE,x*.13f,Offset(x*.50f,y*.50f),style=s)
                drawCircle(SAGE,x*.035f,Offset(x*.50f,y*.50f)); drawLine(SAGE,Offset(x*.50f,y*.11f),Offset(x*.50f,y*.89f),w*.65f); drawLine(SAGE,Offset(x*.11f,y*.50f),Offset(x*.89f,y*.50f),w*.65f)
            }
        }
    }
}

@Composable
private fun LiquidSurface(modifier: Modifier) {
    Canvas(modifier) {
        val y=size.height*.52f
        val p=Path().apply{moveTo(-20f,y+12f);cubicTo(size.width*.20f,y-22f,size.width*.35f,y+24f,size.width*.50f,y+2f);cubicTo(size.width*.67f,y-20f,size.width*.78f,y+18f,size.width+20f,y-4f)}
        drawPath(p,Brush.linearGradient(listOf(Color.White.copy(.78f),AMBER.copy(.34f),Color.White.copy(.60f))),style=Stroke(4.2f,cap=StrokeCap.Round))
        val q=Path().apply{moveTo(-30f,y+22f);cubicTo(size.width*.22f,y+8f,size.width*.36f,y+48f,size.width*.54f,y+25f);cubicTo(size.width*.72f,y+6f,size.width*.83f,y+42f,size.width+30f,y+22f)}
        drawPath(q,Color.White.copy(.55f),style=Stroke(2.2f,cap=StrokeCap.Round))
        drawOval(Brush.radialGradient(listOf(AMBER.copy(.24f),Color.Transparent)),Rect(size.width*.38f,y-2f,size.width*.62f,y+30f))
    }
}

@Composable
private fun GlassDock(modifier: Modifier, iconSize: Dp, user: () -> Unit, cart: () -> Unit, heart: () -> Unit) {
    val shape=RoundedCornerShape(40.dp)
    Box(modifier.widthIn(max=330.dp).fillMaxWidth().height(iconSize+28.dp)
        .shadow(13.dp,shape,ambientColor=SAGE.copy(.10f),spotColor=AMBER.copy(.15f))
        .clip(shape).background(Brush.verticalGradient(listOf(Color.White.copy(.24f),Color.White.copy(.12f),AMBER.copy(.16f))))
        .border(1.dp,Color.White.copy(.58f),shape).padding(horizontal=22.dp,vertical=10.dp),
        contentAlignment=Alignment.Center) {
        Row(Modifier.fillMaxWidth(),Arrangement.SpaceEvenly,Alignment.CenterVertically) {
            DockIcon(DockKind.Person,iconSize,user); DockIcon(DockKind.Cart,iconSize,cart); DockIcon(DockKind.Heart,iconSize,heart)
        }
    }
}

@Composable
private fun DockIcon(kind: DockKind, iconSize: Dp, onClick: () -> Unit) {
    val shape=RoundedCornerShape(12.dp)
    Box(Modifier.size(iconSize).shadow(7.dp,shape,ambientColor=SAGE.copy(.12f),spotColor=AMBER.copy(.12f))
        .clip(shape).background(Color.White.copy(.30f)).border(1.dp,Color.White.copy(.60f),shape).clickable(onClick),
        contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize(.58f)) {
            val w=size.minDimension*.085f; val s=Stroke(w,StrokeCap.Round,StrokeJoin.Round)
            when(kind) {
                DockKind.Person -> { drawCircle(SAGE,size.minDimension*.18f,Offset(size.width*.50f,size.height*.27f),style=s); drawArc(SAGE,205f,130f,false,Rect(size.width*.24f,size.height*.43f,size.width*.76f,size.height*.93f),s) }
                DockKind.Cart -> { drawLine(SAGE,Offset(size.width*.18f,size.height*.27f),Offset(size.width*.30f,size.height*.27f),w); val p=Path().apply{moveTo(size.width*.30f,size.height*.27f);lineTo(size.width*.38f,size.height*.68f);lineTo(size.width*.75f,size.height*.68f);lineTo(size.width*.82f,size.height*.40f);lineTo(size.width*.34f,size.height*.40f)}; drawPath(p,SAGE,style=s); drawCircle(SAGE,size.minDimension*.07f,Offset(size.width*.43f,size.height*.83f),style=s); drawCircle(SAGE,size.minDimension*.07f,Offset(size.width*.72f,size.height*.83f),style=s) }
                DockKind.Heart -> { val p=Path().apply{moveTo(size.width*.50f,size.height*.83f);cubicTo(size.width*.12f,size.height*.58f,size.width*.13f,size.height*.24f,size.width*.34f,size.height*.24f);cubicTo(size.width*.45f,size.height*.24f,size.width*.50f,size.height*.34f,size.width*.50f,size.height*.34f);cubicTo(size.width*.50f,size.height*.34f,size.width*.56f,size.height*.24f,size.width*.66f,size.height*.24f);cubicTo(size.width*.88f,size.height*.24f,size.width*.89f,size.height*.58f,size.width*.50f,size.height*.83f)}; drawPath(p,SAGE,style=s) }
            }
        }
    }
}
