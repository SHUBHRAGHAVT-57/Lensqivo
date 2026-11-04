package com.shubhraghav.lensqivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.shubhraghav.lensqivo.core.Look
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class LookRecoveryTest {
    @Test fun leavingOriginalOnlyRestoresTheSelectedRenderingTransform() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val preferences = Preferences(context)
            val previousLook = preferences.look
            val session = CameraSession(context)
            try {
                session.selectLook(Look.SOL)
                session.changeStrength(.7f)
                val expected = Look.SOL.matrix(.7f)
                assertArrayEquals(expected, session.renderMatrix, .00001f)
                session.retry(true)
                assertArrayEquals(Look.ORIGINAL.matrix(0f), session.renderMatrix, .00001f)
                session.retry(false)
                assertArrayEquals(expected, session.renderMatrix, .00001f)
            } finally {
                session.close()
                preferences.look = previousLook
            }
        }
    }
}
