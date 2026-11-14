package com.example.audiostudiopro

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile

/** إعدادات الموسيقى المرافقة داخل المزج. */
data class MusicSettings(
    val volume: Float = 0.5f,
    val delaySec: Float = 0f,
    val offsetSec: Float = 0f
)

object MusicMix {

    /** يحمّل ملف الموسيقى كعينات 16-bit بصيغة قابلة للمزج. */
    fun load(context: Context, uri: Uri): OverlayPcm? {
        val path = WavTrimmer.trim(context, uri, 0f, 100000f)
            ?: Mp3Trimmer.trim(context, uri, 0f, 100000f)
            ?: return null
        val f = File(path)
        return try {
            val h = WavTrimmer.readWavHeader(f) ?: return null
            if (h.bitsPerSample != 16) return null
            val data = ByteArray(h.dataSize)
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(h.dataOffset)
                raf.readFully(data)
            }
            val n = data.size / 2
            val samples = FloatArray(n) { i ->
                ((data[i * 2].toInt() and 0xff) or (data[i * 2 + 1].toInt() shl 8))
                    .toShort().toFloat()
            }
            OverlayPcm(samples, h.sampleRate, h.channels, 1f)
        } catch (e: Exception) {
            null
        } finally {
            f.delete()
        }
    }

    /**
     * يمزج الموسيقى مع بيانات الصوت (PCM 16-bit بنفس القنوات ومعدل العينة).
     * الترتيب: تحريك البداية (offset)، ثم التأخير (delay)، ثم المستوى.
     */
    fun mix(
        voice: ByteArray,
        channels: Int,
        sampleRate: Int,
        music: OverlayPcm,
        settings: MusicSettings
    ): ByteArray {
        val voiceFrames = voice.size / (2 * channels)
        val musicFrames = music.samples.size / music.channels
        if (musicFrames == 0 || settings.volume <= 0f) return voice

        val ratio = music.sampleRate.toFloat() / sampleRate
        val offsetFrames = (settings.offsetSec * sampleRate).toInt().coerceAtLeast(0)
        val delayFrames = (settings.delaySec * sampleRate).toInt().coerceAtLeast(0)

        val out = voice.copyOf()
        for (f in 0 until voiceFrames) {
            val t = f - delayFrames
            if (t < 0) continue
            val srcFrame = ((t + offsetFrames) * ratio).toInt()
            if (srcFrame >= musicFrames) break
            for (c in 0 until channels) {
                val sc = minOf(c, music.channels - 1)
                val m = music.samples[srcFrame * music.channels + sc] * settings.volume
                val i = (f * channels + c) * 2
                val cur = ((out[i].toInt() and 0xff) or (out[i + 1].toInt() shl 8))
                    .toShort().toInt()
                val v = (cur + m.toInt()).coerceIn(-32768, 32767)
                out[i] = (v and 0xff).toByte()
                out[i + 1] = (v shr 8).toByte()
            }
        }
        return out
    }
}
