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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
 * كلمات كاراوكي تتحرك للأعلى أثناء التسجيل.
 * تبدأ عند 85% من ارتفاع الإطار، وتصعد بسرعة ثابتة تحددها المدة (بالثواني).
 */
@Composable
fun LiveKaraoke(lyrics: String, running: Boolean, scrollSec: Float) {
    val progress = remember { Animatable(0f) }
    var viewportPx by remember { mutableIntStateOf(0) }
    var textPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(running, scrollSec) {
        progress.snapTo(0f)
        if (running) {
            val ms = (scrollSec * 1000f).toInt().coerceAtLeast(5000)
            progress.animateTo(1f, tween(durationMillis = ms, easing = LinearEasing))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x66101C2E))
            .onSizeChanged { viewportPx = it.height }
    ) {
        val startY = viewportPx * 0.85f
        val travel = (textPx + startY).coerceAtLeast(1f)
        Text(
            text = lyrics,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .onSizeChanged { textPx = it.height }
                .offset {
                    IntOffset(0, (startY - progress.value * travel).roundToInt())
                }
        )
    }
}

/** يحلل الملف المحمّل ويعيد (قيمة البوابة المقترحة، مستوى الضوضاء بالديسيبل). */
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

/** يحلل النغمة لملف محمّل: يعيد النوتة الأكثر تكراراً، ونسبة النوافذ الواضحة. */
fun analyzePitchOf(context: Context, uri: Uri, durationSec: Float): String? {
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
        val results = PitchDetector.analyze(data, h.channels, h.sampleRate)
        val windowsTotal = max(1, (data.size / (2 * h.channels)) / (h.sampleRate / 20) * 2)
        if (results.isEmpty()) {
            "لم تُكشف نغمة واضحة في الملف"
        } else {
            val counts = results.groupingBy { it.noteName }.eachCount()
            val topNote = counts.maxByOrNull { it.value }!!.key
            val avgHz = results.map { it.frequencyHz }.average()
            val clarity = (results.size * 100f / windowsTotal).coerceAtMost(100f)
            "النوتة الأكثر تكراراً: $topNote، متوسط التردد: ${"%.1f".format(avgHz)} Hz، " +
                "النوافذ الواضحة: ${"%.0f".format(clarity)}%"
        }
    } catch (e: Exception) {
        null
    } finally {
        f.delete()
    }
}

/** يحسب منحنى النغمة لملف محمّل ويعيد نصه للعرض. */
fun pitchCurveOf(context: Context, uri: Uri, durationSec: Float, scale: ScaleChoice): String? {
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
        val points = PitchCurve.compute(data, h.channels, h.sampleRate)
        "السلم: ${scale.label()}\n" + PitchCurve.toText(points)
    } catch (e: Exception) {
        null
    } finally {
        f.delete()
    }
}

/** يمزج الموسيقى مع ملف WAV ويكتب الناتج في ملف جديد. */
fun mixMusicFile(context: Context, wav: File, music: OverlayPcm, ms: MusicSettings): File? {
    return try {
        val h = WavTrimmer.readWavHeader(wav) ?: return null
        if (h.bitsPerSample != 16) return null
        val data = ByteArray(h.dataSize)
        RandomAccessFile(wav, "r").use { raf ->
            raf.seek(h.dataOffset)
            raf.readFully(data)
        }
        val mixed = MusicMix.mix(data, h.channels, h.sampleRate, music, ms)
        val out = File(context.cacheDir, "mixed.wav")
        FileOutputStream(out).use { o ->
            WavTrimmer.writeWavHeader(o, h.sampleRate, h.channels, 16, mixed.size)
            o.write(mixed)
        }
        out
    } catch (e: Exception) {
        null
    }
}

