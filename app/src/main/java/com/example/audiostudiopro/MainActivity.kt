package com.example.audiostudiopro

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AudioStudioScreen()
            }
        }
    }
}

@Composable
fun AudioStudioScreen() {
    val context = LocalContext.current
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var durationSec by remember { mutableFloatStateOf(0f) }
    var startSec by remember { mutableFloatStateOf(0f) }
    var endSec by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("اختر ملف صوت للبدء") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            player?.release()
            player = null
            isPlaying = false

            sourceUri = uri
            durationSec = readDurationSec(context, uri)
            startSec = 0f
            endSec = durationSec
            player = createPlayer(context, uri)
            status = if (durationSec > 0f) "تم تحميل الملف" else "تعذر قراءة مدة الملف"
        }
    }

    // مراقبة التشغيل وإيقافه عند الوصول إلى نهاية المقطع
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val p = player
            if (p == null || !p.isPlaying || p.currentPosition >= (endSec * 1000).toInt()) {
                p?.pause()
                isPlaying = false
            }
            delay(50)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Audio Studio Pro", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = { picker.launch(arrayOf("audio/*")) }) {
            Text("اختيار ملف صوت")
        }

        val uri = sourceUri
        if (uri != null && durationSec > 0f) {
            Text("المدة: ${"%.2f".format(durationSec)} ثانية")

            Text("بداية القص: ${"%.1f".format(startSec)} ث")
            Slider(
                value = startSec,
                onValueChange = { startSec = min(it, endSec) },
                valueRange = 0f..max(durationSec, 0.1f)
            )

            Text("نهاية القص: ${"%.1f".format(endSec)} ث")
            Slider(
                value = endSec,
                onValueChange = { endSec = max(it, startSec) },
                valueRange = 0f..max(durationSec, 0.1f)
            )

            Button(onClick = {
                val p = player ?: return@Button
                if (isPlaying) {
                    p.pause()
                    isPlaying = false
                } else {
                    p.seekTo((startSec * 1000).toInt())
                    p.start()
                    isPlaying = true
                }
            }) {
                Text(if (isPlaying) "إيقاف المعاينة" else "تشغيل المعاينة")
            }

            Button(onClick = {
                player?.pause()
                isPlaying = false
                status = trimAudio(context, uri, startSec, endSec)
            }) {
                Text("قص وحفظ")
            }
        }

        Text(status)
    }
}

/** ينشئ مشغّل الصوت للمعاينة، ويعيد null عند الفشل. */
fun createPlayer(context: Context, uri: Uri): MediaPlayer? {
    return try {
        MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
        }
    } catch (e: Exception) {
        null
    }
}

/** يقرأ مدة الملف بالثواني باستخدام MediaMetadataRetriever، ويعيد 0 عند الفشل. */
fun readDurationSec(context: Context, uri: Uri): Float {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        (ms?.toLongOrNull() ?: 0L) / 1000f
    } catch (e: Exception) {
        0f
    } finally {
        retriever.release()
    }
}

/** يحاول القص أولاً كملف WAV، ثم كملف MP3 أو غيره عبر فك الترميز. */
fun trimAudio(context: Context, uri: Uri, startSec: Float, endSec: Float): String {
    if (endSec <= startSec) return "نهاية القص يجب أن تكون بعد البداية"

    val path = WavTrimmer.trim(context, uri, startSec, endSec)
        ?: Mp3Trimmer.trim(context, uri, startSec, endSec)

    return if (path != null) "تم الحفظ: $path" else "فشل القص، تأكد أن الملف صوتي وصالح"
}
