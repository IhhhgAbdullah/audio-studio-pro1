package com.example.audiostudiopro

import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * كشف النغمة بخوارزمية YIN (de Cheveigné & Kawahara، 2002).
 * أدق من Autocorrelation البسيط لأنه يطبّع دالة الفرق (CMND) ويختار أول قاع واضح،
 * فيتجنب مضاعفات التردد. يعمل على القناة الأولى فقط.
 */
object YinPitch {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** يعيد التردد بالهرتز أو 0 إذا لم تُكشف نغمة واضحة. threshold عادةً 0.1 إلى 0.15. */
    fun detectHz(
        data: ByteArray,
        channels: Int,
        sampleRate: Int,
        offsetFrames: Int,
        windowFrames: Int,
        threshold: Float = 0.15f
    ): Float {
        val frameBytes = 2 * channels
        val total = data.size / frameBytes
        if (offsetFrames < 0 || offsetFrames + windowFrames > total) return 0f

        val x = FloatArray(windowFrames) { i ->
            val idx = (offsetFrames + i) * frameBytes
            ((data[idx].toInt() and 0xff) or (data[idx + 1].toInt() shl 8)).toShort().toFloat()
        }

        val minTau = (sampleRate / 800f).toInt().coerceAtLeast(2)
        val maxTau = (sampleRate / 70f).toInt().coerceAtMost(windowFrames / 2)
        if (maxTau <= minTau + 1) return 0f

        // دالة الفرق d(tau)
        val d = FloatArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var s = 0f
            for (i in 0 until windowFrames - tau) {
                val diff = x[i] - x[i + tau]
                s += diff * diff
            }
            d[tau] = s
        }

        // الفرق المطبّع التراكمي CMND
        val cmnd = FloatArray(maxTau + 1)
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..maxTau) {
            running += d[tau]
            cmnd[tau] = if (running > 0f) d[tau] * tau / running else 1f
        }

        // أول قاع يقع تحت العتبة
        var tauEst = -1
        var tau = minTau
        while (tau < maxTau) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 < maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
                tauEst = tau
                break
            }
            tau++
        }
        if (tauEst < 0) return 0f

        // تحسين بالتقريب الparabolic
        val a = cmnd[tauEst - 1]
        val b = cmnd[tauEst]
        val c = cmnd[tauEst + 1]
        val denom = a - 2 * b + c
        val shift = if (kotlin.math.abs(denom) > 1e-6f) 0.5f * (a - c) / denom else 0f
        val refined = tauEst + shift
        return if (refined > 0f) sampleRate / refined else 0f
    }

    /** يحوّل التردد إلى نص: النوتة والانحراف بالسنت. */
    fun describe(hz: Float): String {
        if (hz <= 0f) return "-"
        val midi = 69f + 12f * log2(hz / 440f)
        val nearest = midi.roundToInt()
        val cents = (midi - nearest) * 100f
        val name = NOTE_NAMES[((nearest % 12) + 12) % 12] + (nearest / 12 - 1)
        return "%s  %+.0f¢  (%.1f Hz)".format(name, cents, hz)
    }

    /** يحلل المقطع بنوافذ 50 ms ويعيد نصاً مختصراً للمقارنة مع PitchDetector. */
    fun compareSummary(data: ByteArray, channels: Int, sampleRate: Int): String {
        val win = sampleRate / 20
        val hop = win
        var voiced = 0
        var total = 0
        val notes = HashMap<String, Int>()
        var off = 0
        while (off + win <= data.size / (2 * channels)) {
            total++
            val hz = detectHz(data, channels, sampleRate, off, win)
            if (hz > 0f) {
                voiced++
                val name = describe(hz).substringBefore(" ")
                notes[name] = (notes[name] ?: 0) + 1
            }
            off += hop
        }
        if (voiced == 0) return "YIN: لم تُكشف نغمة واضحة"
        val top = notes.maxByOrNull { it.value }?.key ?: "-"
        return "YIN: النوتة الأكثر تكراراً $top، النوافذ الواضحة ${"%.0f".format(voiced * 100f / total)}%"
    }
}
