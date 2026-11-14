package com.example.audiostudiopro

/** اقتراح تلقائي: سلم مقترح، ونسبة ثقته، ومسبقة مقترحة. لا يُطبَّق شيء تلقائياً. */
data class Suggestion(
    val scale: ScaleChoice,
    val confidence: Float,
    val presetName: String?,
    val reason: String
)

object AutoSuggest {

    /** يقدّر السلم من منحنى النغمة بالتصويت على النوتات الواضحة. */
    fun suggestScale(points: List<PitchPoint>): Pair<ScaleChoice, Float>? {
        val voiced = points.filter { it.frequencyHz > 0f && it.confidence >= 0.5f }
        if (voiced.size < 5) return null

        // عدّ كل نوتة (رقم الدرجة 0..11) حسب الزمن الواضح
        val hist = FloatArray(12)
        for (p in voiced) {
            val pc = pitchClassOf(p.frequencyHz)
            hist[pc] += p.confidence
        }
        val total = hist.sum().takeIf { it > 0f } ?: return null

        var bestScore = -1f
        var best: ScaleChoice? = null
        for (type in listOf(ScaleType.MAJOR, ScaleType.MINOR)) {
            for (root in 0 until 12) {
                val scale = ScaleChoice(type, root)
                // درجة التوافق: نسبة الوزن الواقع داخل السلم
                var inside = 0f
                for (pc in 0 until 12) {
                    val rel = ((pc - root) % 12 + 12) % 12
                    if (type.intervals.contains(rel)) inside += hist[pc]
                }
                val score = inside / total
                // تفضيل الجذر الذي تكون نوتته الأولى (الدرجة الأولى) ثقيلة فعلاً
                val tonic = hist[root] / total
                val combined = score * 0.8f + tonic * 0.2f
                if (combined > bestScore) {
                    bestScore = combined
                    best = scale
                }
            }
        }
        val chosen = best ?: return null
        return Pair(chosen, bestScore.coerceIn(0f, 1f))
    }

    /** يقترح مسبقة حسب مستوى الضوضاء (dB) ونطاق الصوت (متوسط التردد). */
    fun suggestPreset(noiseDb: Float, avgHz: Float): String {
        return when {
            noiseDb > -40f -> "صوت هادئ"
            avgHz in 0f..165f -> "صوت عميق"
            avgHz > 250f -> "صوت مذيع"
            else -> "صوت مذيع"
        }
    }

    /** الدالة الرئيسية: تُعيد الاقتراح الكامل من بيانات PCM. */
    fun suggest(data: ByteArray, channels: Int, sampleRate: Int): Suggestion? {
        val points = PitchCurve.compute(data, channels, sampleRate)
        val (scale, conf) = suggestScale(points) ?: return null

        val voiced = points.filter { it.frequencyHz > 0f }
        val avgHz = if (voiced.isEmpty()) 0f else voiced.map { it.frequencyHz }.average().toFloat()
        val noiseDb = AutoNoise.noiseDb(data, channels, sampleRate)
        val preset = suggestPreset(noiseDb, avgHz)

        val reason = "متوسط التردد: ${"%.0f".format(avgHz)} Hz، " +
            "الضوضاء: ${"%.0f".format(noiseDb)} dB"
        return Suggestion(scale, conf, preset, reason)
    }

    private fun pitchClassOf(frequencyHz: Float): Int {
        val midi = 69f + 12f * kotlin.math.log2(frequencyHz / 440f)
        return ((Math.round(midi) % 12) + 12) % 12
    }
}
