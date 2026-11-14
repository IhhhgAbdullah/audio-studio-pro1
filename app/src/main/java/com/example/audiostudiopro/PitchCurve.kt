package com.example.audiostudiopro

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/** نقطة واحدة على منحنى النغمة. */
data class PitchPoint(
    val timeSec: Float,
    val frequencyHz: Float,
    val noteName: String,
    val centsOff: Float,
    val confidence: Float
)

object PitchCurve {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * يحسب منحنى النغمة لكل نافذة 50 ملّي ثانية (بدون تداخل).
     * النوافذ الصامتة أو غير الواضحة تُعطى frequencyHz = 0.
     */
    fun compute(data: ByteArray, channels: Int, sampleRate: Int): List<PitchPoint> {
        val windowFrames = sampleRate / 20
        val frameBytes = 2 * channels
        val total = data.size / frameBytes
        val out = ArrayList<PitchPoint>()

        var offset = 0
        while (offset + windowFrames <= total) {
            val time = offset.toFloat() / sampleRate
            val r = PitchDetector.detect(data, channels, sampleRate, offset, windowFrames)
            if (r != null) {
                out.add(PitchPoint(time, r.frequencyHz, r.noteName, r.centsOff, r.confidence))
            } else {
                out.add(PitchPoint(time, 0f, "-", 0f, 0f))
            }
            offset += windowFrames
        }
        return out
    }

    /** يحوّل النص إلى سطور قصيرة للعرض: الزمن، النوتة، الانحراف، والموثوقية. */
    fun toText(points: List<PitchPoint>, maxLines: Int = 60): String {
        val voiced = points.filter { it.frequencyHz > 0f }
        if (voiced.isEmpty()) return "لم تُكشف نغمة واضحة في أي نافذة"

        val sb = StringBuilder()
        sb.append("النوافذ الواضحة: ${voiced.size} من ${points.size}\n")
        val step = maxOf(1, points.size / maxLines)
        var i = 0
        while (i < points.size) {
            val p = points[i]
            if (p.frequencyHz > 0f) {
                sb.append(
                    "%.2fs  %s  %+.0f¢  (%.0f%%)\n".format(
                        p.timeSec, p.noteName, p.centsOff, p.confidence * 100
                    )
                )
            }
            i += step
        }
        return sb.toString()
    }
}