/** يجهّز الملف النهائي للمعاينة أو الحفظ: القص والمؤثرات ثم الموسيقى المرافقة. */
fun buildPreview(
    context: Context,
    uri: Uri,
    s: Float,
    e: Float,
    p: FxParams,
    music: OverlayPcm?,
    ms: MusicSettings,
    pitchFix: Boolean,
    scale: ScaleChoice
): File? {
    val base = AudioExporter.renderPreview(context, uri, s, e, p, pitchFix, scale) ?: return null
    if (music == null || ms.volume <= 0f) return base
    return mixMusicFile(context, base, music, ms)
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
    var pitchAnalyzing by remember { mutableStateOf(false) }

    var lyricsText by remember { mutableStateOf("") }
    var karaokeRunning by remember { mutableStateOf(false) }
    var scrollSec by remember { mutableFloatStateOf(60f) }

    var scaleType by remember { mutableStateOf(ScaleType.MAJOR) }
    var scaleRoot by remember { mutableIntStateOf(0) }
    var pitchCurveText by remember { mutableStateOf<String?>(null) }
    var pitchFixOn by remember { mutableStateOf(false) }

    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var musicPcm by remember { mutableStateOf<OverlayPcm?>(null) }
    var musicLoading by remember { mutableStateOf(false) }
    var musicVol by remember { mutableFloatStateOf(0.5f) }
    var musicDelay by remember { mutableFloatStateOf(0f) }
    var musicOffset by remember { mutableFloatStateOf(0f) }

    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewKey by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var saveAsM4a by remember { mutableStateOf(false) }
    var bitrateIndex by remember { mutableIntStateOf(1) }

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
            speechEqAmt = pending.speechEq
            levelerAmt = pending.leveler
            dereverbAmt = pending.dereverb
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

    fun stopPlayback() {
        player?.release()
        player = null
        isPlaying = false
    }

    fun startPlayback(file: File) {
        stopPlayback()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.setOnCompletionListener { isPlaying = false }
            mp.start()
            player = mp
            isPlaying = true
        } catch (ex: Exception) {
            mp.release()
            status = "تعذر تشغيل المعاينة"
        }
    }

    fun loadSource(uri: Uri) {
        stopPlayback()
        previewFile = null
        previewKey = null
        pitchCurveText = null
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

    fun currentMusic(): MusicSettings =
        MusicSettings(volume = musicVol, delaySec = musicDelay, offsetSec = musicOffset)

    /** مفتاح يصف كل ما يؤثر على المعاينة. إذا تغيّر، تُعاد تعبئة المعاينة. */
    fun currentKey(): String = listOf(
        sourceUri?.toString(), startSec, endSec, fadeInSec, fadeOutSec,
        echoDelaySec, echoFeedback, echoMix, bassAmt, distAmt, chorusAmt, reverbAmt,
        pitchSt, speedAmt, eqLow, eqMid, eqHigh, compAmt, doublerAmt, enhancerAmt,
        levelerAmt, speechEqAmt, dereverbAmt, cleanEnabled, gateAmt,
        musicUri?.toString(), musicVol, musicDelay, musicOffset,
        pitchFixOn, scaleType, scaleRoot
    ).joinToString("|")

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
        speechEq = speechEqAmt,
        leveler = levelerAmt,
        dereverb = dereverbAmt,
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
        pitchFixOn = false
        status = "تمت إعادة ضبط المؤثرات"
    }

    fun shareLastSaved() {
        val shareUri = AudioExporter.lastSavedUri
        if (shareUri == null) {
            status = "احفظ الملف أولاً ثم شاركه"
            return
        }
        val mime = if (saveAsM4a) "audio/mp4" else "audio/wav"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
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

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            musicUri = uri
            musicPcm = null
            musicLoading = true
            status = "جارٍ تحميل الموسيقى..."
            scope.launch {
                val pcm = withContext(Dispatchers.IO) { MusicMix.load(context, uri) }
                musicPcm = pcm
                musicLoading = false
                status = if (pcm == null) {
                    "تعذر قراءة ملف الموسيقى، جرّب WAV أو MP3"
                } else {
                    "تم تحميل الموسيقى المرافقة"
                }
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (recorder.start()) {
                isRecording = true
                karaokeRunning = true
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
    val previewReady = previewFile != null && previewKey == currentKey()

    val currentScale = ScaleChoice(scaleType, scaleRoot)

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
                    karaokeRunning = false
                    if (file != null) {
                        loadSource(Uri.fromFile(file))
                        status = "تم إيقاف التسجيل وتحميله"
                    } else {
                        status = "فشل حفظ التسجيل"
                    }
                } else {
                    stopPlayback()
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

        SectionCard(title = "كلمات الأغنية (كاراوكي)", icon = "🎤", accent = AccentPink, initiallyExpanded = true) {
            if (!karaokeRunning) {
                Text(
                    "الصق كلمات الأغنية هنا، وتبدأ بالتحرك عند ضغط التسجيل",
                    color = Color(0xFF9FB3C8),
                    fontSize = 12.sp
                )
                BasicTextField(
                    value = lyricsText,
                    onValueChange = { lyricsText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x66101C2E))
                        .padding(12.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp)
                )
            } else {
                LiveKaraoke(lyrics = lyricsText, running = true, scrollSec = scrollSec)
            }
            SliderRow(
                "سرعة التمرير (مدة الصعود)",
                "${"%.0f".format(scrollSec)} ث",
                scrollSec,
                10f..300f,
                AccentPink
            ) {
                scrollSec = it
            }
        }

        if (isRecording) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RecordingCircle(recorder)
            }
        }

        if (uri != null && durationSec > 0f && !isRecording) {
            GradientButton(
                text = if (pitchAnalyzing) "جارٍ تحليل النغمة..." else "تحليل النغمة (Pitch)",
                brush = Brush.horizontalGradient(listOf(AccentCyan, AccentPurple)),
                enabled = !pitchAnalyzing,
                modifier = Modifier.fillMaxWidth()
            ) {
                val src = uri
                val dur = durationSec
                pitchAnalyzing = true
                status = "جارٍ تحليل النغمة..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) { analyzePitchOf(context, src, dur) }
                    pitchAnalyzing = false
                    status = result ?: "تعذر تحليل النغمة، جرّب ملف WAV"
                }
            }

            SectionCard(title = "منحنى النغمة وتصحيحها", icon = "🎼", accent = AccentPurple) {
                Text(
                    text = "السلم المختار: ${currentScale.label()}",
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text("نوع السلم", color = Color.White, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScaleType.values().forEach { t ->
                        GradientButton(
                            text = t.label,
                            brush = Brush.horizontalGradient(
                                if (scaleType == t) listOf(AccentPurple, AccentPink)
                                else listOf(Color(0xFF334155), Color(0xFF475569))
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            scaleType = t
                            pitchCurveText = null
                        }
                    }
                }
                if (scaleType != ScaleType.CHROMATIC) {
                    Text("الجذر", color = Color.White, fontSize = 13.sp)
                    val roots = (0 until 12).toList()
                    roots.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { r ->
                                GradientButton(
                                    text = PitchCorrect.NOTE_NAMES[r],
                                    brush = Brush.horizontalGradient(
                                        if (scaleRoot == r) listOf(AccentGold, Color(0xFFB8860B))
                                        else listOf(Color(0xFF334155), Color(0xFF475569))
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scaleRoot = r
                                    pitchCurveText = null
                                }
                            }
                        }
                    }
                }
                GradientButton(
                    text = "عرض منحنى النغمة",
                    brush = Brush.horizontalGradient(listOf(AccentCyan, AccentBlue)),
                    enabled = !pitchAnalyzing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val src = uri
                    val dur = durationSec
                    val sc = currentScale
                    pitchAnalyzing = true
                    status = "جارٍ حساب منحنى النغمة..."
                    scope.launch {
                        val text = withContext(Dispatchers.IO) {
                            pitchCurveOf(context, src, dur, sc)
                        }
                        pitchAnalyzing = false
                        pitchCurveText = text
                        status = if (text == null) "تعذر حساب منحنى النغمة" else "تم حساب منحنى النغمة"
                    }
                }
                pitchCurveText?.let { txt ->
                    Text(txt, color = Color(0xFFF5E6B8), fontSize = 12.sp)
                }
                Text(
                    "ملاحظة: هذه الخطوة تعرض المنحنى فقط، ولا تعدّل الصوت بعد.",
                    color = Color(0xFF9FB3C8),
                    fontSize = 11.sp
                )
                GradientButton(
                    text = if (pitchFixOn) "تصحيح النغمة: مفعّل (يُطبَّق على المعاينة والحفظ)" else "تصحيح النغمة: متوقف",
                    brush = Brush.horizontalGradient(
                        if (pitchFixOn) listOf(AccentGold, AccentPink)
                        else listOf(Color(0xFF334155), Color(0xFF475569))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pitchFixOn = !pitchFixOn
                    previewFile = null
                    previewKey = null
                }
            }

            SuggestCard(uri = uri, durationSec = durationSec)
            YinCompareCard(uri = uri, durationSec = durationSec)
            PitchFixCard(uri = uri, durationSec = durationSec, scale = currentScale)

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
                    val src = uri
                    val dur = durationSec
                    analyzing = true
                    status = "جارٍ تحليل الضوضاء..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { analyzeNoise(context, src, dur) }
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

            SectionCard(title = "موسيقى مرافقة (تُمزج بعد التسجيل)", icon = "🎵", accent = AccentGold) {
                GradientButton(
                    text = if (musicUri == null) "اختيار موسيقى" else "تغيير الموسيقى",
                    brush = Brush.horizontalGradient(listOf(AccentGold, Color(0xFFB8860B))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    musicPicker.launch(arrayOf("audio/*"))
                }
                if (musicUri != null) {
                    SliderRow("مستوى الموسيقى", "${"%.0f".format(musicVol * 100)}%", musicVol, 0f..1f, AccentGold) {
                        musicVol = it
                    }
                    SliderRow("تأخير الموسيقى", "${"%.1f".format(musicDelay)} ث", musicDelay, 0f..10f, AccentGold) {
                        musicDelay = it
                    }
                    SliderRow("تحريك الموسيقى (تبدأ من ثانية)", "${"%.1f".format(musicOffset)} ث", musicOffset, 0f..60f, AccentGold) {
                        musicOffset = it
                    }
                    GradientButton(
                        text = "إزالة الموسيقى",
                        brush = Brush.horizontalGradient(listOf(Color(0xFF7F1D1D), Color(0xFFB91C1C))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        musicUri = null
                        musicPcm = null
                        musicLoading = false
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

            SectionCard(title = "صيغة الحفظ والجودة", icon = "💿", accent = AccentGold) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton(
                        text = "WAV",
                        brush = Brush.horizontalGradient(
                            if (!saveAsM4a) listOf(AccentGold, Color(0xFFB8860B))
                            else listOf(Color(0xFF334155), Color(0xFF475569))
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        saveAsM4a = false
                    }
                    GradientButton(
                        text = "M4A (AAC)",
                        brush = Brush.horizontalGradient(
                            if (saveAsM4a) listOf(AccentGold, Color(0xFFB8860B))
                            else listOf(Color(0xFF334155), Color(0xFF475569))
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        saveAsM4a = true
                    }
                }
                if (saveAsM4a) {
                    Text("الجودة", color = Color.White, fontSize = 13.sp)
                    val labels = listOf("128 kbps", "192 kbps", "256 kbps")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.forEachIndexed { i, label ->
                            GradientButton(
                                text = label,
                                brush = Brush.horizontalGradient(
                                    if (bitrateIndex == i) listOf(AccentCyan, AccentBlue)
                                    else listOf(Color(0xFF334155), Color(0xFF475569))
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                bitrateIndex = i
                            }
                        }
                    }
                }
            }

            Text(
                text = if (previewReady) "المعاينة جاهزة (لن تُعاد تعبئتها ما لم تتغير الإعدادات)"
                else "تحتاج معاينة جديدة (ستُجهّز عند الضغط على التشغيل)",
                color = if (previewReady) AccentCyan else Color(0xFF9FB3C8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

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
                    if (busy || musicLoading) {
                        status = "انتظر اكتمال التحميل"
                        return@PlayFab
                    }
                    val cached = previewFile
                    val key = currentKey()
                    if (cached != null && previewKey == key) {
                        startPlayback(cached)
                        status = "تشغيل المعاينة"
                        return@PlayFab
                    }

                    val src = uri ?: return@PlayFab
                    val s = startSec
                    val e = endSec
                    val p = buildParams()
                    val music = musicPcm
                    val ms = currentMusic()
                    val fix = pitchFixOn
                    val sc = currentScale
                    stopPlayback()
                    busy = true
                    status = if (fix) "جارٍ تجهيز المعاينة مع تصحيح النغمة..." else "جارٍ تجهيز المعاينة..."

                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            buildPreview(context, src, s, e, p, music, ms, fix, sc)
                        }
                        busy = false
                        if (file == null) {
                            status = "فشل تجهيز المعاينة: ${AudioExporter.lastError}"
                        } else {
                            previewFile = file
                            previewKey = key
                            startPlayback(file)
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
                    if (busy || musicLoading) {
                        status = "انتظر اكتمال التحميل"
                        return@GradientButton
                    }
                    val src = uri ?: return@GradientButton
                    stopPlayback()
                    busy = true
                    status = "جارٍ القص والحفظ..."

                    val key = currentKey()
                    val cached = if (previewKey == key) previewFile else null
                    val s = startSec
                    val e = endSec
                    val p = buildParams()
                    val music = musicPcm
                    val ms = currentMusic()
                    val fix = pitchFixOn
                    val sc = currentScale
                    val m4a = saveAsM4a
                    val bitrate = AacEncoder.BITRATES[bitrateIndex]

                    scope.launch {
                        val path = withContext(Dispatchers.IO) {
                            val file = cached ?: buildPreview(context, src, s, e, p, music, ms, fix, sc)
                            if (file == null) null
                            else if (m4a) AudioExporter.saveM4aToDownloads(context, file, bitrate)
                            else AudioExporter.saveFileToDownloads(context, file)
                        }
                        busy = false
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
