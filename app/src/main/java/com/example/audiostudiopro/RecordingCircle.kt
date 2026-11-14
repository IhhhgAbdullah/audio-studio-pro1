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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** تحويل الصبغة (Hue) إلى لون، لإعطاء الأشعة تدرجاً قوس قزح. */
private fun hueColor(hueDeg: Float, alpha: Float): Color {
    val h = (hueDeg % 360f) / 60f
    val x = 1f - kotlin.math.abs(h % 2f - 1f)
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(1f, x, 0f)
        1 -> Triple(x, 1f, 0f)
        2 -> Triple(0f, 1f, x)
        3 -> Triple(0f, x, 1f)
        4 -> Triple(x, 0f, 1f)
        else -> Triple(1f, 0f, x)
    }
    return Color(r, g, b, alpha)
}

/**
 * دائرة تسجيل: نواة سوداء، وحولها حلقة نقاط بانحراف RGB،
 * وأشعة تخرج منها وتتغير أطوالها حسب مستوى الصوت.
 */
@Composable
fun RecordingCircle(recorder: Recorder) {
    var level by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            // نضخّم الإشارة لأن كلام الإنسان عادةً أهدأ من الحد الأقصى
            val target = (recorder.level * 3f).coerceIn(0f, 1f)
            level = level * 0.75f + target * 0.25f
            phase += 0.08f
            delay(33)
        }
    }

    Canvas(modifier = Modifier.size(260.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = size.minDimension
        val core = minDim * 0.24f
        val ringR = core * 1.08f
        val amp = level
        val chroma = 2.dp.toPx()
        val count = 120

        // النواة السوداء
        drawCircle(
            color = Color.Black,
            radius = core,
            center = Offset(cx, cy)
        )

        for (i in 0 until count) {
            val a = 2f * PI.toFloat() * i / count
            val ux = cos(a)
            val uy = sin(a)

            // شكل الأشعة: أقوى في جهة معينة مثل الصورة، ويتحرك مع الزمن
            val side = max(0f, cos(a - 0.6f)) // ينحاز لليمين
            val wobble = (sin(a * 3f + phase) + sin(a * 7f - phase * 1.3f)) * 0.5f
            val spike = max(0f, wobble) * side * (0.2f + amp * 0.8f)

            val hue = i * 360f / count
            val lineLen = minDim * (0.02f + spike * 0.32f)

            // نقاط الحلقة الخارجية بانحراف RGB
            val dotX = cx + ux * ringR
            val dotY = cy + uy * ringR
            drawCircle(
                color = Color.Red.copy(alpha = 0.85f),
                radius = 2.2.dp.toPx(),
                center = Offset(dotX - ux * chroma, dotY - uy * chroma)
            )
            drawCircle(
                color = Color.Green.copy(alpha = 0.85f),
                radius = 2.2.dp.toPx(),
                center = Offset(dotX, dotY)
            )
            drawCircle(
                color = Color.Blue.copy(alpha = 0.85f),
                radius = 2.2.dp.toPx(),
                center = Offset(dotX + ux * chroma, dotY + uy * chroma)
            )

            // الشعاع الملوّن
            if (lineLen > minDim * 0.03f) {
                val startX = cx + ux * ringR * 1.05f
                val startY = cy + uy * ringR * 1.05f
                val endX = cx + ux * (ringR * 1.05f + lineLen)
                val endY = cy + uy * (ringR * 1.05f + lineLen)
                drawLine(
                    color = hueColor(hue, 0.9f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
