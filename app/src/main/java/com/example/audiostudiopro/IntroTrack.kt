package com.example.audiostudiopro

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile

/** تحميل ملف اللحن وإلحاقه قبل المقطع الرئيسي. */
object IntroTrack {

    fun load(context: Context, uri: Uri, volume: Float): OverlayPcm {
        val path = WavTrimmer.trim(context, uri, 0f, 100000f)
            ?: Mp3Trimmer.trim(context, uri, 0f, 100000f)
            ?: throw IllegalStateException("تعذر قراءة ملف اللحن")
        val f = File(path)
        try {
            val h = WavTrimmer.readWavHeader(f)
                ?: throw IllegalStateException("ترويسة اللحن غير صالحة")
            if (h.bitsPerSample != 16) {
                throw IllegalStateException("اللحن يجب أن يكون 16-bit")
            }
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
            return OverlayPcm(samples, h.sampleRate, h.channels, volume)
        } finally {
            f.delete()
        }
    }

    /** يضيف اللحن قبل البيانات، مع تحويل القنوات ومعدل العينة إلى صيغة المقطع. */
    fun prepend(data: ByteArray, channels: Int, sampleRate: Int, intro: OverlayPcm): ByteArray {
        val srcIntroFrames = intro.samples.size / intro.channels
        if (srcIntroFrames == 0) return data

        val introFrames = (srcIntroFrames.toLong() * sampleRate / intro.sampleRate).toInt()
        val mainBytes = data.size
        val out = ByteArray(introFrames * channels * 2 + mainBytes)

        val ratio = intro.sampleRate.toFloat() / sampleRate
        for (f in 0 until introFrames) {
            val pos = (f * ratio).toInt().coerceAtMost(srcIntroFrames - 1)
            for (c in 0 until channels) {
                val sc = minOf(c, intro.channels - 1)
                val v = (intro.samples[pos * intro.channels + sc] * intro.volume)
                    .toInt().coerceIn(-32768, 32767)
                val i = (f * channels + c) * 2
                out[i] = (v and 0xff).toByte()
                out[i + 1] = (v shr 8).toByte()
            }
        }
        System.arraycopy(data, 0, out, introFrames * channels * 2, mainBytes)
        return out
    }
}
