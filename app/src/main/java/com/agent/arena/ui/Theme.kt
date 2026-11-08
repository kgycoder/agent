package com.agent.arena.ui

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** 순수 검정 / 포인트 블루 / 블랙-그레이 / 화이트 팔레트. */
object Pal {
    val bg = Color(0xFF000000)
    val surface = Color(0xFF111113)
    val surfaceHi = Color(0xFF1C1C1F)
    val border = Color(0xFF26262A)
    val text = Color(0xFFF5F5F7)
    val sub = Color(0xFF9B9BA3)
    val faint = Color(0xFF55555C)
    val accent = Color(0xFF3B82F6)
    val error = Color(0xFFFF5A5F)
    val ok = Color(0xFF34C759)
    val userBubble = Color(0xFF1C1C1F)
    val code = Color(0xFF0C0C0E)
}

/** 부드럽게 감속하는 곡선(Expo-out 계열). */
val SoftEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * 블러 효과(Android 12+ 에서만 적용, 그 미만은 무시). 값은 draw 단계에서 읽으므로 재구성이 일어나지 않는다.
 * radius 는 px.
 */
fun Modifier.softBlur(radius: () -> Float): Modifier =
    if (Build.VERSION.SDK_INT < 31) this
    else this.graphicsLayer {
        val r = radius()
        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
    }

/** 눌림에 스프링으로 반응하는 클릭. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, pressedScale: Float = 0.92f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

// ------------------------------------------------------------------ 아이콘 (Canvas)

@Composable
fun PlusIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val sw = 2.dp.toPx()
    drawLine(color, Offset(size.width * 0.15f, size.height / 2), Offset(size.width * 0.85f, size.height / 2), sw, StrokeCap.Round)
    drawLine(color, Offset(size.width / 2, size.height * 0.15f), Offset(size.width / 2, size.height * 0.85f), sw, StrokeCap.Round)
}

@Composable
fun SlidersIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val sw = 2.dp.toPx()
    val w = size.width; val h = size.height
    drawLine(color, Offset(w * 0.1f, h * 0.32f), Offset(w * 0.9f, h * 0.32f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.1f, h * 0.68f), Offset(w * 0.9f, h * 0.68f), sw, StrokeCap.Round)
    drawCircle(color, radius = 3.2.dp.toPx(), center = Offset(w * 0.35f, h * 0.32f))
    drawCircle(color, radius = 3.2.dp.toPx(), center = Offset(w * 0.68f, h * 0.68f))
}

@Composable
fun ArrowUpIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val sw = 2.2f.dp.toPx()
    val w = size.width; val h = size.height
    drawLine(color, Offset(w / 2, h * 0.88f), Offset(w / 2, h * 0.14f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.16f, h * 0.46f), Offset(w / 2, h * 0.12f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.84f, h * 0.46f), Offset(w / 2, h * 0.12f), sw, StrokeCap.Round)
}

@Composable
fun StopIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    drawRoundRect(
        color, topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
        size = Size(size.width * 0.64f, size.height * 0.64f), cornerRadius = CornerRadius(3.dp.toPx()),
    )
}

@Composable
fun CheckIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val p = Path().apply {
        moveTo(size.width * 0.2f, size.height * 0.52f)
        lineTo(size.width * 0.42f, size.height * 0.74f)
        lineTo(size.width * 0.8f, size.height * 0.28f)
    }
    drawPath(p, color, style = Stroke(width = 2.2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun ChevronIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val p = Path().apply {
        moveTo(size.width * 0.22f, size.height * 0.38f)
        lineTo(size.width * 0.5f, size.height * 0.66f)
        lineTo(size.width * 0.78f, size.height * 0.38f)
    }
    drawPath(p, color, style = Stroke(width = 2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 네 갈래 반짝임(로고). */
@Composable
fun SparkIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val cx = w / 2; val cy = h / 2
    val p = Path().apply {
        moveTo(cx, 0f)
        quadraticBezierTo(cx, cy, w, cy)
        quadraticBezierTo(cx, cy, cx, h)
        quadraticBezierTo(cx, cy, 0f, cy)
        quadraticBezierTo(cx, cy, cx, 0f)
        close()
    }
    drawPath(p, color)
}
