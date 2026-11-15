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
        val sampleRate: Int,
        val channels: Int,
        val bits: Int,
        val bytes: ByteArray
    )

    /** يقص المقطع ثم يطبّق المؤثرات، ويعيد البيانات الناتجة مع معلومات الترويسة. */
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
            return Rendered(header.sampleRate, header.channels, header.bitsPerSample, bytes)
        } finally {
            trimmed.delete()
        }
    }

    /** يكتب ترويسة WAV ثم البيانات على دفعات صغيرة. */
    private fun writeWavInChunks(out: File, r: Rendered) {
        FileOutputStream(out).use { o ->
            WavTrimmer.writeWavHeader(o, r.sampleRate, r.channels, r.bits, r.bytes.size)
            val chunk = 64 * 1024
            var off = 0
            while (off < r.bytes.size) {
                val n = minOf(chunk, r.bytes.size - off)
                o.write(r.bytes, off, n)
                off += n
            }
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
            writeWavInChunks(out, r)
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
            saveToDownloads(context, file, "AudioStudioPro_${System.currentTimeMillis()}.wav", "audio/wav")
        } catch (e: Throwable) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: "بدون تفاصيل"}"
            null
        }
    }

    /**
     * يحفظ الملف المعالج كـ M4A (AAC) بالجودة المختارة في Downloads/AudioStudioPro.
     * يبدأ بمعالجة المقطع كاملاً ثم يرمّزه، ويعيد المسار المعروض أو null.
     */
    fun saveM4aToDownloads(
        context: Context,
        wav: File,
        bitrate: Int
    ): String? {
        lastError = null
        lastSavedUri = null
        return try {
            val m4a = File(context.cacheDir, "export.m4a")
            if (!AacEncoder.encode(wav, m4a, bitrate)) {
                throw IllegalStateException("فشل ترميز AAC على هذا الجهاز")
            }
            val result = saveToDownloads(context, m4a, "AudioStudioPro_${System.currentTimeMillis()}.m4a", "audio/mp4")
            m4a.delete()
            result
        } catch (e: Throwable) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: "بدون تفاصيل"}"
            null
        }
    }

    private fun saveToDownloads(context: Context, file: File, name: String, mime: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/AudioStudioPro")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("تعذر إنشاء الملف في Downloads")
            val out = resolver.openOutputStream(itemUri)
                ?: throw IllegalStateException("تعذر فتح الملف للكتابة")
            out.use { o -> file.inputStream().use { it.copyTo(o, 64 * 1024) } }

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
