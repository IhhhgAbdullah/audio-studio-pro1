package com.example.audiostudiopro

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * تحليل تلقائي للضوضاء:
 * يقيس طاقة الإطارات القصيرة، ويأخذ أهدأ 10% منها كمستوى للضوضاء،
 * ثم يحسب قيمة بوابة (gate) من 0 إلى 1 بناءً على ذلك.
 */
object AutoNoise {

    /** يعيد قيمة البوابة المقترحة بين 0.2 و0.9. */
    fun suggestGate(data: ByteArray, channels: Int, sampleRate: Int): Float {
        val frames = data.size / (2 * channels)
        val win = max(1, sampleRate / 50) // نافذة 20 ملّي ثانية
        val count = frames / win
        if (count < 4) return 0.5f

        val rms = FloatArray(count)
        for (w in 0 until count) {
            var sum = 0.0
            for (f in w * win until (w + 1) * win) {
                // نأخذ القناة الأولى فقط للتحليل
                val idx = f * channels * 2
                val s = ((data[idx].toInt() and 0xff) or (data[idx + 1].toInt() shl 8))
                    .toShort().toDouble()
                sum += s * s
            }
            rms[w] = sqrt(sum / win).toFloat()
        }

        val sorted = rms.sortedArray()
        val noiseIdx = max(0, (sorted.size * 0.10f).toInt() - 1)
        val peakIdx = min(sorted.size - 1, (sorted.size * 0.95f).toInt())
        val noise = sorted[noiseIdx]
        val speech = sorted[peakIdx]

        if (speech <= 1f) return 0.5f

        // نسبة الضوضاء إلى الكلام: كلما كانت الضوضاء أعلى، زادت قوة البوابة
        val ratio = (noise / speech).coerceIn(0f, 1f)
        val gate = (0.2f + ratio * 2.5f).coerceIn(0.2f, 0.9f)
        return gate
    }

    /** يعيد نسبة الضوضاء التقريبية بالديسيبل، لعرضها للمستخدم. */
    fun noiseDb(data: ByteArray, channels: Int, sampleRate: Int): Float {
        val frames = data.size / (2 * channels)
        val win = max(1, sampleRate / 50)
        val count = frames / win
        if (count < 4) return 0f

        val rms = FloatArray(count)
        for (w in 0 until count) {
            var sum = 0.0
            for (f in w * win until (w + 1) * win) {
                val idx = f * channels * 2
                val s = ((data[idx].toInt() and 0xff) or (data[idx + 1].toInt() shl 8))
                    .toShort().toDouble()
                sum += s * s
            }
            rms[w] = sqrt(sum / win).toFloat()
        }
        val sorted = rms.sortedArray()
        val noise = max(sorted[max(0, (sorted.size * 0.10f).toInt() - 1)], 1f)
        return 20f * kotlin.math.log10(noise / 32768f)
    }
}
