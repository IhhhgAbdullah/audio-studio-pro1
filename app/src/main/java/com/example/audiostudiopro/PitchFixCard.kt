package com.example.audiostudiopro

import android.content.Context
import android.net.Uri
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

/** يصحح نغمة الملف المحمّل ويكتبه كـ WAV مؤقت ثم يحفظه في Downloads. */
private fun pitchFixToDownloads(context: Context, uri: Uri, durationSec: Float, scale: ScaleChoice): String? {
    val path = WavTrimmer.trim(context, uri, 0f, durationSec)
        ?: Mp3Trimmer.trim(context, uri, 0f, durationSec)
        ?: return null
    val src = File(path)
    try {
        val h = WavTrimmer.readWavHeader(src) ?: return null
        if (h.bitsPerSample != 16) return null
        val data = ByteArray(h.dataSize)
        RandomAccessFile(src, "r").use { raf ->
            raf.seek(h.dataOffset)
            raf.readFully(data)
        }
        val samples = FloatArray(data.size / 2) { i ->
            ((data[i * 2].toInt() and 0xff) or (data[i * 2 + 1].toInt() shl 8)).toShort().toFloat()
        }
        val fixed = PsolaShift.correctKeepLength(samples, h.channels, h.sampleRate, scale)
        val outBytes = ByteArray(fixed.size * 2)
        for (i in fixed.indices) {
            val v = fixed[i].toInt().coerceIn(-32768, 32767)
            outBytes[i * 2] = (v and 0xff).toByte()
            outBytes[i * 2 + 1] = (v shr 8).toByte()
        }
        val tmp = File(context.cacheDir, "pitchfix.wav")
        java.io.FileOutputStream(tmp).use { o ->
            WavTrimmer.writeWavHeader(o, h.sampleRate, h.channels, 16, outBytes.size)
            o.write(outBytes)
        }
        val name = "AudioStudioPro_PitchFix_${System.currentTimeMillis()}.wav"
        val result = AudioExporter.saveFileToDownloads(context, tmp, name, "audio/wav")
        tmp.delete()
        return result
    } catch (e: Exception) {
        return null
    } finally {
        src.delete()
    }
}

/** زر تجريبي لتصحيح النغمة نحو السلم المختار مع الحفاظ على الطول. */
@Composable
fun PitchFixCard(uri: Uri, durationSec: Float, scale: ScaleChoice) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    GradientButton(
        text = if (busy) "جارٍ التصحيح..." else "تصحيح النغمة (تجريبي)",
        brush = Brush.horizontalGradient(listOf(AccentGold, AccentPink)),
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        busy = true
        result = null
        scope.launch {
            val path = withContext(Dispatchers.IO) { pitchFixToDownloads(context, uri, durationSec, scale) }
            busy = false
            result = if (path != null) {
                "تم الحفظ: $path\nقارن الملف بالأصلي واستمع للفرق."
            } else {
                "فشل التصحيح، تأكد أن الملف WAV صالح"
            }
        }
    }

    result?.let { Text(it, color = Color(0xFFF5E6B8), fontSize = 12.sp) }
}
