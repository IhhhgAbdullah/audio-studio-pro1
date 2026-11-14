package com.example.audiostudiopro

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile

class Recorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var recording = false
    private var outFile: File? = null

    /** مستوى الصوت الحالي بين 0 و1، يُحدَّث أثناء التسجيل. */
    @Volatile var level: Float = 0f

    val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    val isRecording: Boolean
        get() = recording

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuf <= 0) return false

        val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, encoding, minBuf * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        outFile = file
        audioRecord = record
        level = 0f
        recording = true
        record.startRecording()

        thread = Thread {
            val buffer = ByteArray(minBuf)
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0)
                raf.write(ByteArray(44))
                var total = 0L
                while (recording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        raf.write(buffer, 0, read)
                        total += read

                        var peak = 0
                        var i = 0
                        while (i + 1 < read) {
                            val s = ((buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8))
                                .toShort().toInt()
                            val a = if (s < 0) -s else s
                            if (a > peak) peak = a
                            i += 2
                        }
                        level = peak / 32768f
                    }
                }
                writeHeader(raf, total)
            }
        }.also { it.start() }

        return true
    }

    fun stop(): File? {
        if (!recording) return null
        recording = false
        thread?.join(2000)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        thread = null
        level = 0f
        return outFile
    }

    private fun writeHeader(raf: RandomAccessFile, dataSize: Long) {
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val channels = 1
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataSize).toInt())
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
        header.putInt(dataSize.toInt())
        raf.seek(0)
        raf.write(header.array())
    }
}
