package com.example.audiostudiopro

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object WavTrimmer {

    fun trim(context: Context, inputUri: Uri, startSec: Float, endSec: Float): String? {
        return try {
            val tempInput = File(context.cacheDir, "input.wav")
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempInput).use { output -> input.copyTo(output) }
            } ?: return null

            val header = readWavHeader(tempInput) ?: return null
            val bytesPerSecond = header.sampleRate * header.channels * (header.bitsPerSample / 8)
            val blockAlign = header.channels * (header.bitsPerSample / 8)

            val startByte = ((startSec * bytesPerSecond).toLong() / blockAlign) * blockAlign
            val endByte = ((endSec * bytesPerSecond).toLong() / blockAlign) * blockAlign
            val lengthByte = (endByte - startByte).coerceIn(0L, header.dataSize.toLong())

            val outFile = File(context.getExternalFilesDir(null), "trimmed_${System.currentTimeMillis()}.wav")
            RandomAccessFile(tempInput, "r").use { raf ->
                FileOutputStream(outFile).use { out ->
                    writeWavHeader(out, header.sampleRate, header.channels, header.bitsPerSample, lengthByte.toInt())
                    raf.seek(header.dataOffset + startByte)
                    val buffer = ByteArray(8192)
                    var remaining = lengthByte
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            tempInput.delete()
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    data class WavHeader(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSize: Int
    )

    fun readWavHeader(file: File): WavHeader? {
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(12)
            raf.readFully(riff)
            if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") return null

            var sampleRate = 0
            var channels = 0
            var bits = 0
            while (raf.filePointer < raf.length()) {
                val chunkId = ByteArray(4)
                raf.readFully(chunkId)
                val sizeBytes = ByteArray(4)
                raf.readFully(sizeBytes)
                val size = littleEndianInt(sizeBytes)
                val id = String(chunkId)

                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        raf.readFully(fmt)
                        channels = littleEndianShort(fmt, 2)
                        sampleRate = littleEndianInt(fmt, 4)
                        bits = littleEndianShort(fmt, 14)
                    }
                    "data" -> {
                        return WavHeader(sampleRate, channels, bits, raf.filePointer, size)
                    }
                    else -> raf.skipBytes(size)
                }
            }
            return null
        }
    }

    fun writeWavHeader(out: FileOutputStream, sampleRate: Int, channels: Int, bits: Int, dataSize: Int) {
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray())
        header.putInt(dataSize)
        out.write(header.array())
    }

    private fun littleEndianInt(b: ByteArray, offset: Int = 0): Int =
        (b[offset].toInt() and 0xff) or
            ((b[offset + 1].toInt() and 0xff) shl 8) or
            ((b[offset + 2].toInt() and 0xff) shl 16) or
            ((b[offset + 3].toInt() and 0xff) shl 24)

    private fun littleEndianShort(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xff) or ((b[offset + 1].toInt() and 0xff) shl 8)
}
