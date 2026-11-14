package com.example.audiostudiopro

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object Mp3Trimmer {

    private const val TIMEOUT_US = 10_000L

    fun trim(context: Context, inputUri: Uri, startSec: Float, endSec: Float): String? {
        val pcmFile = File(context.cacheDir, "decoded.pcm")
        return try {
            val info = decodeToPcm(context, inputUri, pcmFile) ?: return null

            val bytesPerSecond = info.sampleRate * info.channels * 2
            val blockAlign = info.channels * 2

            val total = pcmFile.length()
            val startByte = ((startSec * bytesPerSecond).toLong() / blockAlign) * blockAlign
            val endByte = ((endSec * bytesPerSecond).toLong() / blockAlign) * blockAlign

            val startB = startByte.coerceIn(0L, total)
            val lengthByte = (endByte - startByte).coerceIn(0L, total - startB)
            if (lengthByte <= 0L) return null

            val outFile = File(
                context.getExternalFilesDir(null),
                "trimmed_${System.currentTimeMillis()}.wav"
            )

            RandomAccessFile(pcmFile, "r").use { raf ->
                FileOutputStream(outFile).use { out ->
                    WavTrimmer.writeWavHeader(
                        out, info.sampleRate, info.channels, 16, lengthByte.toInt()
                    )
                    raf.seek(startB)
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
            outFile.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            pcmFile.delete()
        }
    }

    private data class PcmInfo(val sampleRate: Int, val channels: Int)

    private fun decodeToPcm(context: Context, uri: Uri, pcmFile: File): PcmInfo? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            FileOutputStream(pcmFile).use { out ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inIndex = dec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuf = dec.getInputBuffer(inIndex)!!
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                dec.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                dec.queueInputBuffer(
                                    inIndex, 0, size, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    val outIndex = dec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = dec.outputFormat
                            sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        outIndex >= 0 -> {
                            if (bufferInfo.size > 0) {
                                val outBuf = dec.getOutputBuffer(outIndex)!!
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val bytes = ByteArray(bufferInfo.size)
                                outBuf.get(bytes)
                                out.write(bytes)
                            }
                            dec.releaseOutputBuffer(outIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }
            return PcmInfo(sampleRate, channels)
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) { }
            codec?.release()
            extractor.release()
        }
    }
}
