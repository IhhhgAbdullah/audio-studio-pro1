package com.example.audiostudiopro

import android.content.Context
import org.json.JSONObject

/** مسبقة: مجموعة إعدادات مؤثرات يمكن تطبيقها بضغطة واحدة. */
data class Preset(
    val name: String,
    val echoMix: Float = 0f,
    val echoDelay: Float = 0.3f,
    val echoFeedback: Float = 0.4f,
    val bass: Float = 0f,
    val distortion: Float = 0f,
    val chorus: Float = 0f,
    val reverb: Float = 0f,
    val pitch: Float = 0f,
    val eqLow: Float = 0f,
    val eqMid: Float = 0f,
    val eqHigh: Float = 0f,
    val compressor: Float = 0f,
    val doubler: Float = 0f,
    val enhancer: Float = 0f,
    val speechEq: Float = 0f,
    val leveler: Float = 0f,
    val dereverb: Float = 0f,
    val gate: Float = 0.5f,
    val clean: Boolean = false,
    val builtIn: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("echoMix", echoMix.toDouble())
        put("echoDelay", echoDelay.toDouble())
        put("echoFeedback", echoFeedback.toDouble())
        put("bass", bass.toDouble())
        put("distortion", distortion.toDouble())
        put("chorus", chorus.toDouble())
        put("reverb", reverb.toDouble())
        put("pitch", pitch.toDouble())
        put("eqLow", eqLow.toDouble())
        put("eqMid", eqMid.toDouble())
        put("eqHigh", eqHigh.toDouble())
        put("compressor", compressor.toDouble())
        put("doubler", doubler.toDouble())
        put("enhancer", enhancer.toDouble())
        put("speechEq", speechEq.toDouble())
        put("leveler", leveler.toDouble())
        put("dereverb", dereverb.toDouble())
        put("gate", gate.toDouble())
        put("clean", clean)
    }

    companion object {
        fun fromJson(o: JSONObject): Preset = Preset(
            name = o.getString("name"),
            echoMix = o.optDouble("echoMix", 0.0).toFloat(),
            echoDelay = o.optDouble("echoDelay", 0.3).toFloat(),
            echoFeedback = o.optDouble("echoFeedback", 0.4).toFloat(),
            bass = o.optDouble("bass", 0.0).toFloat(),
            distortion = o.optDouble("distortion", 0.0).toFloat(),
            chorus = o.optDouble("chorus", 0.0).toFloat(),
            reverb = o.optDouble("reverb", 0.0).toFloat(),
            pitch = o.optDouble("pitch", 0.0).toFloat(),
            eqLow = o.optDouble("eqLow", 0.0).toFloat(),
            eqMid = o.optDouble("eqMid", 0.0).toFloat(),
            eqHigh = o.optDouble("eqHigh", 0.0).toFloat(),
            compressor = o.optDouble("compressor", 0.0).toFloat(),
            doubler = o.optDouble("doubler", 0.0).toFloat(),
            enhancer = o.optDouble("enhancer", 0.0).toFloat(),
            speechEq = o.optDouble("speechEq", 0.0).toFloat(),
            leveler = o.optDouble("leveler", 0.0).toFloat(),
            dereverb = o.optDouble("dereverb", 0.0).toFloat(),
            gate = o.optDouble("gate", 0.5).toFloat(),
            clean = o.optBoolean("clean", false)
        )

        val BUILT_IN: List<Preset> = listOf(
            Preset("صوت مذيع", eqLow = 3f, eqHigh = 2f, compressor = 0.6f, enhancer = 0.5f, builtIn = true),
            Preset("صوت قاعة", reverb = 0.6f, echoMix = 0.15f, builtIn = true),
            Preset("صوت روبوت", distortion = 0.4f, echoMix = 0.3f, echoDelay = 0.12f, echoFeedback = 0.5f, builtIn = true),
            Preset("صوت عميق", bass = 0.7f, eqLow = 4f, pitch = -3f, builtIn = true),
            Preset("صوت هادئ", clean = true, gate = 0.7f, compressor = 0.3f, builtIn = true),
            Preset("صوت مزدوج", doubler = 0.8f, chorus = 0.3f, builtIn = true),
            Preset(
                "قاعة مسجد",
                reverb = 0.7f,
                echoMix = 0.2f,
                echoDelay = 0.6f,
                echoFeedback = 0.25f,
                compressor = 0.2f,
                eqMid = -1f,
                eqHigh = -4f,
                builtIn = true
            ),
            Preset(
                "تأثير بودكاست",
                bass = 0.1f,
                enhancer = 0.5f,
                compressor = 0.6f,
                echoMix = 0.08f,
                echoDelay = 0.2f,
                echoFeedback = 0.2f,
                eqLow = 2f,
                eqMid = 2f,
                eqHigh = 1f,
                clean = true,
                gate = 0.5f,
                builtIn = true
            ),
            Preset(
                "مطرب عراقي شعبي",
                bass = 0.28f,
                distortion = 0f,
                chorus = 0.22f,
                reverb = 0.38f,
                doubler = 0.18f,
                compressor = 0.5f,
                enhancer = 0.35f,
                pitch = 0f,
                eqLow = 3f,
                eqMid = 1f,
                eqHigh = 3f,
                speechEq = 0.10f,
                echoDelay = 0.28f,
                echoFeedback = 0.20f,
                echoMix = 0.18f,
                builtIn = true
            ),
            Preset(
                "محاضرة دينية",
                bass = 0.20f,
                distortion = 0f,
                chorus = 0f,
                reverb = 0.12f,
                doubler = 0f,
                compressor = 0.55f,
                enhancer = 0.20f,
                pitch = 0f,
                eqLow = 1f,
                eqMid = 3f,
                eqHigh = 3f,
                speechEq = 0.50f,
                leveler = 0.60f,
                dereverb = 0.20f,
                gate = 0.30f,
                echoDelay = 0.10f,
                echoFeedback = 0.10f,
                echoMix = 0.08f,
                builtIn = true
            ),
            Preset(
                "إنشاد إسلامي",
                bass = 0.32f,
                distortion = 0.08f,
                chorus = 0.15f,
                reverb = 0.38f,
                doubler = 0.22f,
                compressor = 0.65f,
                enhancer = 0.30f,
                pitch = 0f,
                eqLow = 2f,
                eqMid = 3f,
                eqHigh = 3f,
                speechEq = 0.35f,
                leveler = 0.55f,
                dereverb = 0.05f,
                gate = 0.25f,
                echoDelay = 0.19f,
                echoFeedback = 0.25f,
                echoMix = 0.22f,
                builtIn = true
            ),
            Preset(
                "تلاوة قرآنية",
                bass = 0.15f,
                distortion = 0f,
                chorus = 0f,
                reverb = 0.18f,
                doubler = 0f,
                compressor = 0.40f,
                enhancer = 0.15f,
                pitch = 0f,
                eqLow = 1f,
                eqMid = 2f,
                eqHigh = 3f,
                speechEq = 0.35f,
                leveler = 0.45f,
                dereverb = 0.10f,
                gate = 0.20f,
                echoDelay = 0.08f,
                echoFeedback = 0.05f,
                echoMix = 0.05f,
                builtIn = true
            )
        )
    }
}

/** حفظ المسبقات الخاصة داخل ملف JSON في مجلد التطبيق. */
object PresetStore {
    private const val FILE = "user_presets.json"

    fun loadUser(context: Context): MutableList<Preset> {
        val f = java.io.File(context.filesDir, FILE)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = org.json.JSONArray(f.readText())
            MutableList(arr.length()) { Preset.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveUser(context: Context, presets: List<Preset>) {
        val arr = org.json.JSONArray()
        presets.forEach { arr.put(it.toJson()) }
        java.io.File(context.filesDir, FILE).writeText(arr.toString())
    }
} 
