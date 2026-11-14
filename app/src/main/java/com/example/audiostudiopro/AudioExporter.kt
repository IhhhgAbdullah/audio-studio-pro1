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

    /** يقص الملف، يطبق تدرج الدخول والخروج، ثم يحفظه في Downloads. يعيد المسار المعروض أو null. */
    fun trimWithFades(
        context: Context,
        uri: Uri,
        startSec: Float,
        endSec: Float,
        fadeInSec: Float,
        fadeOutSec: Float
    ): String? {
        val trimmedPath = WavTrimmer.trim(context, uri, startSec, endSec)
            ?: Mp3Trimmer.trim(context, uri, startSec, endSec)
            ?: return null

        val trimmed = File(trimmedPath)
        return try {
            val header = WavTrimmer.readWavHeader(trimmed) ?: return null

            val data = ByteArray(header.dataSize)
            RandomAccessFile(trimmed, "r").use { raf ->
                raf.seek(header.dataOffset)
                raf.readFully(data)
            }

            val finalData = if (header.bitsPerSample == 16 && (fadeInSec > 0f || fadeOutSec > 0f)) {
                AudioEffects.applyFades(data, header.channels, header.sampleRate, fadeInSec, fadeOutSec)
            } else {
                data
            }

            val outTemp = File(context.cacheDir, "export.wav")
            FileOutputStream(outTemp).use { out ->
                WavTrimmer.writeWavHeader(
                    out, header.sampleRate, header.channels, header.bitsPerSample, finalData.size
                )
                out.write(finalData)
            }

            val name = "AudioStudioPro_${System.currentTimeMillis()}.wav"
            val saved = saveToDownloads(context, outTemp, name)
            outTemp.delete()
            saved
        } catch (e: Exception) {
            null
        } finally {
            trimmed.delete()
        }
    }

    private fun saveToDownloads(context: Context, file: File, name: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/AudioStudioPro")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(itemUri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                } ?: return null

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                "Download/AudioStudioPro/$name"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "AudioStudioPro"
                )
                dir.mkdirs()
                val dest = File(dir, name)
                file.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** مؤثرات بسيطة تعمل على عينات PCM 16-bit. */
object AudioEffects {

    fun applyFades(
        data: ByteArray,
        channels: Int,
        sampleRate: Int,
        fadeInSec: Float,
        fadeOutSec: Float
    ): ByteArray {
        val out = data.copyOf()
        val frameCount = data.size / (2 * channels)
        val fadeInFrames = (fadeInSec * sampleRate).toInt().coerceIn(0, frameCount)
        val fadeOutFrames = (fadeOutSec * sampleRate).toInt().coerceIn(0, frameCount)

        for (f in 0 until frameCount) {
            var gain = 1f
            if (fadeInFrames > 0 && f < fadeInFrames) {
                gain *= f.toFloat() / fadeInFrames
            }
            val fromEnd = frameCount - 1 - f
            if (fadeOutFrames > 0 && fromEnd < fadeOutFrames) {
                gain *= fromEnd.toFloat() / fadeOutFrames
            }
            if (gain >= 1f) continue

            for (c in 0 until channels) {
                val idx = (f * channels + c) * 2
                val sample = ((out[idx].toInt() and 0xff) or (out[idx + 1].toInt() shl 8))
                    .toShort().toInt()
                val v = (sample * gain).toInt().coerceIn(-32768, 32767)
                out[idx] = (v and 0xff).toByte()
                out[idx + 1] = (v shr 8).toByte()
            }
        }
        return out
    }
}
