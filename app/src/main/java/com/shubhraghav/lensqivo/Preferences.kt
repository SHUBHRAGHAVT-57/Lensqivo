package com.shubhraghav.lensqivo

import android.content.Context
import androidx.core.content.edit
import androidx.camera.core.ImageCapture
import com.shubhraghav.lensqivo.core.Look
import org.json.JSONObject

class Preferences(context: Context) {
    private val values = context.getSharedPreferences("camera", Context.MODE_PRIVATE)
    var look: Look
        get() = Look.values().firstOrNull { it.name == values.getString("look", "ORIGINAL") } ?: Look.ORIGINAL
        set(value) { values.edit { putString("look", value.name) } }
    var strength: Float
        get() = values.getFloat("strength", .7f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .7f
        set(value) { values.edit { putFloat("strength", value.coerceIn(0f, 1f)) } }
    var grid: Boolean
        get() = values.getBoolean("grid", false)
        set(value) { values.edit { putBoolean("grid", value) } }
    var audio: Boolean
        get() = values.getBoolean("audio", false)
        set(value) { values.edit { putBoolean("audio", value) } }
    var smooth: Float
        get() = safeAmount(values.getFloat("smooth", 0f))
        set(value) { values.edit { putFloat("smooth", safeAmount(value)) } }
    var glow: Float
        get() = safeAmount(values.getFloat("glow", 0f))
        set(value) { values.edit { putFloat("glow", safeAmount(value)) } }
    var timer: Int
        get() = values.getInt("timer", 0).takeIf { it in listOf(0, 3, 10) } ?: 0
        set(value) { require(value in listOf(0, 3, 10)); values.edit { putInt("timer", value) } }
    var flash: Int
        get() = values.getInt("flash", ImageCapture.FLASH_MODE_OFF).takeIf { it in 0..2 } ?: ImageCapture.FLASH_MODE_OFF
        set(value) { require(value in 0..2); values.edit { putInt("flash", value) } }
    var mirror: Boolean
        get() = values.getBoolean("mirror", true)
        set(value) { values.edit { putBoolean("mirror", value) } }
    var keepOriginal: Boolean
        get() = values.getBoolean("keepOriginal", true)
        set(value) { values.edit { putBoolean("keepOriginal", value) } }

    fun saveRecipe(slot: Int, look: Look, strength: Float, smooth: Float, glow: Float) {
        require(slot in 1..3)
        val json = JSONObject().put("look", look.name).put("strength", safeAmount(strength))
            .put("smooth", safeAmount(smooth)).put("glow", safeAmount(glow)).toString()
        values.edit { putString("recipe$slot", json) }
    }

    fun recipe(slot: Int): RenderRecipe? {
        require(slot in 1..3)
        return runCatching {
            val json = JSONObject(values.getString("recipe$slot", null) ?: return null)
            RenderRecipe(Look.valueOf(json.getString("look")), safeAmount(json.getDouble("strength").toFloat()),
                safeAmount(json.getDouble("smooth").toFloat()), safeAmount(json.getDouble("glow").toFloat()))
        }.getOrNull()
    }
    fun hasRecipe(slot: Int) = recipe(slot) != null
    private fun safeAmount(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}
