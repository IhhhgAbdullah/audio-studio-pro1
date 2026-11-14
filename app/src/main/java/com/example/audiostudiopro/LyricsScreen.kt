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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

/**
 * شاشة كلمات كاراوكي: النص يتحرك للأعلى بسرعة ثابتة على مدة الصوت.
 * الكلمات تُلصق يدوياً، والتمرير يبدأ عند الضغط على "ابدأ".
 */
@Composable
fun LyricsScreen(durationSec: Float, onClose: () -> Unit) {
    var lyrics by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(true) }

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
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(Color(0x66101C2E))
                    .padding(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { editing = false }, enabled = lyrics.isNotBlank()) {
                    Text("ابدأ الكاراوكي")
                }
                Button(onClick = onClose) { Text("رجوع") }
            }
        } else {
            KaraokeScroll(lyrics = lyrics, durationSec = durationSec)
            Button(onClick = { editing = true }) { Text("تعديل الكلمات") }
        }
    }
}

@Composable
private fun KaraokeScroll(lyrics: String, durationSec: Float) {
    val durationMs = (durationSec * 1000f).toInt().coerceAtLeast(1000)
    val transition = rememberInfiniteTransition(label = "karaoke")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing)
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(Color(0x66101C2E)),
        contentAlignment = Alignment.Center
    ) {
        val textHeightPx = 1000f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .offset(y = (-progress * textHeightPx).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = lyrics,
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
