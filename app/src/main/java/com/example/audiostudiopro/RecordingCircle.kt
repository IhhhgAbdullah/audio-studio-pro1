package com.example.audiostudiopro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** دائرة تعرض موجات صوتية تتحرك حسب مستوى الصوت أثناء التسجيل. */
@Composable
fun RecordingCircle(recorder: Recorder) {
    var level by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            level = level * 0.7f + recorder.level * 0.3f
            phase += 0.12f
            delay(33)
        }
    }

    val colors = listOf(Color(0xFFD4AF37), Color(0xFF3A86FF), Color(0xFFFF006E))

    Canvas(modifier = Modifier.size(220.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = size.minDimension * 0.22f
        val amp = (level * 4f).coerceIn(0.05f, 1f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3A86FF), Color(0xFF0D1B2A)),
                center = Offset(cx, cy),
                radius = baseR
            ),
            radius = baseR,
            center = Offset(cx, cy)
        )

        for (k in 0 until 3) {
            val path = Path()
            val steps = 120
            for (s in 0..steps) {
                val a = 2f * PI.toFloat() * s / steps
                val wave = sin(a * (5 + k) + phase * (1f + k * 0.5f)) * amp * baseR * 0.35f
                val r = baseR * 1.15f + k * baseR * 0.12f + wave
                val x = cx + cos(a) * r
                val y = cy + sin(a) * r
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(
                path = path,
                color = colors[k].copy(alpha = 0.9f - k * 0.2f),
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}
