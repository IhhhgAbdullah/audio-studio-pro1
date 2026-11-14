package com.example.audiostudiopro

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/** نتيجة كشف النغمة في نافذة واحدة. */
data class PitchResult(
    val frequencyHz: Float,
    val noteName: String,
    val centsOff: Float,
    val confidence: Float
)

object PitchDetector {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** يكشف النغمة في نافذة من عينات PCM 16-bit (القناة الأولى فقط). يعيد null إذا لم تُكشف نغمة واضحة. */
    fun detect(data: ByteArray, channels: Int, sampleRate: Int, offsetFrames: Int, windowFrames: Int): PitchResult? {
        val frameBytes = 2 * channels
        val total = data.size / frameBytes
        if (offsetFrames < 0 || offsetFrames + windowFrames > total) return null

        val x = FloatArray(windowFrames) { i ->
            val idx = (offsetFrames + i) * frameBytes
            ((data[idx].toInt() and 0xff) or (data[idx + 1].toInt() shl 8)).toShort().toFloat()
        }

        // نطاق الصوت البشري تقريباً: 70 إلى 800 هرتز
        val minLag = (sampleRate / 800f).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / 70f).toInt().coerceAtMost(windowFrames / 2)
        if (maxLag <= minLag) return null

        var energy = 0f
        for (v in x) energy += v * v
        if (energy < 1e-3f * windowFrames * 1000f) return null

        // دالة Autocorrelation مع تطبيع
        val r0 = energy
        var bestLag = -1
        var bestVal = 0f
        val corr = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var s = 0f
            for (i in 0 until windowFrames - lag) s += x[i] * x[i + lag]
            corr[lag] = s / r0
        }
        // نبحث عن أول قمة قوية، لتفادي الاختيار الخاطئ للمضاعفات
        for (lag in minLag + 1 until maxLag) {
            if (corr[lag] > corr[lag - 1] && corr[lag] >= corr[lag + 1] && corr[lag] > 0.5f) {
                if (corr[lag] > bestVal) {
                    bestVal = corr[lag]
                    bestLag = lag
                }
                break
            }
        }
        if (bestLag < 0) return null

        // تحسين دقة القمة بتقريب parabolic
        val a = corr[bestLag - 1]
        val b = corr[bestLag]
        val c = corr[bestLag + 1]
        val denom = a - 2 * b + c
        val shift = if (abs(denom) > 1e-6f) 0.5f * (a - c) / denom else 0f
        val refinedLag = bestLag + shift

        val freq = sampleRate / refinedLag
        val midi = 69 + 12 * log2(freq / 440f)
        val nearest = midi.roundToInt()
        val cents = (midi - nearest) * 100f
        val name = NOTE_NAMES[((nearest % 12) + 12) % 12] + (nearest / 12 - 1)

        return PitchResult(freq, name, cents, bestVal.coerceIn(0f, 1f))
    }

    /**
     * يمرّ على المقطع كله بنوافذ متداخلة ويعيد قائمة نتائج الكشف،
     * مع تجاهل النوافذ الصامتة أو غير الواضحة.
     */
    fun analyze(data: ByteArray, channels: Int, sampleRate: Int): List<PitchResult> {
        val windowFrames = sampleRate / 20 // 50 ملّي ثانية
        val hop = windowFrames / 2
        val total = data.size / (2 * channels)
        val out = ArrayList<PitchResult>()
        var offset = 0
        while (offset + windowFrames <= total) {
            detect(data, channels, sampleRate, offset, windowFrames)?.let { out.add(it) }
            offset += hop
        }
        return out
    }
}
