package com.example.audiostudiopro

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * شاشة كاراوكي: تُلصق الكلمات، ثم تتحرك للأعلى بسرعة يحددها المستخدم.
 * المدة الافتراضية للتمرير = مدة الصوت، ويمكن تعديلها بالمؤشر.
 */
@Composable
fun LyricsScreen(durationSec: Float, onClose: () -> Unit) {
    var lyrics by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(true) }
    var scrollSec by remember { mutableFloatStateOf(durationSec.coerceAtLeast(10f)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (editing) {
            Text("الصق كلمات الأغنية هنا", color = AccentGold, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = lyrics,
                onValueChange = { lyrics = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x66101C2E))
                    .padding(12.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { editing = false }, enabled = lyrics.isNotBlank()) {
                    Text("ابدأ الكاراوكي")
                }
                Button(onClick = onClose) { Text("رجوع") }
            }
        } else {
            Text("مدة التمرير: ${"%.0f".format(scrollSec)} ثانية", color = Color.White)
            Slider(
                value = scrollSec,
                onValueChange = { scrollSec = it },
                valueRange = 10f..300f
            )
            KaraokeScroll(lyrics = lyrics, scrollSec = scrollSec)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { editing = true }) { Text("تعديل الكلمات") }
                Button(onClick = onClose) { Text("رجوع") }
            }
        }
    }
}

@Composable
private fun KaraokeScroll(lyrics: String, scrollSec: Float) {
    val durationMs = (scrollSec * 1000f).toInt().coerceAtLeast(10000)
    val transition = rememberInfiniteTransition(label = "karaoke")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing)
        ),
        label = "progress"
    )

    var viewportPx by remember { mutableIntStateOf(0) }
    var textPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x66101C2E))
            .onSizeChanged { viewportPx = it.height },
        contentAlignment = Alignment.TopCenter
    ) {
        // المسافة الكلية: من أسفل الشاشة حتى يخرج آخر سطر من الأعلى
        val travel = (textPx + viewportPx).coerceAtLeast(1)
        val shiftPx = viewportPx - progress * travel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .onSizeChanged { textPx = it.height }
                .offset(y = density.run { shiftPx.toDp() })
        ) {
            Text(
                text = lyrics,
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
