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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** يحلل الملف المحمّل ويعيد الاقتراح، أو null عند الفشل. */
fun suggestFor(context: Context, uri: Uri, durationSec: Float): Suggestion? {
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
        AutoSuggest.suggest(data, h.channels, h.sampleRate)
    } catch (e: Exception) {
        null
    } finally {
        f.delete()
    }
}

/**
 * زر "اقترح لي" مع عرض الاقتراح كتقدير تجريبي.
 * لا يطبّق شيئاً تلقائياً، ويعرض النتيجة فقط.
 */
@Composable
fun SuggestCard(uri: Uri, durationSec: Float) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Suggestion?>(null) }
    var failed by remember { mutableStateOf(false) }

    GradientButton(
        text = if (loading) "جارٍ التحليل..." else "اقترح لي (تقدير تجريبي)",
        brush = Brush.horizontalGradient(listOf(AccentPurple, AccentCyan)),
        enabled = !loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        loading = true
        failed = false
        scope.launch {
            val s = withContext(Dispatchers.IO) { suggestFor(context, uri, durationSec) }
            loading = false
            result = s
            failed = s == null
        }
    }

    val r = result
    if (r != null) {
        Column {
            Text(
                "السلم المقترح: ${r.scale.label()}",
                color = AccentGold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                "الثقة: ${"%.0f".format(r.confidence * 100)}%",
                color = Color.White,
                fontSize = 13.sp
            )
            Text(
                "المسبقة المقترحة: ${r.presetName ?: "لا يوجد"}",
                color = Color.White,
                fontSize = 13.sp
            )
            Text(r.reason, color = Color(0xFF9FB3C8), fontSize = 12.sp)
            Text(
                "هذا تقدير تجريبي فقط، ولم يُطبّق على الإعدادات. راجعه قبل الاستخدام.",
                color = Color(0xFF9FB3C8),
                fontSize = 11.sp
            )
        }
    } else if (failed) {
        Text("تعذر إنشاء الاقتراح من هذا الملف", color = Color(0xFF9FB3C8), fontSize = 12.sp)
    }
}
