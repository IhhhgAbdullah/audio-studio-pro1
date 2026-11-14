package com.example.audiostudiopro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object SpeechEnhancer {

    /**
     * Leveling: يحسب مستوى RMS لكل نافذة (200 ملّي ثانية) ويحدد كسباً يقرّب
     * الجمل الهادئة من مستوى الكلام العام، مع سقف للكسب حتى لا يتضخم الضجيج.
     */
    fun level(buf: FloatArray, channels: Int, sampleRate: Int, amount: Float) {
        if (amount <= 0f) return
        val frames = buf.size / channels
        val win = max(1, sampleRate / 5)
        val count = (frames + win - 1) / win
        if (count == 0) return

        val rms = FloatArray(count)
        for (w in 0 until count) {
            var sum = 0.0
            val start = w * win
            val end = min(frames, start + win)
            for (f in start until end) {
                var s = 0f
                for (c in 0 until channels) s = max(s, abs(buf[f * channels + c]))
                sum += (s * s).toDouble()
            }
            rms[w] = sqrt(sum / max(1, end - start)).toFloat()
        }

        // المرجع: الوسيط للنوافذ التي فيها كلام (أعلى من 5% من الذروة)
        val peak = rms.maxOrNull() ?: 0f
        if (peak < 1f) return
        val active = rms.filter { it > peak * 0.05f }.sorted()
        if (active.isEmpty()) return
        val target = active[active.size / 2]

        val maxGain = 1f + 3f * amount
        val gains = FloatArray(count) { w ->
            if (rms[w] < peak * 0.05f) 1f
            else (target / rms[w]).coerceIn(1f / maxGain, maxGain).pow(amount)
        }

        // تنعيم الكسب بين النوافذ حتى لا تظهر قفزات مسموعة
        val smooth = FloatArray(count)
        var g = 1f
        val alpha = 0.3f
        for (w in 0 until count) {
            g += alpha * (gains[w] - g)
            smooth[w] = g
        }

        for (f in 0 until frames) {
            val gain = smooth[min(count - 1, f / win)]
            for (c in 0 until channels) {
                buf[f * channels + c] = (buf[f * channels + c] * gain).coerceIn(-32768f, 32767f)
            }
        }
    }

    /**
     * Speech EQ: خفض الطنين حول 300 هرتز (مرشح نطاقي تقريبي بمرشحين منخفض/عالٍ)
     * ورفع الوضوح حول 3 كيلوهرتز بإضافة الفرق بين نسخة مفلترة والأصل.
     */
    fun speechEq(buf: FloatArray, channels: Int, sampleRate: Int, amount: Float) {
        if (amount <= 0f) return
        val a = amount.coerceIn(0f, 1f)
        val cutLow = 1f - exp(-2f * PI.toFloat() * 300f / sampleRate)
        val cutHigh = 1f - exp(-2f * PI.toFloat() * 3000f / sampleRate)
        val gainBoost = 1f + 0.6f * a
        val gainCut = 1f - 0.3f * a
        for (c in 0 until channels) {
            var lp300 = 0f
            var lp3k = 0f
            for (f in 0 until buf.size / channels) {
                val i = f * channels + c
                val x = buf[i]
                lp300 += cutLow * (x - lp300)
                lp3k += cutHigh * (x - lp3k)
                val band300 = lp300
                val highBand = x - lp3k
                val mid = x - band300 - highBand
                // تخفيف الطنين حول 300 هرتز، ورفع نطاق الوضوح العالي
                buf[i] = (mid * gainCut + band300 * gainCut + highBand * gainBoost)
                    .coerceIn(-32768f, 32767f)
            }
        }
    }

    /**
     * Dereverb تلقائي: يقيس نسبة الطاقة المتأخرة إلى الطاقة المباشرة في كل نافذة،
     * ويخفّض الذيل المتأخر (الصدى) بقدر يتناسب مع هذه النسبة.
     */
    fun dereverb(buf: FloatArray, channels: Int, sampleRate: Int, amount: Float) {
        if (amount <= 0f) return
        val frames = buf.size / channels
        val delay = max(1, (0.05f * sampleRate).toInt())
        val a = amount.coerceIn(0f, 1f)
        // نحسب الطاقة المتأخرة ونطرحها بنسبة ثابتة لتقليل الذيل
        val src = buf.copyOf()
        for (c in 0 until channels) {
            var env = 0f
            var lateEnv = 0f
            val rel = exp(-1f / (0.05f * sampleRate))
            for (f in 0 until frames) {
                val i = f * channels + c
                val x = src[i]
                env = rel * env + (1f - rel) * abs(x)
                val past = if (f >= delay) abs(src[(f - delay) * channels + c]) else 0f
                lateEnv = rel * lateEnv + (1f - rel) * past
                val ratio = if (env > 1f) (lateEnv / env).coerceIn(0f, 1f) else 0f
                val reduce = 1f - a * 0.6f * ratio
                buf[i] = (x - (src[i] - x) * 0f) * reduce
            }
        }
    }
}
