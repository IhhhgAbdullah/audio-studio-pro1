package com.example.audiostudiopro

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/** نوع السلم الموسيقي المستخدم لتحديد النوتات المسموحة. */
enum class ScaleType(val label: String, val intervals: IntArray) {
    CHROMATIC("كروماتيك", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
    MAJOR("ماجور", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    MINOR("مينور طبيعي", intArrayOf(0, 2, 3, 5, 7, 8, 10))
}

/** السلم المختار: النوع والجذر (0 = C، 1 = C#، ... 11 = B). */
data class ScaleChoice(val type: ScaleType, val root: Int) {
    fun label(): String {
        if (type == ScaleType.CHROMATIC) return ScaleType.CHROMATIC.label
        return "${PitchCorrect.NOTE_NAMES[root]} ${type.label}"
    }
}

object PitchCorrect {

    val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** هل النوتة (رقم MIDI) مسموحة داخل السلم المختار؟ */
    fun isAllowed(midi: Int, scale: ScaleChoice): Boolean {
        val pc = ((midi % 12) + 12) % 12
        val rel = ((pc - scale.root) % 12 + 12) % 12
        return scale.type.intervals.contains(rel)
    }

    /**
     * يعيد أقرب نوتة مسموحة لتردد معيّن، مع الانحراف بالسنت بين التردد الحالي والنوتة المستهدفة.
     * يعيد null إذا كان التردد غير صالح.
     */
    fun targetFor(frequencyHz: Float, scale: ScaleChoice): Pair<Int, Float>? {
        if (frequencyHz <= 0f) return null
        val midiFloat = 69f + 12f * log2(frequencyHz / 440f)
        val base = midiFloat.roundToInt()
        var best = base
        var bestDist = Float.MAX_VALUE
        for (candidate in (base - 6)..(base + 6)) {
            if (!isAllowed(candidate, scale)) continue
            val d = abs(candidate - midiFloat)
            if (d < bestDist) {
                bestDist = d
                best = candidate
            }
        }
        val centsOff = (best - midiFloat) * 100f
        return Pair(best, centsOff)
    }

    /** تحويل رقم MIDI إلى تردد بالهرتز. */
    fun midiToHz(midi: Int): Float =
        (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
}
