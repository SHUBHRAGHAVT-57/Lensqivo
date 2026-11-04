package com.shubhraghav.lensqivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shubhraghav.lensqivo.core.Look
import org.junit.Assert.*
import org.junit.Test

class PreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun corruptNumericValuesHaveSafeDefaultsAndRecipeIsAtomic() {
        val raw = context.getSharedPreferences("camera", Context.MODE_PRIVATE)
        val before = raw.all
        try {
            raw.edit().clear().putFloat("smooth", Float.NaN).putFloat("glow", Float.POSITIVE_INFINITY)
                .putInt("timer", 7).putInt("flash", 99).commit()
            val preferences = Preferences(context)
            assertEquals(Look.ORIGINAL, preferences.look)
            assertEquals(0f, preferences.smooth, 0f)
            assertEquals(0f, preferences.glow, 0f)
            assertEquals(0, preferences.timer)
            assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_OFF, preferences.flash)
            assertFalse(preferences.hasRecipe(1))
            preferences.saveRecipe(1, Look.PETAL, .6f, .2f, .1f)
            val recipe = requireNotNull(preferences.recipe(1))
            assertEquals(Look.PETAL, recipe.look)
            assertEquals(.6f, recipe.strength, 0f)
            assertEquals(.2f, recipe.smooth, 0f)
            assertEquals(.1f, recipe.glow, 0f)
            raw.edit().putString("recipe1", "{broken").commit()
            assertNull(preferences.recipe(1))
        } finally {
            val editor = raw.edit().clear()
            before.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
            editor.commit()
        }
    }
}
