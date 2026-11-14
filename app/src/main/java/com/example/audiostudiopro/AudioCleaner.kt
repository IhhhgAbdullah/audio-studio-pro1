package com.example.audiostudiopro

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.PI
import kotlin.math.sqrt

object AudioCleaner {

    /**
     * ينظف عينات PCM 16-bit: فلتر قطع يزيل الهدير، ثم بوابة ضوضاء، ثم تطبيع.
     * gateLevel بين 0 و1، كلما زاد ارتفعت عتبة البوابة فتُخفَّض الضوضاء أكثر.
     */
    fun clean(data: ByteArray, channels: Int, sampleRate: Int, gateLevel: Float): ByteArray {
        val frames = data.size / (2 * channels)
        if (frames == 0) return data

        // 1) تحويل إلى float لكل قناة
        val samples = FloatArray(frames * channels)
        for (i in samples.indices) {
            val idx = i * 2
            samples[i] = ((data[idx].toInt() and 0xff) or (data[idx + 1].toInt() shl 8))
                .toShort().toFloat()
        }

        // 2) فلتر قطع (High-pass) بتردد 80 هرتز لإزالة الهدير
        val rc = 1f / (2f * PI.toFloat() * 80f)
        val dt = 1f / sampleRate
        val alpha = rc / (rc + dt)
        for (c in 0 until channels) {
            var prevIn = 0f
            var prevOut = 0f
            for (f in 0 until frames) {
                val i = f * channels + c
                val x = samples[i]
                val y = alpha * (prevOut + x - prevIn)
                prevIn = x
                prevOut = y
                samples[i] = y
            }
        }

        // 3) بوابة ضوضاء: تخفض الصوت إذا كان أقل من العتبة
        val threshold = 32767f * (0.005f + gateLevel * 0.05f)
        val attack = exp(-1f / (0.005f * sampleRate))
        val release = exp(-1f / (0.150f * sampleRate))
        var env = 0f
        for (f in 0 until frames) {
            val level = 0f.let { var m = 0f
                for (c in 0 until channels) m = max(m, abs(samples[f * channels + c]))
                m }
            val coeff = if (level > env) attack else release
            env = coeff * env + (1f - coeff) * level
            val gain = if (env >= threshold) 1f else (env / threshold).let { it * it }
            for (c in 0 until channels) samples[f * channels + c] *= gain
        }

        // 4) تطبيع: رفع أعلى ذروة إلى 90% من الحد الأقصى
        var peak = 0f
        for (s in samples) peak = max(peak, abs(s))
        if (peak > 0f) {
            val norm = (32767f * 0.9f) / peak
            for (i in samples.indices) samples[i] *= norm
        }

        // 5) تحويل العينات إلى 16-bit
        val out = ByteArray(frames * channels * 2)
        for (i in samples.indices) {
            val v = samples[i].toInt().coerceIn(-32768, 32767)
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        return out
    }

    private fun max(a: Float, b: Float): Float = if (a > b) a else b
}
