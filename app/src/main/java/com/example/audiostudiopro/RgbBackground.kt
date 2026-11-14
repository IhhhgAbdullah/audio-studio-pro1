package com.example.audiostudiopro

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/**
 * خلفية متحركة بتدرجات RGB ناعمة تتحرك ببطء.
 * الألوان داكنة حتى تبقى النصوص واضحة فوقها.
 */
@Composable
fun RgbBackground(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "rgb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRgbBlobs(phase)
        }
        content()
    }
}

private fun DrawScope.drawRgbBlobs(phase: Float) {
    drawRect(color = Color(0xFF0D1B2A))

    val t = phase * 2f * Math.PI.toFloat()
    val w = size.width
    val h = size.height
    val radius = maxOf(w, h) * 0.8f

    val blobs = listOf(
        Triple(Color(0x663A86FF), 0f, 0.3f),
        Triple(Color(0x66FF006E), 2.1f, 0.6f),
        Triple(Color(0x66D4AF37), 4.2f, 0.9f)
    )

    for ((color, offset, speed) in blobs) {
        val a = t * speed + offset
        val cx = w * (0.5f + 0.35f * cos(a))
        val cy = h * (0.5f + 0.35f * sin(a * 0.7f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = Offset(cx, cy),
                radius = radius
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
    }
}
