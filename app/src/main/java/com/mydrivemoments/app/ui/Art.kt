package com.mydrivemoments.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydrivemoments.app.data.PlaceKind

object C {
    val bg = Color(0xFF0C1218)
    val panel = Color(0xFF151E27)
    val panel2 = Color(0xFF1C2733)
    val line = Color(0xFF26323F)
    val ink = Color(0xFFEAF0F6)
    val muted = Color(0xFF8D9BAB)
    val accent = Color(0xFFF0A04B)
    val accentInk = Color(0xFF1A1207)
    val route = Color(0xFF5DB2E6)
    val stamp = Color(0xFFE25A4D)
    val paper = Color(0xFFFBFBF8)
    val paperInk = Color(0xFF2A2620)
    val paperMuted = Color(0xFF7A7468)
    val ok = Color(0xFF9BE3BC)
    val okBg = Color(0xFF13301F)
}

private val palettes = mapOf(
    PlaceKind.RIVER to listOf(0xFFF4C38E, 0xFFA9CFE3, 0xFF3C6E8F, 0xFF2E4A39),
    PlaceKind.CAFE to listOf(0xFFF1B27A, 0xFFEBD4B6, 0xFF8A5A3C, 0xFF3B2A20),
    PlaceKind.MOUNTAIN to listOf(0xFFEBAA6C, 0xFFCFDCC4, 0xFFB4552E, 0xFF4C3A2A),
    PlaceKind.MART to listOf(0xFF33435C, 0xFF566C8C, 0xFFE6AE48, 0xFF1E2939),
    PlaceKind.CITY to listOf(0xFF7D90B1, 0xFFBAC6D9, 0xFFE0A24A, 0xFF2A3346),
)

/** Illustration for a drive card, drawn from the place category (no photo needed in the car). */
@Composable
fun PlaceScene(kind: PlaceKind, variant: Int, modifier: Modifier = Modifier) {
    val p = palettes.getValue(kind).map { Color(it) }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun x(v: Float) = v / 100f * w
        fun y(v: Float) = v / 100f * h
        drawRect(Brush.verticalGradient(listOf(p[0], p[1])))
        drawCircle(Color(0xD9FFF3DC), radius = x(9f), center = Offset(x(26f + variant * 24f), y(28f + (variant % 2) * 8f)))
        when (kind) {
            PlaceKind.RIVER -> {
                drawPath(path(w, h, listOf(0f to 60f, 18f to 40f, 30f to 58f, 46f to 36f, 62f to 59f, 100f to 50f, 100f to 62f, 0f to 62f)), p[3])
                drawPath(path(w, h, listOf(0f to 62f, 30f to 57f, 55f to 60f, 100f to 57f, 100f to 100f, 0f to 100f)), p[2])
                drawLine(Color(0x99E7F2F8), Offset(0f, y(70f)), Offset(w, y(70f)), strokeWidth = 2f)
                drawCircle(Color(0xFFF7F1E6), x(2.2f), Offset(x(70f - variant * 10), y(75f)))
            }
            PlaceKind.MOUNTAIN -> {
                drawPath(path(w, h, listOf(0f to 70f, 25f to 38f, 45f to 60f, 66f to 30f, 100f to 66f, 100f to 100f, 0f to 100f)), p[2])
                drawPath(path(w, h, listOf(0f to 82f, 50f to 72f, 100f to 84f, 100f to 100f, 0f to 100f)), p[3])
            }
            PlaceKind.CAFE -> {
                drawRect(p[3], Offset(0f, y(66f)), androidx.compose.ui.geometry.Size(w, y(34f)))
                drawRect(p[2], Offset(x(38f), y(44f)), androidx.compose.ui.geometry.Size(x(24f), y(24f)))
                drawCircle(p[2], x(6f), Offset(x(64f), y(56f)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = x(3f)))
            }
            PlaceKind.MART, PlaceKind.CITY -> {
                drawRect(p[3], Offset(x(8f), y(46f)), androidx.compose.ui.geometry.Size(x(20f), y(54f)))
                drawRect(p[3], Offset(x(32f), y(34f)), androidx.compose.ui.geometry.Size(x(18f), y(66f)))
                drawRect(p[3], Offset(x(54f), y(52f)), androidx.compose.ui.geometry.Size(x(40f), y(48f)))
                drawRect(p[2], Offset(x(60f), y(60f)), androidx.compose.ui.geometry.Size(x(28f), y(6f)))
            }
        }
    }
}

private fun DrawScope.path(w: Float, h: Float, pts: List<Pair<Float, Float>>) = Path().apply {
    pts.forEachIndexed { i, (px, py) ->
        val ox = px / 100f * w; val oy = py / 100f * h
        if (i == 0) moveTo(ox, oy) else lineTo(ox, oy)
    }
    close()
}

/** Red rubber-stamp circle: only ever used for routine stamps. */
@Composable
fun RoutineStamp(text: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .rotate(-12f)
            .border(2.dp, C.stamp, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = C.stamp, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.24f).sp, textAlign = TextAlign.Center, lineHeight = (size.value * 0.26f).sp)
    }
}

/** Small polaroid: white frame + scene, used in the calendar and collection. */
@Composable
fun MiniPolaroid(kind: PlaceKind, variant: Int, modifier: Modifier = Modifier, tilt: Float = 6f) {
    Column(
        modifier
            .rotate(tilt)
            .shadow(3.dp)
            .background(C.paper)
            .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 5.dp)
    ) {
        PlaceScene(kind, variant, Modifier.fillMaxWidth().aspectRatio(1f))
    }
}
