package com.example.audiostudiopro

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** يقرأ الملف كعينات PCM 16-bit (القناة الأولى)، ويعيد (البيانات، القنوات، معدل العينة). */
private fun readPcm(context: Context, uri: Uri, durationSec: Float): Triple<ByteArray, Int, Int>? {
    val path = WavTrimmer.trim(context, uri, 0f, durationSec)
        ?: Mp3Trimmer.trim(context, uri, 0f, durationSec)
        ?: return null
    val f = File(path)
    return try {
        val h = WavTrimmer.readWavHeader(f) ?: return null
        if (h.bitsPerSample != 16) return null
        val data = ByteArray(h.dataSize)
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(h.dataOffset)
            raf.readFully(data)
        }
        Triple(data, h.channels, h.sampleRate)
    } catch (e: Exception) {
        null
    } finally {
        f.delete()
    }
}

/** يعرض نتيجة PitchDetector الحالية ونتيجة YIN جنباً إلى جنب للمقارنة. */
@Composable
fun YinCompareCard(uri: Uri, durationSec: Float) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    GradientButton(
        text = if (loading) "جارٍ المقارنة..." else "مقارنة YIN",
        brush = Brush.horizontalGradient(listOf(AccentCyan, AccentPurple)),
        enabled = !loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        loading = true
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                val pcm = readPcm(context, uri, durationSec) ?: return@withContext null
                val (data, ch, sr) = pcm
                val old = PitchDetector.analyze(data, ch, sr)
                val oldText = if (old.isEmpty()) {
                    "الحالي: لم تُكشف نغمة"
                } else {
                    val top = old.groupingBy { it.noteName }.eachCount().maxByOrNull { it.value }!!.key
                    "الحالي: النوتة $top، متوسط ${"%.1f".format(old.map { it.frequencyHz }.average())} Hz"
                }
                oldText + "\n" + YinPitch.compareSummary(data, ch, sr)
            }
            loading = false
            result = text ?: "تعذر تحليل الملف، جرّب WAV"
        }
    }

    result?.let { Text(it, color = Color(0xFFF5E6B8), fontSize = 12.sp) }
}
