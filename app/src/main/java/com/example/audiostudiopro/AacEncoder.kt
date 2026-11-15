package com.example.audiostudiopro

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** يرمّز ملف WAV (PCM 16-bit) إلى M4A (AAC) عبر MediaCodec وMediaMuxer. */
object AacEncoder {

    /** خيارات الجودة المتاحة بالكيلو بت في الثانية. */
    val BITRATES = intArrayOf(128_000, 192_000, 256_000)

    fun encode(wavFile: File, outFile: File, bitrate: Int): Boolean {
        val h = WavTrimmer.readWavHeader(wavFile) ?: return false
        if (h.bitsPerSample != 16) return false

        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, h.sampleRate, h.channels
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val raf = java.io.RandomAccessFile(wavFile, "r")
            raf.seek(h.dataOffset)
            var remaining = h.dataSize.toLong()
            var inputDone = false
            var presentationUs = 0L
            val info = MediaCodec.BufferInfo()
            val bytesPerUs = h.sampleRate.toDouble() * h.channels * 2 / 1_000_000.0

            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf: ByteBuffer = codec.getInputBuffer(inIndex) ?: continue
                        inBuf.clear()
                        val toRead = minOf(remaining, inBuf.remaining().toLong()).toInt()
                        if (toRead <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, presentationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val chunk = ByteArray(toRead)
                            raf.readFully(chunk)
                            inBuf.put(chunk)
                            codec.queueInputBuffer(inIndex, 0, toRead, presentationUs, 0)
                            presentationUs += (toRead / bytesPerUs).toLong()
                            remaining -= toRead
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && info.size > 0 && muxerStarted &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }
            raf.close()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {}
        }
    }
}
