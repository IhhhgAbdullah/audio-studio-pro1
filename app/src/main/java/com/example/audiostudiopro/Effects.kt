package com.example.audiostudiopro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** صوت إضافي يُمزج مع المقطع. عيناته بمقياس 16-bit كما في المقطع الأصلي. */
class OverlayPcm(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int,
    val volume: Float
)

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
    val speed: Float = 1f,
    val eqLow: Float = 0f,
    val eqMid: Float = 0f,
    val eqHigh: Float = 0f,
    val compressor: Float = 0f,
    val doubler: Float = 0f,
    val enhancer: Float = 0f,
    val clean: Boolean = false,
    val gate: Float = 0.5f,
    val overlay: OverlayPcm? = null
)

object Effects {

    fun process(data: ByteArray, channels: Int, sampleRate: Int, p: FxParams): ByteArray {
        var bytes = data
        if (p.clean) {
            bytes = AudioCleaner.clean(bytes, channels, sampleRate, p.gate)
        }
        val inFrames = bytes.size / (2 * channels)
        if (inFrames == 0) return bytes

        var x = FloatArray(inFrames * channels)
        for (i in x.indices) {
            val idx = i * 2
            x[i] = ((bytes[idx].toInt() and 0xff) or (bytes[idx + 1].toInt() shl 8))
                .toShort().toFloat()
        }

        // 0) Speed: تغيير السرعة مع الحفاظ على النغمة (WSOLA)
        if (abs(p.speed - 1f) > 0.01f) {
            x = TimeStretch.stretch(x, channels, p.speed)
        }
        var frames = x.size / channels
        if (frames == 0) return bytes

        // 1) Pitch
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
        System.arraycopy(x, 0, buf, 0, min(x.size, buf.size))

        // 2) Equalizer ثلاثي النطاق
        if (p.eqLow != 0f || p.eqMid != 0f || p.eqHigh != 0f) {
            applyEq3(buf, channels, sampleRate, p)
        }

        // 3) Vocal Enhancer
        if (p.enhancer > 0f) {
            val amt = p.enhancer.coerceIn(0f, 1f)
            val a = 1f - exp(-2f * PI.toFloat() * 2500f / sampleRate)
            for (c in 0 until channels) {
                var lp = 0f
                for (f in 0 until total) {
                    val i = f * channels + c
                    val x0 = buf[i]
                    lp += a * (x0 - lp)
                    val high = x0 - lp
                    buf[i] = x0 + high * amt * 0.8f
                }
            }
        }

        // 4) Bass Boost
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

        // 5) Distortion
        if (p.distortion > 0f) {
            val d = p.distortion.coerceIn(0f, 1f)
            val k = 1f + 3f * d
            for (i in buf.indices) {
                val s = buf[i] / 32768f * k
                val wet = s / (1f + abs(s)) * 32768f
                buf[i] = buf[i] * (1f - d) + wet * d
            }
        }

        // 6) Compressor
        if (p.compressor > 0f) {
            applyCompressor(buf, channels, sampleRate, p.compressor.coerceIn(0f, 1f))
        }

        // 7) Chorus
        if (p.chorus > 0f) {
            applyChorus(buf, channels, sampleRate, total, p.chorus.coerceIn(0f, 1f))
        }

        // 8) Vocal Doubler
        if (p.doubler > 0f) {
            applyDoubler(buf, channels, sampleRate, total, p.doubler.coerceIn(0f, 1f))
        }

        // 9) Reverb
        if (p.reverb > 0f) {
            applyReverb(buf, channels, sampleRate, total, p.reverb.coerceIn(0f, 1f))
        }

        // 10) Echo
        if (p.echoMix > 0f && p.echoFeedback > 0.01f) {
            applyEcho(buf, channels, sampleRate, total, p)
        }

        // 11) مزج صوت من الهاتف
        p.overlay?.let { ov -> mixOverlay(buf, channels, sampleRate, total, ov) }

        // 12) Fade In / Fade Out
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

        val out = ByteArray(total * channels * 2)
        for (i in buf.indices) {
            val v = buf[i].toInt().coerceIn(-32768, 32767)
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        return out
    }

    private fun applyEq3(buf: FloatArray, channels: Int, sampleRate: Int, p: FxParams) {
        val gL = 10f.pow(p.eqLow / 20f)
        val gM = 10f.pow(p.eqMid / 20f)
        val gH = 10f.pow(p.eqHigh / 20f)
        val frames = buf.size / channels
        val aL = 1f - exp(-2f * PI.toFloat() * 250f / sampleRate)
        val aH = 1f - exp(-2f * PI.toFloat() * 3000f / sampleRate)
        for (c in 0 until channels) {
            var lp1 = 0f
            var lp2 = 0f
            for (f in 0 until frames) {
                val i = f * channels + c
                val x = buf[i]
                lp1 += aL * (x - lp1)
                lp2 += aH * (x - lp2)
                val low = lp1
                val high = x - lp2
                val mid = x - low - high
                buf[i] = low * gL + mid * gM + high * gH
            }
        }
    }

    private fun applyCompressor(buf: FloatArray, channels: Int, sampleRate: Int, amount: Float) {
        val threshold = 32767f * 0.25f
        val ratio = 1f + 7f * amount
        val attack = exp(-1f / (0.005f * sampleRate))
        val release = exp(-1f / (0.150f * sampleRate))
        val frames = buf.size / channels
        var env = 0f
        for (f in 0 until frames) {
            var level = 0f
            for (c in 0 until channels) {
                level = max(level, abs(buf[f * channels + c]))
            }
            val coeff = if (level > env) attack else release
            env = coeff * env + (1f - coeff) * level

            var gain = 1f
            if (env > threshold) {
                val over = env / threshold
                gain = over.pow(1f / ratio - 1f)
            }
            val makeup = 1f + 0.6f * amount
            for (c in 0 until channels) {
                buf[f * channels + c] = (buf[f * channels + c] * gain * makeup)
                    .coerceIn(-32768f, 32767f)
            }
        }
    }

    private fun applyChorus(buf: FloatArray, channels: Int, sampleRate: Int, total: Int, amount: Float) {
        val src = buf.copyOf()
        val baseDelay = 0.02f * sampleRate
        val depth = 0.005f * sampleRate
        val lfoStep = 2f * PI.toFloat() * 0.5f / sampleRate
        for (f in 0 until total) {
            val delay = baseDelay + depth * sin(lfoStep * f)
            val srcPos = f - delay
            for (c in 0 until channels) {
                val i = f * channels + c
                val wet = if (srcPos >= 0f) {
                    val i0 = srcPos.toInt()
                    val frac = srcPos - i0
                    val a = src[i0 * channels + c]
                    val b = src[min(i0 + 1, total - 1) * channels + c]
                    a + (b - a) * frac
                } else {
                    0f
                }
                buf[i] = src[i] * (1f - amount * 0.5f) + wet * amount * 0.5f
            }
        }
    }

    private fun applyDoubler(buf: FloatArray, channels: Int, sampleRate: Int, total: Int, amount: Float) {
        val src = buf.copyOf()
        val baseDelay = 0.018f * sampleRate
        val depth = 0.004f * sampleRate
        val lfoStep = 2f * PI.toFloat() * 0.8f / sampleRate
        val mixAmt = 0.45f * amount
        for (f in 0 until total) {
            val delay = baseDelay + depth * sin(lfoStep * f)
            val srcPos = f - delay
            for (c in 0 until channels) {
                val i = f * channels + c
                val wet = if (srcPos >= 0f) {
                    val i0 = srcPos.toInt()
                    val frac = srcPos - i0
                    val a = src[i0 * channels + c]
                    val b = src[min(i0 + 1, total - 1) * channels + c]
                    a + (b - a) * frac
                } else {
                    0f
                }
                buf[i] = src[i] + wet * mixAmt
            }
        }
    }

    private fun applyReverb(buf: FloatArray, channels: Int, sampleRate: Int, total: Int, r: Float) {
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

    private fun applyEcho(buf: FloatArray, channels: Int, sampleRate: Int, total: Int, p: FxParams) {
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

    private fun mixOverlay(buf: FloatArray, channels: Int, sampleRate: Int, total: Int, ov: OverlayPcm) {
        val ovFrames = ov.samples.size / ov.channels
        if (ovFrames == 0) return
        val ratio = ov.sampleRate.toFloat() / sampleRate
        for (f in 0 until total) {
            val pos = (f * ratio).toInt()
            if (pos >= ovFrames) break
            for (c in 0 until channels) {
                val sc = min(c, ov.channels - 1)
                buf[f * channels + c] += ov.samples[pos * ov.channels + sc] * ov.volume
            }
        }
    }
}
            for (c in 0 until channels) {
                val i = f * channels + c
                val wet = if (srcPos >= 0f) {
                    val i0 = srcPos.toInt()
                    val frac = srcPos - i0
                    val a = src[i0 * channels + c]
                    val b = src[min(i0 + 1, total - 1) * channels + c]
                    a + (b - a) * frac
                } else {
                    0f
                }
                buf[i] = src[i] + wet * mixAmt
            }
        }
    }

    private fun applyReverb(buf: FloatArray, channels: Int, sampleRate: Int, total: Int, r: Float) {
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

    private fun applyEcho(buf: FloatArray, channels: Int, sampleRate: Int, total: Int, p: FxParams) {
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

    private fun mixOverlay(buf: FloatArray, channels: Int, sampleRate: Int, total: Int, ov: OverlayPcm) {
        val ovFrames = ov.samples.size / ov.channels
        if (ovFrames == 0) return
        val ratio = ov.sampleRate.toFloat() / sampleRate
        for (f in 0 until total) {
            val pos = (f * ratio).toInt()
            if (pos >= ovFrames) break
            for (c in 0 until channels) {
                val sc = min(c, ov.channels - 1)
                buf[f * channels + c] += ov.samples[pos * ov.channels + sc] * ov.volume
            }
        }
    }
}
