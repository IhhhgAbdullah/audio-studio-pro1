package com.example.audiostudiopro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * تغيير السرعة دون تغيير النغمة باستخدام WSOLA.
 * speed أكبر من 1 = أسرع وأقصر، وأقل من 1 = أبطأ وأطول.
 * المدخل والمخرج عينات float بنفس القنوات (interleaved).
 */
object TimeStretch {

    private const val FRAME = 1024
    private const val HOP = 512
    private const val SEARCH = 96

    fun stretch(input: FloatArray, channels: Int, speed: Float): FloatArray {
        if (speed <= 0f || kotlin.math.abs(speed - 1f) < 0.01f) return input
        val frames = input.size / channels
        if (frames < FRAME * 2) return input

        val window = FloatArray(FRAME) { i ->
            (0.5 - 0.5 * cos(2.0 * PI * i / (FRAME - 1))).toFloat()
        }

        val outFrames = (frames / speed).toInt() + FRAME
        val result = FloatArray(outFrames * channels)

        for (c in 0 until channels) {
            val mono = FloatArray(frames) { input[it * channels + c] }
            val stretched = stretchMono(mono, speed, window)
            val n = min(stretched.size, outFrames)
            for (f in 0 until n) {
                result[f * channels + c] = stretched[f]
            }
        }

        val finalFrames = min(outFrames, (frames / speed).toInt())
        return result.copyOf(finalFrames * channels)
    }

    private fun stretchMono(m: FloatArray, speed: Float, window: FloatArray): FloatArray {
        val n = m.size
        val analysisHop = HOP * speed
        val out = FloatArray((n / speed).toInt() + FRAME)
        val norm = FloatArray(out.size)

        var outPos = 0
        var k = 0
        var prevStart = -1

        while (outPos + FRAME <= out.size) {
            val nominal = (k * analysisHop).toInt()
            if (nominal + FRAME + SEARCH > n) break

            var bestStart = nominal
            if (prevStart >= 0) {
                val naturalStart = prevStart + HOP
                var bestCorr = Float.NEGATIVE_INFINITY
                for (d in -SEARCH..SEARCH step 2) {
                    val s = nominal + d
                    if (s < 0 || s + FRAME > n || naturalStart + FRAME > n) continue
                    var corr = 0f
                    var i = 0
                    while (i < FRAME) {
                        corr += m[s + i] * m[naturalStart + i]
                        i += 4
                    }
                    if (corr > bestCorr) {
                        bestCorr = corr
                        bestStart = s
                    }
                }
            }

            for (i in 0 until FRAME) {
                val w = window[i]
                out[outPos + i] += m[bestStart + i] * w
                norm[outPos + i] += w * w
            }

            prevStart = bestStart
            outPos += HOP
            k++
        }

        val end = min(outPos + FRAME, out.size)
        for (i in 0 until end) {
            if (norm[i] > 1e-3f) out[i] /= norm[i]
        }
        return out.copyOf(end)
    }
}
