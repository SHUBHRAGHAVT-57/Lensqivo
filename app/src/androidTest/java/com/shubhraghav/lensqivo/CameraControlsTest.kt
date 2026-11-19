package com.shubhraghav.lensqivo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.shubhraghav.lensqivo.core.CaptureGate.Phase
import com.shubhraghav.lensqivo.core.Look
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

class CameraControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun photoShutterHasActionAndInvokesCapture() {
        var captures = 0
        compose.setContent { LensqivoTheme { CaptureButton(Phase.IDLE, CaptureMode.PHOTO, true) { captures++ } } }
        compose.onNodeWithContentDescription("Take photo").assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, captures) }
    }

    @Test fun finalizingShutterCannotStartAnotherCapture() {
        compose.setContent { LensqivoTheme { CaptureButton(Phase.FINALIZING, CaptureMode.VIDEO, true) {} } }
        compose.onNodeWithContentDescription("Saving video").assertIsNotEnabled()
    }

    @Test fun savingPhotoShutterCannotStartAnotherCapture() {
        compose.setContent { LensqivoTheme { CaptureButton(Phase.PHOTO, CaptureMode.PHOTO, true) {} } }
        compose.onNodeWithContentDescription("Saving photo").assertIsNotEnabled()
    }

    @Test fun recordingAndStartingOfferStop() {
        var stops = 0
        compose.setContent { LensqivoTheme { CaptureButton(Phase.STARTING, CaptureMode.VIDEO, false) { stops++ } } }
        compose.onNodeWithContentDescription("Stop recording").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }

    @Test fun looksExposeSelectionAndCanBeLocked() {
        compose.setContent { LensqivoTheme { LookSelector(Look.SOL, false) {} } }
        compose.onNodeWithText("Sol").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Silver").assertIsNotEnabled()
    }

    @Test fun countdownDisablesShutterAndModeChanges() {
        compose.setContent { LensqivoTheme { TestDock(CameraUiState(ready = true, countdown = 3)) } }
        compose.onNodeWithContentDescription("Take photo").assertIsNotEnabled()
        compose.onNodeWithText("VIDEO").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Switch camera").assertIsNotEnabled()
    }

    @Test fun originalComparisonPausesCaptureAndAlwaysOffersReturn() {
        var returns = 0
        compose.setContent { LensqivoTheme { TestDock(CameraUiState(ready = true), comparing = true, compare = { returns++ }) } }
        compose.onNodeWithContentDescription("Take photo").assertIsNotEnabled()
        compose.onNodeWithText("Return to your look").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, returns) }
    }

    @Test fun unsupportedAdjustmentsAreNotOffered() {
        compose.setContent { LensqivoTheme { TestDock(CameraUiState(ready = true, minZoom = 1f, maxZoom = 1f)) } }
        compose.onNodeWithText("Adjust").assertDoesNotExist()
    }

    @Test fun flashAndTimerReflectAndChangeActualSettings() {
        var state by mutableStateOf(CameraUiState(ready = true, hasFlash = true, flash = ImageCapture.FLASH_MODE_OFF))
        compose.setContent { LensqivoTheme { CameraToolbar(state, false, { state = state.copy(flash = it) }, { state = state.copy(timer = it) }, {}) } }
        compose.onNodeWithContentDescription("Flash: Off").performClick()
        compose.onNodeWithContentDescription("Flash: Auto").assertIsEnabled()
        compose.onNodeWithContentDescription("Timer: Off").performClick()
        compose.onNodeWithContentDescription("Timer: 3 seconds").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Timer: 10 seconds").assertIsEnabled()
        compose.runOnIdle { state = state.copy(mode = CaptureMode.VIDEO) }
        compose.onNodeWithContentDescription("Flash: Auto").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(mode = CaptureMode.PHOTO) }
        compose.onNodeWithContentDescription("Flash: Auto").assertIsEnabled()
        compose.runOnIdle { state = state.copy(hasFlash = false) }
        compose.onNodeWithContentDescription("Flash: Auto").assertIsNotEnabled()
    }

    @Test fun creativeDrawerDoesNotShrinkOrMoveFullWidthPhotoViewport() {
        var tool by mutableStateOf<CreativeTool?>(null)
        var fixtureDensity = 0f
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize().testTag("fixture-host")) {
                val hostDensity = LocalDensity.current
                val fittingDensity = minOf(hostDensity.density, (constraints.maxWidth - 1) / 320f, (constraints.maxHeight - 1) / 680f)
                fixtureDensity = fittingDensity
                CompositionLocalProvider(LocalDensity provides Density(fittingDensity, hostDensity.fontScale)) {
                    LensqivoTheme {
                        Box(Modifier.size(320.dp, 680.dp).testTag("fixture-bounds")) {
                            CameraFrame(3f / 4f, toolbar = { Spacer(Modifier.fillMaxWidth().height(52.dp)) },
                                dock = { TestDock(CameraUiState(ready = true), select = { tool = it }) }, preview = {},
                                creativePanel = if (tool == null) null else ({ Box(Modifier.fillMaxWidth().height(240.dp).background(Color.DarkGray)) }))
                        }
                    }
                }
            }
        }
        val host = compose.onNodeWithTag("fixture-host").fetchSemanticsNode().boundsInRoot
        val fixture = compose.onNodeWithTag("fixture-bounds").fetchSemanticsNode().boundsInRoot
        Log.i("LensqivoLayout", "Drawer fixture: host=$host, fixture=$fixture, density=$fixtureDensity")
        assertEquals("Fixture must retain its requested logical width inside host $host", 320f * fixtureDensity, fixture.width, 1f)
        assertEquals("Fixture must retain its requested logical height inside host $host", 680f * fixtureDensity, fixture.height, 1f)
        assertTrue("Fixture $fixture must fit without clipping inside host $host",
            fixture.left >= host.left && fixture.top >= host.top && fixture.right <= host.right && fixture.bottom <= host.bottom)
        val frame = compose.onNodeWithTag("camera-frame").fetchSemanticsNode().boundsInRoot
        assertEquals("Camera frame must fill the unclipped fixture", fixture, frame)
        val before = compose.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot
        assertEquals(frame.width, before.width, 1f)
        assertEquals(4f / 3f, before.height / before.width, .01f)
        val shutter = compose.onNodeWithContentDescription("Take photo").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Looks").performClick()
        assertEquals(before, compose.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot)
        assertEquals(shutter, compose.onNodeWithContentDescription("Take photo").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun largeTextLandscapeKeepsControlsAndSettingsReachable() {
        var settings by mutableStateOf(false)
        var toggled = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density / 2, 2f)) {
                LensqivoTheme {
                    Box(Modifier.size(568.dp, 320.dp)) {
                        if (!settings) CameraFrame(3f / 4f,
                            toolbar = { CameraToolbar(CameraUiState(ready = true), false, {}, {}, { settings = true }) },
                            dock = { wide -> TestDock(CameraUiState(ready = true, canFlip = true), wide = wide) }, preview = {})
                        else SettingsPage({ settings = false }) {
                            repeat(12) { Text("Camera settings group ${it + 1}") }
                            SettingToggle("Composition grid", "A rule-of-thirds guide", false, true) { toggled = it }
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Take photo").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("Camera settings").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Composition grid").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(toggled) }
        compose.onNodeWithContentDescription("Back to camera").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Take photo").assertIsDisplayed()
    }

    @Test fun settingsToggleLabelsShareOneAccessibleSwitchAndRespectCaptureLock() {
        var checked by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var changes = 0
        compose.setContent {
            LensqivoTheme {
                SettingToggle("Composition grid", "A rule-of-thirds guide", checked, enabled) {
                    checked = it
                    changes++
                }
            }
        }
        compose.onAllNodesWithContentDescription("Composition grid").assertCountEquals(1)
        compose.onNodeWithContentDescription("Composition grid").assertIsOff().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("A rule-of-thirds guide").performClick()
        compose.onNodeWithContentDescription("Composition grid").assertIsOn()
        compose.runOnIdle { assertEquals(1, changes); enabled = false }
        compose.onNodeWithContentDescription("Composition grid").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test fun settingsUseAllThreeRecipeSlotsAndResetAndRecoveryActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var session: CameraSession
        compose.setContent {
            LensqivoTheme {
                val active = remember { CameraSession(context) }
                session = active
                DisposableEffect(active) { onDispose { active.close() } }
                CameraSettings(active, false, {}, false, {}, true, {})
            }
        }
        compose.runOnIdle { session.selectLook(Look.SOL); session.changeSmooth(.25f) }
        (1..3).forEach { slot ->
            // Scroll the vertical card before its nested horizontal action row.
            compose.onNodeWithTag("recipe-card-$slot").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Save recipe $slot", substring = true)
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals("Recipe $slot click must reach the session", "Recipe $slot saved on this phone.", session.state.notice)
                val saved = session.preferences.recipe(slot)
                assertNotNull("Recipe $slot must persist and parse immediately after its click", saved)
                assertEquals("Recipe $slot must save the selected look", Look.SOL, saved?.look)
                assertEquals("Recipe $slot must save the current smooth amount", .25f, saved?.smooth ?: -1f, 0f)
            }
        }
        compose.runOnIdle { (1..3).forEach { assertTrue("Recipe $it must remain saved after saving the other slots", session.preferences.hasRecipe(it)) } }
        compose.onNodeWithText("Reset look & beauty").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Look.ORIGINAL, session.look); assertEquals(0f, session.smooth) }
        compose.onNodeWithTag("recipe-card-3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Load recipe 3").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(Look.SOL, session.look); assertEquals(.25f, session.smooth) }
        compose.waitUntil(10_000) { !session.state.recovering }
        compose.onNodeWithText("Recover unsaved originals").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { !session.state.recovering }
        compose.onNodeWithText("Recover unsaved originals").assertIsEnabled()
    }

    @Test fun sampleGradingPreservesOriginalAndAppliesLinearLook() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF804020.toInt(), 0x8033AA22.toInt())
        assertArrayEquals(pixels, gradeSamplePixels(pixels, Look.ORIGINAL, .7f))
        assertArrayEquals(pixels, gradeSamplePixels(pixels, Look.SOL, 0f))
        val graded = gradeSamplePixels(pixels, Look.SILVER, 1f)
        graded.forEachIndexed { index, pixel ->
            assertEquals(pixels[index] ushr 24, pixel ushr 24)
            assertEquals((pixel ushr 16) and 255, (pixel ushr 8) and 255)
            assertEquals((pixel ushr 8) and 255, pixel and 255)
        }
        assertFalse(pixels.contentEquals(gradeSamplePixels(pixels, Look.SOL, .7f)))
    }

    @Test fun defaultTextHasReadableRenderedContrastOnDarkPanel() {
        compose.setContent {
            CompositionLocalProvider(LocalContentColor provides Color.Black) {
                LensqivoTheme {
                    Box(Modifier.size(160.dp, 64.dp).background(MaterialTheme.colorScheme.surface).testTag("contrast-panel"),
                        contentAlignment = Alignment.Center) {
                        Text("Looks", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        val bitmap = compose.onNodeWithTag("contrast-panel").captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val readableGlyphPixels = pixels.count { ColorUtils.calculateContrast(it, 0xFF232422.toInt()) >= 4.5 }
        assertTrue("Default Text must paint bright glyphs with at least 4.5:1 contrast on the dark panel; found $readableGlyphPixels pixels",
            readableGlyphPixels >= 20)
    }

    /** Native Compose screenshots of production chrome with a clearly marked, public-safe sample. */
    @Test fun nativeCameraAndLooksScreenshots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reference = context.assets.open("samples/look-reference.jpg").use { requireNotNull(BitmapFactory.decodeStream(it)) }
        var tool by mutableStateOf<CreativeTool?>(null)
        var maxPanelHeightPx = 0f
        val state = CameraUiState(ready = true, hasFlash = true, canFlip = true, minZoom = 1f, maxZoom = 4f, minEv = -3, maxEv = 3)
        compose.setContent {
            LensqivoTheme {
                maxPanelHeightPx = with(LocalDensity.current) { 240.dp.toPx() }
                val session = remember { CameraSession(context).apply { resetCreative(); selectLook(Look.SOL) } }
                DisposableEffect(session) { onDispose { session.close() } }
                Box(Modifier.fillMaxSize().background(Color(0xFF101110))) {
                    CameraFrame(3f / 4f,
                        toolbar = { CameraToolbar(state, false, {}, {}, {}) },
                        dock = { wide -> TestDock(state, wide = wide, select = { tool = it }) },
                        preview = {
                            Box(Modifier.fillMaxSize()) {
                                Image(reference.asImageBitmap(), "Sample scene", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Text("Sample · Sol", color = Color.White, modifier = Modifier.align(Alignment.TopStart)
                                    .padding(12.dp).background(Color.Black.copy(alpha = .65f)).padding(8.dp))
                            }
                        }, creativePanel = if (tool == null) null else ({ CreativePanel(session, checkNotNull(tool), true) { tool = null } }))
                }
            }
        }
        compose.waitForIdle()
        val viewport = compose.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithContentDescription("Take photo").fetchSemanticsNode().boundsInRoot
        logScreenshot("camera")
        compose.onNodeWithText("Looks").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("look-sample-SOL", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Look intensity").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compare original").assertIsDisplayed()
        val looksPanel = compose.onNodeWithTag("creative-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Creative drawer must leave the scene visible", looksPanel.height <= viewport.height * .6f + 1f)
        assertTrue("Creative drawer must stay within its compact height cap", looksPanel.height <= maxPanelHeightPx + 1f)
        assertEquals("Opening looks must preserve viewport geometry", viewport, compose.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot)
        assertEquals("Opening looks must preserve shutter geometry", shutter, compose.onNodeWithContentDescription("Take photo").fetchSemanticsNode().boundsInRoot)
        logScreenshot("looks")
        compose.onNodeWithContentDescription("Close looks").assertIsDisplayed().performClick()
        compose.onNodeWithText("Beauty").performClick()
        compose.onNodeWithContentDescription("Natural smooth").assertIsDisplayed()
        compose.onNodeWithContentDescription("Soft glow").assertIsDisplayed()
        val beautyPanel = compose.onNodeWithTag("creative-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Beauty drawer must leave the scene visible", beautyPanel.height <= viewport.height * .6f + 1f)
        assertTrue("Beauty drawer must stay within its compact height cap", beautyPanel.height <= maxPanelHeightPx + 1f)
        logScreenshot("beauty")
    }

    private fun logScreenshot(name: String) {
        val original = compose.onRoot().captureToImage().asAndroidBitmap()
        val width = original.width.coerceAtMost(400)
        val bitmap = Bitmap.createScaledBitmap(original, width, original.height * width / original.width, true)
        val bytes = ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output))
            output.toByteArray()
        }
        val chunks = Base64.encodeToString(bytes, Base64.NO_WRAP).chunked(2800)
        chunks.forEachIndexed { index, data -> Log.i("LensqivoScreenshot", "LENSQIVO_SCREENSHOT:$name:${index + 1}/${chunks.size}:$data") }
    }
}

@Composable
private fun TestDock(state: CameraUiState, comparing: Boolean = false, wide: Boolean = false,
    compare: () -> Unit = {}, select: (CreativeTool) -> Unit = {}) {
    CameraDock(state, comparing, false, null, wide, {}, {}, {}, compare, {}, select)
}
