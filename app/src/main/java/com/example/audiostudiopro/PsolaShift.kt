package com.example.audiostudiopro

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * تصحيح نغمة مبسط (الخطوة الأولى من PSOLA):
 * يحسب لكل نافذة (50 ms) التردد بـ YIN، ثم النوتة المسموحة في السلم،
 * ويطبق تعديلاً بحد أقصى maxCents مع تنعيم بين النوافذ.
 * الناتج بنفس طول المدخل ونفس السرعة.
 */
object PsolaShift {

    fun correct(
        data: FloatArray,
        channels: Int,
        sampleRate: Int,
        scale: ScaleChoice,
        maxCents: Float = 50f
    ): FloatArray {
        val frames = data.size / channels
        val win = sampleRate / 20
        if (frames < win * 2) return data
        val out = FloatArray(data.size)

        // قيمة النسبة لكل نافذة: 1 يعني بلا تعديل
        val windows = (frames + win - 1) / win
        val ratios = FloatArray(windows) { 1f }

        for (w in 0 until windows) {
            val start = w * win
            if (start + win > frames) break
            val bytes = ByteArray(win * channels * 2)
            // تحويل النافذة إلى بايتات PCM لتستخدمها YinPitch
            for (i in 0 until win * channels) {
                val v = data[start * channels + i].toInt().coerceIn(-32768, 32767)
                bytes[i * 2] = (v and 0xff).toByte()
                bytes[i * 2 + 1] = (v shr 8).toByte()
            }
            val hz = YinPitch.detectHz(bytes, channels, sampleRate, 0, win)
            if (hz <= 0f) continue
            val midi = 69f + 12f * log2(hz / 440f)
            val target = nearestAllowed(midi, scale)
            var cents = (target - midi) * 100f
            cents = cents.coerceIn(-maxCents, maxCents)
            ratios[w] = 2f.pow(cents / 1200f)
        }

        // تنعيم النسب بين النوافذ (متوسط متحرك بسيط)
        val smooth = FloatArray(windows) { ratios[it] }
        for (w in 1 until windows - 1) {
            smooth[w] = (ratios[w - 1] + 2 * ratios[w] + ratios[w + 1]) / 4f
        }

        // إعادة أخذ العينات بنسبة متغيرة (تغيّر النغمة مع الحفاظ على الطول)
        for (c in 0 until channels) {
            var readPos = 0f
            for (f in 0 until frames) {
                val w = minOf(f / win, windows - 1)
                val r = smooth[w]
                readPos += r
                val p = readPos.coerceIn(0f, (frames - 1).toFloat())
                val i0 = p.toInt()
                val i1 = minOf(i0 + 1, frames - 1)
                val t = p - i0
                val a = data[i0 * channels + c]
                val b = data[i1 * channels + c]
                out[f * channels + c] = a + (b - a) * t
            }
        }
        return out
    }

    private fun nearestAllowed(midi: Float, scale: ScaleChoice): Float {
        val base = midi.roundToInt()
        var best = base.toFloat()
        var bestDist = Float.MAX_VALUE
        for (cand in (base - 6)..(base + 6)) {
            val pc = ((cand % 12) + 12) % 12
            val rel = ((pc - scale.root) % 12 + 12) % 12
            if (!scale.type.intervals.contains(rel)) continue
            val d = abs(cand - midi)
            if (d < bestDist) {
                bestDist = d
                best = cand.toFloat()
            }
        }
        return best
    }
}
