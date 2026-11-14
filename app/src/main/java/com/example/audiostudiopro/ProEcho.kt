package com.example.audiostudiopro

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max

/**
 * Echo احترافي:
 * - تأخير ستيريو متبادل (Ping-Pong): كل تكرار ينتقل إلى القناة الأخرى.
 * - تصفية في حلقة التغذية الراجعة (Damping): التكرارات تصبح أدفأ مع كل تكرار.
 */
object ProEcho {

    fun apply(
        buf: FloatArray,
        channels: Int,
        sampleRate: Int,
        total: Int,
        delaySec: Float,
        feedback: Float,
        mix: Float,
        damping: Float = 0.45f
    ) {
        val fb = feedback.coerceIn(0f, 0.85f)
        val wet = mix.coerceIn(0f, 1f)
        val dl = max(1, (delaySec * sampleRate).toInt())

        // تردد قطع المرشح داخل التغذية الراجعة: من 9 كيلوهرتز (نقي) إلى 2 كيلوهرتز (دافئ)
        val cutoff = 9000f - 7000f * damping.coerceIn(0f, 1f)
        val a = 1f - exp(-2f * PI.toFloat() * cutoff / sampleRate)

        val src = buf.copyOf()
        val rings = Array(channels) { FloatArray(dl) }
        val lp = FloatArray(channels)
        val delayed = FloatArray(channels)
        var pos = 0

        for (f in 0 until total) {
            // 1) قراءة التأخير وتصفيته لكل قناة
            for (c in 0 until channels) {
                delayed[c] = rings[c][pos]
                lp[c] += a * (delayed[c] - lp[c])
            }

            // 2) كتابة الدخل مع التغذية الراجعة المتبادلة، ومزج الناتج
            for (c in 0 until channels) {
                val i = f * channels + c
                val x = src[i]
                val cross = if (channels > 1) lp[(c + channels - 1) % channels] else lp[c]
                rings[c][pos] = (x + fb * cross).coerceIn(-32768f, 32767f)
                buf[i] = x * (1f - wet) + delayed[c] * wet
            }

            pos = (pos + 1) % dl
        }
    }
}
