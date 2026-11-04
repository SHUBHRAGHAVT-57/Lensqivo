package com.shubhraghav.lensqivo

import android.content.Context
import com.shubhraghav.lensqivo.core.Look

class Preferences(context: Context) {
    private val values = context.getSharedPreferences("camera", Context.MODE_PRIVATE)
    var look: Look
        get() = Look.values().firstOrNull { it.name == values.getString("look", "SOL") } ?: Look.SOL
        set(value) { values.edit().putString("look", value.name).apply() }
    var strength: Float
        get() = values.getFloat("strength", .7f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .7f
        set(value) { values.edit().putFloat("strength", value.coerceIn(0f, 1f)).apply() }
    var grid: Boolean
        get() = values.getBoolean("grid", false)
        set(value) { values.edit().putBoolean("grid", value).apply() }
    var audio: Boolean
        get() = values.getBoolean("audio", false)
        set(value) { values.edit().putBoolean("audio", value).apply() }
}
