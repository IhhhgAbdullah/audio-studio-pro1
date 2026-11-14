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

    /** يقص المقطع ثم يطبّق المؤثرات. */
    private fun render(
        context: Context,
        uri: Uri,
        startSec: Float,
        endSec: Float,
        p: FxParams
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

            val bytes = if (header.bitsPerSample == 16) {
                Effects.process(data, header.channels, header.sampleRate, p)
            } else {
                data
            }
            return Rendered(bytes, header.sampleRate, header.channels, header.bitsPerSample)
        } finally {
            trimmed.delete()
        }
    }

    /** يجهّز ملف WAV للمعاينة في مجلد الكاش، ويعيد الملف أو null عند الفشل. */
    fun renderPreview(
        context: Context,
        uri: Uri,
        startSec: Float,
        endSec: Float,
        p: FxParams
    ): File? {
        lastError = null
        return try {
            val r = render(context, uri, startSec, endSec, p)
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

    /** يحفظ ملف WAV جاهزاً في Downloads/AudioStudioPro، ويعيد المسار المعروض أو null. */
    fun saveFileToDownloads(context: Context, file: File): String? {
        lastError = null
        lastSavedUri = null
        return try {
            saveToDownloads(context, file, "AudioStudioPro_${System.currentTimeMillis()}.wav")
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
