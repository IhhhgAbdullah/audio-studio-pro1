package com.example.audiostudiopro

import kotlin.math.abs
import kotlin.math.exp

/**
 * Limiter بسيط مع Lookahead صفر:
 * يخفض الكسب فوراً عند تجاوز السقف، ثم يعيده تدريجياً (Release).
 * السقف بالمقياس الخطي من 32767 (مثلاً 0.89 ≈ -1 dBFS).
 */
object Limiter {

    fun apply(buf: FloatArray, channels: Int, sampleRate: Int, ceiling: Float = 32767f * 0.89f) {
        val frames = buf.size / channels
        if (frames == 0) return
        val release = exp(-1f / (0.100f * sampleRate))
        var gain = 1f

        for (f in 0 until frames) {
            var peak = 0f
            for (c in 0 until channels) {
                val a = abs(buf[f * channels + c])
                if (a > peak) peak = a
            }
            // الكسب المطلوب لهذه العينة حتى لا تتجاوز السقف
            val needed = if (peak > ceiling) ceiling / peak else 1f
            gain = if (needed < gain) needed else release * gain + (1f - release) * needed
            if (gain != 1f) {
                for (c in 0 until channels) {
                    buf[f * channels + c] *= gain
                }
            }
        }
    }
}
