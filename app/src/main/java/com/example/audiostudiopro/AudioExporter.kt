package com.example.audiostudiopro

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object AudioExporter {

    /** سبب آخر فشل، ليُعرض للمستخدم. */
    var lastError: String? = null

    /** Uri آخر ملف محفوظ، يُستخدم لزر المشاركة. */
    var lastSavedUri: Uri? = null

    private class Rendered(
        val bytes: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val bits: Int
    )

    private fun loadOverlay(context: Context, uri: Uri, volume: Float): OverlayPcm {
        val path = WavTrimmer.trim(context, uri, 0f, 100000f)
            ?: Mp3Trimmer.trim(context, uri, 0f, 100000f)
            ?: throw IllegalStateException("تعذر قراءة ملف المزج")
        val f = File(path)
        try {
            val h = WavTrimmer.readWavHeader(f)
                ?: throw IllegalStateException("ترويسة ملف المزج غير صالحة")
            if (h.bitsPerSample != 16) {
                throw IllegalStateException("ملف المزج يجب أن يكون 16-bit")
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

    private fun render(
        context: Context,
        uri: Uri,
        startSec: Float,
        endSec: Float,
        p: FxParams,
        overlayUri: Uri?,
        overlayVolume: Float,
        introUri: Uri?,
        introVolume: Float
    ): Rendered {
        val trimmedPath = WavTrimmer.trim(context, uri, startSec, endSec)
            ?: Mp3Trimmer.trim(context, uri, startSec, endSec)
            ?: throw IllegalStateException("لم يُقرأ الملف أو المقطع فارغ")

        val trimmed = File(trimmedPath)
        try {
            val header = WavTrimmer.readWavHeader(trimmed)
                ?: throw IllegalStateException("ترويسة WAV غير صالحة")

            val data = ByteArray(header.dataSize)
            RandomAccessFile(trimmed, "r").use { raf ->
                raf.seek(header.dataOffset)
                raf.readFully(data)
            }

            val full = if (overlayUri != null) {
                p.copy(overlay = loadOverlay(context, overlayUri, overlayVolume))
            } else {
                p
            }

            var bytes = if (header.bitsPerSample == 16) {
                Effects.process(data, header.channels, header.sampleRate, full)
            } else {
                data
            }

            // اللحن يُلحق بعد المؤثرات، فلا يتأثر بها
            if (introUri != null && header.bitsPerSample == 16) {
                val intro = IntroTrack.load(context, introUri, introVolume)
                bytes = IntroTrack.prepend(bytes, header.channels, header.sampleRate, intro)
            }

            return Rendered(bytes, header.sampleRate, header.channels, header.bitsPerSample)
        } finally {
            trimmed.delete()
        }
    }

    fun trimWithFades(
        context: Context,
        uri: Uri,
        startSec: Float,
        endSec: Float,
        p: FxParams,
        overlayUri: Uri?,
        overlayVolume: Float,
        introUri: Uri? = null,
        introVolume: Float = 1f
    ): String? {
        lastError = null
        lastSavedUri = null
        return try {
            val r = render(
                context, uri, startSec, endSec, p,
                overlayUri, overlayVolume, introUri, introVolume
            )
            val outTemp = File(context.cacheDir, "export.wav")
            FileOutputStream(outTemp).use { out ->
                WavTrimmer.writeWavHeader(out, r.sampleRate, r.channels, r.bits, r.bytes.size)
                out.write(r.bytes)
            }
            val name = "AudioStudioPro_${System.currentTimeMillis()}.wav"
            val saved = saveToDownloads(context, outTemp, name)
            outTemp.delete()
            saved
        } catch (e: Throwable) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: "بدون تفاصيل"}"
            null
        }
    }

    fun renderPreview(
        context: Context,
        uri: Uri,
        startSec: Float,
        endSec: Float,
        p: FxParams,
        overlayUri: Uri?,
        overlayVolume: Float,
        introUri: Uri? = null,
        introVolume: Float = 1f
    ): File? {
        lastError = null
        return try {
            val r = render(
                context, uri, startSec, endSec, p,
                overlayUri, overlayVolume, introUri, introVolume
            )
            val out = File(context.cacheDir, "preview.wav")
            FileOutputStream(out).use { o ->
                WavTrimmer.writeWavHeader(o, r.sampleRate, r.channels, r.bits, r.bytes.size)
                o.write(r.bytes)
            }
            out
        } catch (e: Throwable) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: "بدون تفاصيل"}"
            null
        }
    }

    private fun saveToDownloads(context: Context, file: File, name: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/AudioStudioPro")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("تعذر إنشاء الملف في Downloads")
            val out = resolver.openOutputStream(itemUri)
                ?: throw IllegalStateException("تعذر فتح الملف للكتابة")
            out.use { o -> file.inputStream().use { it.copyTo(o) } }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            lastSavedUri = itemUri
            return "Download/AudioStudioPro/$name"
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AudioStudioPro"
            )
            dir.mkdirs()
            val dest = File(dir, name)
            file.copyTo(dest, overwrite = true)
            lastSavedUri = Uri.fromFile(dest)
            return dest.absolutePath
        }
    }
}
