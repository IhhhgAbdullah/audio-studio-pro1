package com.example.audiostudiopro

import android.Manifest
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0D1B2A),
                    surface = Color(0xFF0D1B2A),
                    primary = Color(0xFFD4AF37),
                    onPrimary = Color(0xFF0D1B2A),
                    onBackground = Color(0xFFF5E6B8),
                    onSurface = Color(0xFFF5E6B8)
                )
            ) {
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
    var fadeInSec by remember { mutableFloatStateOf(0f) }
    var fadeOutSec by remember { mutableFloatStateOf(0f) }
    var echoDelaySec by remember { mutableFloatStateOf(0.3f) }
    var echoFeedback by remember { mutableFloatStateOf(0.4f) }
    var echoMix by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("اختر ملف صوت أو سجّل للبدء") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    val recorder = remember { Recorder(context) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            if (recorder.isRecording) recorder.stop()
        }
    }

    fun loadSource(uri: Uri) {
        player?.release()
        player = null
        isPlaying = false

        sourceUri = uri
        durationSec = readDurationSec(context, uri)
        startSec = 0f
        endSec = durationSec
        fadeInSec = 0f
        fadeOutSec = 0f
        echoMix = 0f
        player = createPlayer(context, uri)
        status = if (durationSec > 0f) "تم تحميل الملف" else "تعذر قراءة مدة الملف"
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) loadSource(uri)
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (recorder.start()) {
                isRecording = true
                status = "جارٍ التسجيل..."
            } else {
                status = "تعذر بدء التسجيل"
            }
        } else {
            status = "يلزم إذن الميكروفون للتسجيل"
        }
    }

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Audio Studio Pro", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = { picker.launch(arrayOf("audio/*")) },
            enabled = !isRecording
        ) {
            Text("اختيار ملف صوت")
        }

        Button(onClick = {
            if (isRecording) {
                val file = recorder.stop()
                isRecording = false
                if (file != null) {
                    loadSource(Uri.fromFile(file))
                    status = "تم إيقاف التسجيل وتحميله"
                } else {
                    status = "فشل حفظ التسجيل"
                }
            } else {
                if (player != null) player?.release()
                player = null
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }) {
            Text(if (isRecording) "إيقاف التسجيل" else "تسجيل من الميكروفون")
        }

        val uri = sourceUri
        if (uri != null && durationSec > 0f && !isRecording) {
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

            val maxFade = max(0.1f, min(5f, (endSec - startSec) / 2f))

            Text("تدرج الدخول (Fade In): ${"%.1f".format(fadeInSec)} ث")
            Slider(
                value = fadeInSec,
                onValueChange = { fadeInSec = it },
                valueRange = 0f..maxFade
            )

            Text("تدرج الخروج (Fade Out): ${"%.1f".format(fadeOutSec)} ث")
            Slider(
                value = fadeOutSec,
                onValueChange = { fadeOutSec = it },
                valueRange = 0f..maxFade
            )

            Text("زمن الصدى (Echo Delay): ${"%.2f".format(echoDelaySec)} ث")
            Slider(
                value = echoDelaySec,
                onValueChange = { echoDelaySec = it },
                valueRange = 0.05f..1f
            )

            Text("تكرار الصدى (Feedback): ${"%.0f".format(echoFeedback * 100)}%")
            Slider(
                value = echoFeedback,
                onValueChange = { echoFeedback = it },
                valueRange = 0f..0.8f
            )

            Text("مزج الصدى (Echo Mix): ${"%.0f".format(echoMix * 100)}%")
            Slider(
                value = echoMix,
                onValueChange = { echoMix = it },
                valueRange = 0f..1f
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
                status = "جارٍ القص والحفظ..."
                val path = AudioExporter.trimWithFades(
                    context, uri, startSec, endSec,
                    fadeInSec.coerceIn(0f, maxFade),
                    fadeOutSec.coerceIn(0f, maxFade),
                    echoDelaySec, echoFeedback, echoMix
                )
                status = if (path != null) {
                    "تم الحفظ في: $path"
                } else {
                    "فشل القص، تأكد أن الملف صوتي وصالح"
                }
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

/** يقرأ مدة الملف بالثواني، ويعيد 0 عند الفشل. */
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
