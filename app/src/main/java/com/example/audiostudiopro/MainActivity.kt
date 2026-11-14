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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var durationSec by remember { mutableFloatStateOf(0f) }
    var startSec by remember { mutableFloatStateOf(0f) }
    var endSec by remember { mutableFloatStateOf(0f) }
    var fadeInSec by remember { mutableFloatStateOf(0f) }
    var fadeOutSec by remember { mutableFloatStateOf(0f) }
    var echoDelaySec by remember { mutableFloatStateOf(0.3f) }
    var echoFeedback by remember { mutableFloatStateOf(0.4f) }
    var echoMix by remember { mutableFloatStateOf(0f) }
    var bassAmt by remember { mutableFloatStateOf(0f) }
    var distAmt by remember { mutableFloatStateOf(0f) }
    var chorusAmt by remember { mutableFloatStateOf(0f) }
    var reverbAmt by remember { mutableFloatStateOf(0f) }
    var pitchSt by remember { mutableFloatStateOf(0f) }
    var eqLow by remember { mutableFloatStateOf(0f) }
    var eqMid by remember { mutableFloatStateOf(0f) }
    var eqHigh by remember { mutableFloatStateOf(0f) }
    var compAmt by remember { mutableFloatStateOf(0f) }
    var doublerAmt by remember { mutableFloatStateOf(0f) }
    var enhancerAmt by remember { mutableFloatStateOf(0f) }
    var gateAmt by remember { mutableFloatStateOf(0.5f) }
    var cleanEnabled by remember { mutableStateOf(false) }
    var overlayUri by remember { mutableStateOf<Uri?>(null) }
    var overlayVol by remember { mutableFloatStateOf(0.5f) }
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
        status = if (durationSec > 0f) "تم تحميل الملف" else "تعذر قراءة مدة الملف"
    }

    fun maxFadeNow(): Float = max(0.1f, min(5f, (endSec - startSec) / 2f))

    fun buildParams(): FxParams {
        val maxFade = maxFadeNow()
        return FxParams(
            fadeInSec = fadeInSec.coerceIn(0f, maxFade),
            fadeOutSec = fadeOutSec.coerceIn(0f, maxFade),
            echoDelaySec = echoDelaySec,
            echoFeedback = echoFeedback,
            echoMix = echoMix,
            bass = bassAmt,
            distortion = distAmt,
            chorus = chorusAmt,
            reverb = reverbAmt,
            pitchSemitones = pitchSt,
            eqLow = eqLow,
            eqMid = eqMid,
            eqHigh = eqHigh,
            compressor = compAmt,
            doubler = doublerAmt,
            enhancer = enhancerAmt,
            clean = cleanEnabled,
            gate = gateAmt
        )
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) loadSource(uri)
    }

    val overlayPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            overlayUri = uri
            status = "تمت إضافة ملف المزج"
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
                player?.release()
                player = null
                isPlaying = false
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

            val maxFade = maxFadeNow()

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

            Text("تعزيز الباس (Bass Boost): ${"%.0f".format(bassAmt * 100)}%")
            Slider(
                value = bassAmt,
                onValueChange = { bassAmt = it },
                valueRange = 0f..1f
            )

            Text("تشويه (Distortion): ${"%.0f".format(distAmt * 100)}%")
            Slider(
                value = distAmt,
                onValueChange = { distAmt = it },
                valueRange = 0f..1f
            )

            Text("تعدد الأصوات (Chorus): ${"%.0f".format(chorusAmt * 100)}%")
            Slider(
                value = chorusAmt,
                onValueChange = { chorusAmt = it },
                valueRange = 0f..1f
            )

            Text("مضاعف الصوت (Vocal Doubler): ${"%.0f".format(doublerAmt * 100)}%")
            Slider(
                value = doublerAmt,
                onValueChange = { doublerAmt = it },
                valueRange = 0f..1f
            )

            Text("ضاغط الصوت (Compressor): ${"%.0f".format(compAmt * 100)}%")
            Slider(
                value = compAmt,
                onValueChange = { compAmt = it },
                valueRange = 0f..1f
            )

            Text("تحسين الصوت (Vocal Enhancer): ${"%.0f".format(enhancerAmt * 100)}%")
            Slider(
                value = enhancerAmt,
                onValueChange = { enhancerAmt = it },
                valueRange = 0f..1f
            )

            Text("صدى القاعة (Reverb): ${"%.0f".format(reverbAmt * 100)}%")
            Slider(
                value = reverbAmt,
                onValueChange = { reverbAmt = it },
                valueRange = 0f..1f
            )

            Text("النغمة (Pitch): ${"%+.0f".format(pitchSt)} نصف نغمة")
            Slider(
                value = pitchSt,
                onValueChange = { pitchSt = it },
                valueRange = -12f..12f
            )

            Text("الترددات المنخفضة (أقل من 250 هرتز): ${"%+.0f".format(eqLow)} dB")
            Slider(
                value = eqLow,
                onValueChange = { eqLow = it },
                valueRange = -12f..12f
            )

            Text("الترددات المتوسطة: ${"%+.0f".format(eqMid)} dB")
            Slider(
                value = eqMid,
                onValueChange = { eqMid = it },
                valueRange = -12f..12f
            )

            Text("الترددات العالية (أعلى من 3000 هرتز): ${"%+.0f".format(eqHigh)} dB")
            Slider(
                value = eqHigh,
                onValueChange = { eqHigh = it },
                valueRange = -12f..12f
            )

            Text("قوة بوابة الضوضاء (Gate): ${"%.0f".format(gateAmt * 100)}%")
            Slider(
                value = gateAmt,
                onValueChange = { gateAmt = it },
                valueRange = 0f..1f
            )

            Button(onClick = { cleanEnabled = !cleanEnabled }) {
                Text(if (cleanEnabled) "التنقية: مفعّلة" else "التنقية: متوقفة")
            }

            Button(onClick = { overlayPicker.launch(arrayOf("audio/*")) }) {
                Text(if (overlayUri == null) "إضافة صوت من الهاتف للمزج" else "تغيير ملف المزج")
            }

            if (overlayUri != null) {
                Text("مستوى ملف المزج: ${"%.0f".format(overlayVol * 100)}%")
                Slider(
                    value = overlayVol,
                    onValueChange = { overlayVol = it },
                    valueRange = 0f..1f
                )
                Button(onClick = { overlayUri = null }) {
                    Text("إزالة ملف المزج")
                }
            }

            Button(onClick = {
                if (isPlaying) {
                    player?.pause()
                    isPlaying = false
                    return@Button
                }
                player?.release()
                player = null
                status = "جارٍ تجهيز المعاينة..."

                val s = startSec
                val e = endSec
                val p = buildParams()
                val ov = overlayUri
                val ovv = overlayVol

                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        AudioExporter.renderPreview(context, uri, s, e, p, ov, ovv)
                    }
                    if (file == null) {
                        status = "فشل تجهيز المعاينة: ${AudioExporter.lastError}"
                    } else {
                        player = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            prepare()
                            setOnCompletionListener { isPlaying = false }
                            start()
                        }
                        isPlaying = true
                        status = "تشغيل المعاينة"
                    }
                }
            }) {
                Text(if (isPlaying) "إيقاف المعاينة" else "تشغيل المعاينة")
            }

            Button(onClick = {
                player?.pause()
                isPlaying = false
                status = "جارٍ القص والحفظ..."

                val s = startSec
                val e = endSec
                val p = buildParams()
                val ov = overlayUri
                val ovv = overlayVol

                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        AudioExporter.trimWithFades(context, uri, s, e, p, ov, ovv)
                    }
                    status = if (path != null) {
                        "تم الحفظ في: $path"
                    } else {
                        "فشل الحفظ: ${AudioExporter.lastError}"
                    }
                }
            }) {
                Text("قص وحفظ")
            }
        }

        Text(status)
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
