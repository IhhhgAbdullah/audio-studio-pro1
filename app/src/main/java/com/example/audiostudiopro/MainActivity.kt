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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    primary = AccentGold,
                    onPrimary = Color(0xFF0D1B2A),
                    onBackground = Color(0xFFF5E6B8),
                    onSurface = Color(0xFFF5E6B8)
                )
            ) {
                RgbBackground {
                    AudioStudioScreen()
                }
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

    fun resetEffects() {
        fadeInSec = 0f
        fadeOutSec = 0f
        echoDelaySec = 0.3f
        echoFeedback = 0.4f
        echoMix = 0f
        bassAmt = 0f
        distAmt = 0f
        chorusAmt = 0f
        reverbAmt = 0f
        pitchSt = 0f
        eqLow = 0f
        eqMid = 0f
        eqHigh = 0f
        compAmt = 0f
        doublerAmt = 0f
        enhancerAmt = 0f
        gateAmt = 0.5f
        cleanEnabled = false
        overlayVol = 0.5f
        status = "تمت إعادة ضبط المؤثرات"
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

    val uri = sourceUri
    val maxFade = maxFadeNow()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppHeader()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GradientButton(
                text = if (isRecording) "إيقاف التسجيل" else "تسجيل من الميكروفون",
                brush = Brush.horizontalGradient(listOf(AccentBlue, AccentCyan)),
                modifier = Modifier.weight(1f)
            ) {
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
            }

            GradientButton(
                text = "اختيار ملف صوت",
                brush = Brush.horizontalGradient(listOf(AccentPurple, AccentPink)),
                enabled = !isRecording,
                modifier = Modifier.weight(1f)
            ) {
                picker.launch(arrayOf("audio/*"))
            }
        }

        InfoChip(
            if (durationSec > 0f) "المدة: ${"%.2f".format(durationSec)} ثانية" else "لا يوجد ملف محدد"
        )

        if (isRecording) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RecordingCircle(recorder)
            }
        }

        if (uri != null && durationSec > 0f && !isRecording) {
            SectionCard(title = "القص", icon = "✂️", accent = AccentPink, initiallyExpanded = true) {
                SliderRow(
                    label = "بداية القص",
                    valueText = "${"%.1f".format(startSec)} ث",
                    value = startSec,
                    range = 0f..max(durationSec, 0.1f),
                    accent = AccentPink
                ) {
                    startSec = min(it, endSec)
                }
                SliderRow(
                    label = "نهاية القص",
                    valueText = "${"%.1f".format(endSec)} ث",
                    value = endSec,
                    range = 0f..max(durationSec, 0.1f),
                    accent = AccentPink
                ) {
                    endSec = max(it, startSec)
                }
            }

            SectionCard(title = "التدرجات", icon = "📈", accent = AccentGold) {
                SliderRow(
                    label = "تدرج الدخول (Fade In)",
                    valueText = "${"%.1f".format(fadeInSec)} ث",
                    value = fadeInSec,
                    range = 0f..maxFade,
                    accent = AccentGold
                ) {
                    fadeInSec = it
                }
                SliderRow(
                    label = "تدرج الخروج (Fade Out)",
                    valueText = "${"%.1f".format(fadeOutSec)} ث",
                    value = fadeOutSec,
                    range = 0f..maxFade,
                    accent = AccentGold
                ) {
                    fadeOutSec = it
                }
            }

            SectionCard(title = "الصدى", icon = "🔁", accent = AccentCyan) {
                SliderRow(
                    label = "زمن الصدى (Delay)",
                    valueText = "${"%.2f".format(echoDelaySec)} ث",
                    value = echoDelaySec,
                    range = 0.05f..1f,
                    accent = AccentCyan
                ) {
                    echoDelaySec = it
                }
                SliderRow(
                    label = "تكرار الصدى (Feedback)",
                    valueText = "${"%.0f".format(echoFeedback * 100)}%",
                    value = echoFeedback,
                    range = 0f..0.8f,
                    accent = AccentCyan
                ) {
                    echoFeedback = it
                }
                SliderRow(
                    label = "مزج الصدى (Mix)",
                    valueText = "${"%.0f".format(echoMix * 100)}%",
                    value = echoMix,
                    range = 0f..1f,
                    accent = AccentCyan
                ) {
                    echoMix = it
                }
            }

            SectionCard(title = "تأثيرات الصوت", icon = "🎛️", accent = AccentPurple) {
                SliderRow("تعزيز الباس (Bass)", "${"%.0f".format(bassAmt * 100)}%", bassAmt, 0f..1f, AccentPurple) { bassAmt = it }
                SliderRow("تشويه (Distortion)", "${"%.0f".format(distAmt * 100)}%", distAmt, 0f..1f, AccentPurple) { distAmt = it }
                SliderRow("تعدد الأصوات (Chorus)", "${"%.0f".format(chorusAmt * 100)}%", chorusAmt, 0f..1f, AccentPurple) { chorusAmt = it }
                SliderRow("صدى القاعة (Reverb)", "${"%.0f".format(reverbAmt * 100)}%", reverbAmt, 0f..1f, AccentPurple) { reverbAmt = it }
                SliderRow("مضاعف الصوت (Doubler)", "${"%.0f".format(doublerAmt * 100)}%", doublerAmt, 0f..1f, AccentPurple) { doublerAmt = it }
                SliderRow("ضاغط الصوت (Compressor)", "${"%.0f".format(compAmt * 100)}%", compAmt, 0f..1f, AccentPurple) { compAmt = it }
                SliderRow("تحسين الصوت (Enhancer)", "${"%.0f".format(enhancerAmt * 100)}%", enhancerAmt, 0f..1f, AccentPurple) { enhancerAmt = it }
                SliderRow("النغمة (Pitch)", "${"%+.0f".format(pitchSt)} نصف نغمة", pitchSt, -12f..12f, AccentPurple) { pitchSt = it }
            }

            SectionCard(title = "الترددات والتنقية", icon = "🎚️", accent = AccentBlue) {
                SliderRow("المنخفضة (أقل من 250 هرتز)", "${"%+.0f".format(eqLow)} dB", eqLow, -12f..12f, AccentBlue) { eqLow = it }
                SliderRow("المتوسطة", "${"%+.0f".format(eqMid)} dB", eqMid, -12f..12f, AccentBlue) { eqMid = it }
                SliderRow("العالية (أعلى من 3000 هرتز)", "${"%+.0f".format(eqHigh)} dB", eqHigh, -12f..12f, AccentBlue) { eqHigh = it }
                SliderRow("قوة بوابة الضوضاء (Gate)", "${"%.0f".format(gateAmt * 100)}%", gateAmt, 0f..1f, AccentBlue) { gateAmt = it }
                GradientButton(
                    text = if (cleanEnabled) "التنقية: مفعّلة" else "التنقية: متوقفة",
                    brush = Brush.horizontalGradient(
                        if (cleanEnabled) listOf(AccentBlue, AccentCyan)
                        else listOf(Color(0xFF334155), Color(0xFF475569))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    cleanEnabled = !cleanEnabled
                }
            }

            SectionCard(title = "المزج مع ملف من الهاتف", icon = "🎵", accent = AccentGold) {
                GradientButton(
                    text = if (overlayUri == null) "إضافة صوت للمزج" else "تغيير ملف المزج",
                    brush = Brush.horizontalGradient(listOf(AccentGold, Color(0xFFB8860B))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    overlayPicker.launch(arrayOf("audio/*"))
                }
                if (overlayUri != null) {
                    SliderRow("مستوى ملف المزج", "${"%.0f".format(overlayVol * 100)}%", overlayVol, 0f..1f, AccentGold) {
                        overlayVol = it
                    }
                    GradientButton(
                        text = "إزالة ملف المزج",
                        brush = Brush.horizontalGradient(listOf(Color(0xFF7F1D1D), Color(0xFFB91C1C))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        overlayUri = null
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientButton(
                    text = "إعادة تعيين",
                    brush = Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF475569))),
                    modifier = Modifier.weight(1f)
                ) {
                    resetEffects()
                }
                Spacer(Modifier.width(12.dp))
                PlayFab(playing = isPlaying) {
                    if (isPlaying) {
                        player?.pause()
                        isPlaying = false
                        return@PlayFab
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
                }
                Spacer(Modifier.width(12.dp))
                GradientButton(
                    text = "حفظ الملف",
                    brush = Brush.horizontalGradient(listOf(AccentGold, Color(0xFFB8860B))),
                    modifier = Modifier.weight(1f)
                ) {
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
                }
            }
        }

        Text(
            text = status,
            color = Color(0xFF9FB3C8),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
