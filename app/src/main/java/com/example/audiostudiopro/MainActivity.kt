package com.example.audiostudiopro

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
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
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            Box(modifier = if (tab == 0) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                AudioStudioScreen()
            }
            if (tab == 1) {
                PresetsScreen()
            }
        }
        NavigationBar(containerColor = Color(0xCC0D1B2A)) {
            NavigationBarItem(
                selected = tab == 0,
                onClick = { tab = 0 },
                icon = { Text("🎚️") },
                label = { Text("الرئيسية") }
            )
            NavigationBarItem(
                selected = tab == 1,
                onClick = { tab = 1 },
                icon = { Text("⭐") },
                label = { Text("المسبقات") }
            )
        }
    }
}

/** صفحة المسبقات: جاهزة وخاصة. الضغط على مسبقة يطبقها في الشاشة الرئيسية. */
@Composable
fun PresetsScreen() {
    val context = LocalContext.current
    var userPresets by remember { mutableStateOf(PresetStore.loadUser(context)) }
    var message by remember { mutableStateOf("اضغط مسبقة لتطبيقها في الشاشة الرئيسية") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppHeader()
        Text("مسبقات جاهزة", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Preset.BUILT_IN.forEach { p ->
            GradientButton(
                text = p.name,
                brush = Brush.horizontalGradient(listOf(AccentPurple, AccentBlue)),
                modifier = Modifier.fillMaxWidth()
            ) {
                PresetBus.pending = p
                message = "تم تطبيق: ${p.name}. ارجع للرئيسية لترى المؤشرات"
            }
        }

        Text("مسبقاتي", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (userPresets.isEmpty()) {
            Text("لا توجد مسبقات خاصة بعد", color = Color(0xFF9FB3C8), fontSize = 13.sp)
        }
        userPresets.forEachIndexed { index, p ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GradientButton(
                    text = p.name,
                    brush = Brush.horizontalGradient(listOf(AccentCyan, AccentBlue)),
                    modifier = Modifier.weight(1f)
                ) {
                    PresetBus.pending = p
                    message = "تم تطبيق: ${p.name}. ارجع للرئيسية لترى المؤشرات"
                }
                Spacer(Modifier.width(8.dp))
                GradientButton(
                    text = "حذف",
                    brush = Brush.horizontalGradient(listOf(Color(0xFF7F1D1D), Color(0xFFB91C1C))),
                ) {
                    val updated = userPresets.toMutableList().also { it.removeAt(index) }
                    PresetStore.saveUser(context, updated)
                    userPresets = updated
                    message = "تم حذف المسبقة"
                }
            }
        }

        Text(message, color = Color(0xFF9FB3C8), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** ناقل المسبقة المختارة. حالة مراقَبة حتى تلتقطها الشاشة الرئيسية فوراً. */
object PresetBus {
    var pending: Preset? by mutableStateOf(null)
}

/**
 * يحلل الملف المحمّل ويعيد (قيمة البوابة المقترحة، مستوى الضوضاء بالديسيبل).
 */
fun analyzeNoise(context: Context, uri: Uri, durationSec: Float): Pair<Float, Float>? {
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
        Pair(
            AutoNoise.suggestGate(data, h.channels, h.sampleRate),
            AutoNoise.noiseDb(data, h.channels, h.sampleRate)
        )
    } catch (e: Exception) {
        null
    } finally {
        f.delete()
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
    var speedAmt by remember { mutableFloatStateOf(1f) }
    var eqLow by remember { mutableFloatStateOf(0f) }
    var eqMid by remember { mutableFloatStateOf(0f) }
    var eqHigh by remember { mutableFloatStateOf(0f) }
    var compAmt by remember { mutableFloatStateOf(0f) }
    var doublerAmt by remember { mutableFloatStateOf(0f) }
    var enhancerAmt by remember { mutableFloatStateOf(0f) }
    var levelerAmt by remember { mutableFloatStateOf(0f) }
    var speechEqAmt by remember { mutableFloatStateOf(0f) }
    var dereverbAmt by remember { mutableFloatStateOf(0f) }
    var gateAmt by remember { mutableFloatStateOf(0.5f) }
    var cleanEnabled by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var overlayUri by remember { mutableStateOf<Uri?>(null) }
    var overlayVol by remember { mutableFloatStateOf(0.5f) }
    var status by remember { mutableStateOf("اختر ملف صوت أو سجّل للبدء") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    val recorder = remember { Recorder(context) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            if (recorder.isRecording) recorder.stop()
        }
    }

    // تطبيق المسبقة فور اختيارها من صفحة المسبقات
    val pending = PresetBus.pending
    LaunchedEffect(pending) {
        if (pending != null) {
            echoMix = pending.echoMix
            echoDelaySec = pending.echoDelay
            echoFeedback = pending.echoFeedback
            bassAmt = pending.bass
            distAmt = pending.distortion
            chorusAmt = pending.chorus
            reverbAmt = pending.reverb
            pitchSt = pending.pitch
            eqLow = pending.eqLow
            eqMid = pending.eqMid
            eqHigh = pending.eqHigh
            compAmt = pending.compressor
            doublerAmt = pending.doubler
            enhancerAmt = pending.enhancer
            gateAmt = pending.gate
            cleanEnabled = pending.clean
            status = if (sourceUri == null) {
                "تم تطبيق مسبقة: ${pending.name}. اختر ملفاً لتسمع النتيجة"
            } else {
                "تم تطبيق مسبقة: ${pending.name}"
            }
            PresetBus.pending = null
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
            speed = speedAmt,
            eqLow = eqLow,
            eqMid = eqMid,
            eqHigh = eqHigh,
            compressor = compAmt,
            doubler = doublerAmt,
            enhancer = enhancerAmt,
            leveler = levelerAmt,
            speechEq = speechEqAmt,
            dereverb = dereverbAmt,
            clean = cleanEnabled,
            gate = gateAmt
        )
    }

    fun currentAsPreset(name: String) = Preset(
        name = name,
        echoMix = echoMix,
        echoDelay = echoDelaySec,
        echoFeedback = echoFeedback,
        bass = bassAmt,
        distortion = distAmt,
        chorus = chorusAmt,
        reverb = reverbAmt,
        pitch = pitchSt,
        eqLow = eqLow,
        eqMid = eqMid,
        eqHigh = eqHigh,
        compressor = compAmt,
        doubler = doublerAmt,
        enhancer = enhancerAmt,
        gate = gateAmt,
        clean = cleanEnabled
    )

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
        speedAmt = 1f
        eqLow = 0f
        eqMid = 0f
        eqHigh = 0f
        compAmt = 0f
        doublerAmt = 0f
        enhancerAmt = 0f
        levelerAmt = 0f
        speechEqAmt = 0f
        dereverbAmt = 0f
        gateAmt = 0.5f
        cleanEnabled = false
        overlayVol = 0.5f
        status = "تمت إعادة ضبط المؤثرات"
    }

    fun shareLastSaved() {
        val shareUri = AudioExporter.lastSavedUri
        if (shareUri == null) {
            status = "احفظ الملف أولاً ثم شاركه"
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف"))
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
                SliderRow("بداية القص", "${"%.1f".format(startSec)} ث", startSec, 0f..max(durationSec, 0.1f), AccentPink) {
                    startSec = min(it, endSec)
                }
                SliderRow("نهاية القص", "${"%.1f".format(endSec)} ث", endSec, 0f..max(durationSec, 0.1f), AccentPink) {
                    endSec = max(it, startSec)
                }
            }

            SectionCard(title = "التدرجات", icon = "📈", accent = AccentGold) {
                SliderRow("تدرج الدخول (Fade In)", "${"%.1f".format(fadeInSec)} ث", fadeInSec, 0f..maxFade, AccentGold) {
                    fadeInSec = it
                }
                SliderRow("تدرج الخروج (Fade Out)", "${"%.1f".format(fadeOutSec)} ث", fadeOutSec, 0f..maxFade, AccentGold) {
                    fadeOutSec = it
                }
            }

            SectionCard(title = "الصدى", icon = "🔁", accent = AccentCyan) {
                SliderRow("زمن الصدى (Delay)", "${"%.2f".format(echoDelaySec)} ث", echoDelaySec, 0.05f..1f, AccentCyan) {
                    echoDelaySec = it
                }
                SliderRow("تكرار الصدى (Feedback)", "${"%.0f".format(echoFeedback * 100)}%", echoFeedback, 0f..0.8f, AccentCyan) {
                    echoFeedback = it
                }
                SliderRow("مزج الصدى (Mix)", "${"%.0f".format(echoMix * 100)}%", echoMix, 0f..1f, AccentCyan) {
                    echoMix = it
                }
            }

            SectionCard(title = "تأثيرات الصوت", icon = "🎛️", accent = AccentPurple) {
                SliderRow("السرعة (Speed) بدون تغيير النغمة", "${"%.2f".format(speedAmt)}x", speedAmt, 0.5f..2f, AccentPurple) { speedAmt = it }
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
                SliderRow("وضوح الكلام (Speech EQ)", "${"%.0f".format(speechEqAmt * 100)}%", speechEqAmt, 0f..1f, AccentBlue) { speechEqAmt = it }
                SliderRow("توحيد مستوى الكلام (Leveler)", "${"%.0f".format(levelerAmt * 100)}%", levelerAmt, 0f..1f, AccentBlue) { levelerAmt = it }
                SliderRow("تقليل الصدى الداخلي (Dereverb)", "${"%.0f".format(dereverbAmt * 100)}%", dereverbAmt, 0f..1f, AccentBlue) { dereverbAmt = it }
                SliderRow("قوة بوابة الضوضاء (Gate)", "${"%.0f".format(gateAmt * 100)}%", gateAmt, 0f..1f, AccentBlue) { gateAmt = it }

                GradientButton(
                    text = if (analyzing) "جارٍ التحليل..." else "تنقية تلقائية (تحليل الضوضاء)",
                    brush = Brush.horizontalGradient(listOf(AccentCyan, AccentBlue)),
                    enabled = !analyzing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val src = uri ?: return@GradientButton
                    val dur = durationSec
                    analyzing = true
                    status = "جارٍ تحليل الضوضاء..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            analyzeNoise(context, src, dur)
                        }
                        analyzing = false
                        if (result == null) {
                            status = "تعذر تحليل الملف، جرّب ملف WAV"
                        } else {
                            gateAmt = result.first
                            cleanEnabled = true
                            val db = "%.0f".format(result.second)
                            val pct = "%.0f".format(result.first * 100)
                            status = "مستوى الضوضاء: $db dB، تم ضبط البوابة إلى $pct% وتفعيل التنقية"
                        }
                    }
                }

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

            SectionCard(title = "المسبقات", icon = "💾", accent = AccentCyan) {
                GradientButton(
                    text = "حفظ الإعدادات كمسبقة",
                    brush = Brush.horizontalGradient(listOf(AccentCyan, AccentBlue)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val name = "مسبقتي ${PresetStore.loadUser(context).size + 1}"
                    val list = PresetStore.loadUser(context)
                    list.add(currentAsPreset(name))
                    PresetStore.saveUser(context, list)
                    status = "تم حفظ المسبقة: $name"
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
                        savedUri = AudioExporter.lastSavedUri
                        status = if (path != null) {
                            "تم الحفظ في: $path"
                        } else {
                            "فشل الحفظ: ${AudioExporter.lastError}"
                        }
                    }
                }
            }

            if (savedUri != null) {
                GradientButton(
                    text = "مشاركة آخر ملف محفوظ",
                    brush = Brush.horizontalGradient(listOf(AccentCyan, AccentPurple)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    shareLastSaved()
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
