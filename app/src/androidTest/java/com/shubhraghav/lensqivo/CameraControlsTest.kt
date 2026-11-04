package com.shubhraghav.lensqivo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shubhraghav.lensqivo.core.CaptureGate.Phase
import com.shubhraghav.lensqivo.core.Look
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CameraControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun photoShutterHasActionAndInvokesCapture() {
        var captures = 0
        compose.setContent { LensqivoTheme { CaptureButton(Phase.IDLE, CaptureMode.PHOTO, true) { captures++ } } }
        compose.onNodeWithContentDescription("Take photo").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, captures) }
    }

    @Test fun finalizingShutterCannotStartAnotherCapture() {
        compose.setContent { LensqivoTheme { CaptureButton(Phase.FINALIZING, CaptureMode.VIDEO, true) {} } }
        compose.onNodeWithContentDescription("Saving video").assertIsNotEnabled()
    }

    @Test fun recordingAndStartingOfferStop() {
        var stops = 0
        compose.setContent { LensqivoTheme { CaptureButton(Phase.STARTING, CaptureMode.VIDEO, true) { stops++ } } }
        compose.onNodeWithContentDescription("Stop recording").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }

    @Test fun looksExposeSelectionAndCanBeLocked() {
        compose.setContent { LensqivoTheme { LookSelector(Look.SOL, false) {} } }
        compose.onNodeWithText("Sol").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Silver").assertIsNotEnabled()
    }
}
