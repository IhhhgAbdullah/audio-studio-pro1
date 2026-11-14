package com.example.audiostudiopro

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin

data class FxParams(
    val fadeInSec: Float = 0f,
    val fadeOutSec: Float = 0f,
    val echoDelaySec: Float = 0.3f,
    val echoFeedback: Float = 0.4f,
    val echoMix: Float = 0f,
    val bass: Float = 0f,
    val distortion: Float = 0f,
    val chorus: Float = 0f,
    val reverb: Float = 0f,
    val pitchSemitones: Float = 0f,
    val clean: Boolean = false
)

object Effects {

    /** يطبّق كل المؤثرات بالترتيب على عينات PCM 16-bit ويعيد عينات بنفس الصيغة. */
    fun process(data: ByteArray, channels: Int, sampleRate: Int, p: FxParams): ByteArray {
        var bytes = data
        if (p.clean) {
            bytes = AudioCleaner.clean(bytes, channels, sampleRate, 0.5f)
        }
        val inFrames = bytes.size / (2 * channels)
        if (inFrames == 0) return bytes

        // تحويل إلى float
        var x = FloatArray(inFrames * channels)
        for (i in x.indices) {
            val idx = i * 2
            x[i] = ((bytes[idx].toInt() and 0xff) or (bytes[idx + 1].toInt() shl 8))
                .toShort().toFloat()
        }

        // 1) Pitch: إعادة أخذ العينات تغيّر النغمة والسرعة معاً
        var frames = inFrames
        if (abs(p.pitchSemitones) > 0.01f) {
            val ratio = 2f.pow(p.pitchSemitones / 12f)
            val newFrames = max(1, (frames / ratio).toInt())
            val y = FloatArray(newFrames * channels)
            for (f in 0 until newFrames) {
                val pos = f * ratio
                val i0 = pos.toInt().coerceAtMost(frames - 1)
                val i1 = min(i0 + 1, frames - 1)
                val t = pos - i0
                for (c in 0 until channels) {
                    val a = x[i0 * channels + c]
                    val b = x[i1 * channels + c]
                    y[f * channels + c] = a + (b - a) * t
                }
            }
            x = y
            frames = newFrames
        }

        // حساب ذيل الصدى والصدى الصناعي
        val echoTail = if (p.echoMix > 0f && p.echoFeedback > 0.01f) {
            val fb = p.echoFeedback.coerceIn(0f, 0.85f)
            val repeats = ln(0.001f) / ln(fb)
            val delayFrames = max(1, (p.echoDelaySec * sampleRate).toInt())
            min(sampleRate * 6, (delayFrames * repeats).toInt())
        } else {
            0
        }
        val reverbTail = if (p.reverb > 0f) sampleRate * 2 else 0
        val tail = max(echoTail, reverbTail)
        val total = frames + tail

        val buf = FloatArray(total * channels)
        System.arraycopy(x, 0, buf, 0, x.size)

        // 2) Bass Boost: نضيف نسخة منخفضة التردد من الصوت
        if (p.bass > 0f) {
            val a = 1f - exp(-2f * PI.toFloat() * 150f / sampleRate)
            for (c in 0 until channels) {
                var lp = 0f
                for (f in 0 until total) {
                    val i = f * channels + c
                    lp += a * (buf[i] - lp)
                    buf[i] = buf[i] + lp * p.bass * 1.5f
                }
            }
        }

        // 3) Distortion: تشويه ناعم بالمزج مع الصوت الأصلي
        if (p.distortion > 0f) {
            val d = p.distortion.coerceIn(0f, 1f)
            val k = 1f + 3f * d
            for (i in buf.indices) {
                val s = buf[i] / 32768f * k
                val wet = s / (1f + abs(s)) * 32768f
                buf[i] = buf[i] * (1f - d) + wet * d
            }
        }

        // 4) Chorus: تأخير متغير بموجة جيبية بطيئة
        if (p.chorus > 0f) {
            val amount = p.chorus.coerceIn(0f, 1f)
            val src = buf.copyOf()
            val baseDelay = 0.02f * sampleRate
            val depth = 0.005f * sampleRate
            val lfoStep = 2f * PI.toFloat() * 0.5f / sampleRate
            for (f in 0 until total) {
                val delay = baseDelay + depth * sin(lfoStep * f)
                val srcPos = f - delay
                for (c in 0 until channels) {
                    val i = f * channels + c
                    val wetSample = if (srcPos >= 0f) {
                        val i0 = srcPos.toInt()
                        val frac = srcPos - i0
                        val a = src[i0 * channels + c]
                        val b = src[min(i0 + 1, total - 1) * channels + c]
                        a + (b - a) * frac
                    } else {
                        0f
                    }
                    buf[i] = src[i] * (1f - amount * 0.5f) + wetSample * amount * 0.5f
                }
            }
        }

        // 5) Reverb: أربعة مرشحات تغذية راجعة بتأخيرات متداخلة
        if (p.reverb > 0f) {
            val r = p.reverb.coerceIn(0f, 1f)
            val fb = 0.6f + 0.25f * r
            val delays = intArrayOf(
                (0.0297f * sampleRate).toInt(),
                (0.0371f * sampleRate).toInt(),
                (0.0411f * sampleRate).toInt(),
                (0.0437f * sampleRate).toInt()
            )
            val src = buf.copyOf()
            for (c in 0 until channels) {
                val rings = Array(delays.size) { FloatArray(delays[it]) }
                val pos = IntArray(delays.size)
                for (f in 0 until total) {
                    val i = f * channels + c
                    var acc = 0f
                    for (k in delays.indices) {
                        val ring = rings[k]
                        val yOld = ring[pos[k]]
                        val yNew = src[i] + fb * yOld
                        ring[pos[k]] = yNew
                        pos[k] = (pos[k] + 1) % ring.size
                        acc += yNew
                    }
                    val wetS = acc / delays.size
                    buf[i] = src[i] * (1f - 0.5f * r) + wetS * 0.5f * r
                }
            }
        }

        // 6) Echo: حلقة تأخير مع تغذية راجعة ومزج
        if (p.echoMix > 0f && p.echoFeedback > 0.01f) {
            val fb = p.echoFeedback.coerceIn(0f, 0.85f)
            val mix = p.echoMix.coerceIn(0f, 1f)
            val dl = max(1, (p.echoDelaySec * sampleRate).toInt())
            val src = buf.copyOf()
            for (c in 0 until channels) {
                val ring = FloatArray(dl)
                var pos = 0
                for (f in 0 until total) {
                    val i = f * channels + c
                    val echo = ring[pos]
                    val y = (src[i] + echo * fb).coerceIn(-32768f, 32767f)
                    ring[pos] = y
                    pos = (pos + 1) % dl
                    buf[i] = src[i] * (1f - mix) + y * mix
                }
            }
        }

        // 7) Fade In / Fade Out
        if (p.fadeInSec > 0f || p.fadeOutSec > 0f) {
            val fi = (p.fadeInSec * sampleRate).toInt().coerceIn(0, total)
            val fo = (p.fadeOutSec * sampleRate).toInt().coerceIn(0, total)
            for (f in 0 until total) {
                var g = 1f
                if (fi > 0 && f < fi) g *= f.toFloat() / fi
                val fromEnd = total - 1 - f
                if (fo > 0 && fromEnd < fo) g *= fromEnd.toFloat() / fo
                if (g < 1f) {
                    for (c in 0 until channels) {
                        buf[f * channels + c] *= g
                    }
                }
            }
        }

        // تحويل إلى 16-bit مع قص القيم الزائدة
        val out = ByteArray(total * channels * 2)
        for (i in buf.indices) {
            val v = buf[i].toInt().coerceIn(-32768, 32767)
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        return out
    }
}
