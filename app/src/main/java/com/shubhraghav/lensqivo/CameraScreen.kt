package com.shubhraghav.lensqivo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size as AndroidSize
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LifecycleOwner
import com.shubhraghav.lensqivo.core.CaptureGate.Phase
import com.shubhraghav.lensqivo.core.Look
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.pow

private val Ink = Color(0xFF101110)
private val Paper = Color(0xFFF3F0E8)
private val Champagne = Color(0xFFD9C6A3)
private val Muted = Color(0xFFBABAB4)
private val Panel = Color(0xFF232422)

@Composable
fun LensqivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Champagne, onPrimary = Ink,
        background = Ink, onBackground = Paper, surface = Panel, onSurface = Paper,
        secondary = Champagne, onSecondary = Ink)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
    }
}

@Composable
fun CameraPermissionScreen(request: () -> Unit, settings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("LENSQIVO", color = Champagne, letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold)
        Text("A little light.\nA different feeling.", style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Serif, color = Paper)
        Text("An everyday camera with considered color and a quiet place to compose.", color = Muted)
        Text("Camera access lets you compose and capture. Microphone access is optional and requested only when you enable sound. Your captures stay on your phone.", color = Paper)
        Button(onClick = request, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open the camera") }
        OutlinedButton(onClick = settings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Android permission settings") }
        Text("No account. No ads. No network permission.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

internal enum class CreativeTool(val label: String) { LOOKS("Looks"), BEAUTY("Beauty"), ADJUST("Adjust") }

@Composable
fun CameraScreen(session: CameraSession, owner: LifecycleOwner, audio: Boolean,
    setAudio: (Boolean) -> Unit, openSaved: () -> Unit, openLibrary: () -> Unit) {
    val state = session.state
    var settings by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf<CreativeTool?>(null) }
    var grid by remember { mutableStateOf(session.preferences.grid) }
    val snackbar = remember { SnackbarHostState() }
    val idle = state.phase == Phase.IDLE && state.countdown == 0
    val editable = idle && !session.comparing
    DisposableEffect(owner, session) { onDispose { session.detach(owner) } }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); session.clearNotice() } }
    LaunchedEffect(state.phase, state.countdown) { if (!idle) tool = null }
    BackHandler(settings || tool != null) { if (settings) settings = false else tool = null }
    Box(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        CameraFrame(
            ratio = if (state.mode == CaptureMode.PHOTO) 3f / 4f else 9f / 16f,
            toolbar = {
                CameraToolbar(state, session.comparing, session::setFlash, session::setTimer) { settings = true }
            }, preview = { Viewfinder(session, owner, grid) },
            dock = { wide ->
                CameraDock(state, session.comparing, audio, tool, wide,
                    session::changeMode, { session.capture(audio) }, session::flip, session::compare,
                    if (state.saved != null) openSaved else openLibrary,
                    { selected -> tool = if (tool == selected) null else selected })
            }, creativePanel = if (tool == null) null else ({ CreativePanel(session, checkNotNull(tool), editable, { tool = null }) })
        )
        if (settings) Dialog(onDismissRequest = { settings = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CameraSettings(session, audio, setAudio, grid, { grid = it; session.preferences.grid = it }, editable, { settings = false })
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun CameraToolbar(state: CameraUiState, comparing: Boolean, flash: (Int) -> Unit,
    timer: (Int) -> Unit, settings: () -> Unit) {
    val idle = state.phase == Phase.IDLE && state.countdown == 0
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CameraIconButton(Glyph.FLASH, "Flash: ${flashLabel(state.flash)}", idle && !comparing && state.hasFlash && state.mode == CaptureMode.PHOTO,
            state.flash != ImageCapture.FLASH_MODE_OFF, if (state.flash == ImageCapture.FLASH_MODE_AUTO) "A" else null) { flash(nextFlash(state.flash)) }
        CameraIconButton(Glyph.TIMER, "Timer: ${timerLabel(state.timer)}", idle && !comparing && state.mode == CaptureMode.PHOTO, state.timer > 0,
            if (state.timer > 0) "${state.timer}" else null) { timer(nextTimer(state.timer)) }
        Spacer(Modifier.weight(1f))
        Text(if (state.mode == CaptureMode.PHOTO) "3:4" else "9:16", color = Muted,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
        CameraIconButton(Glyph.SETTINGS, "Camera settings", idle, action = settings)
    }
}

/** Overlay panels never participate in the viewfinder's measurement. */
@Composable
internal fun CameraFrame(ratio: Float, toolbar: @Composable () -> Unit,
    dock: @Composable (Boolean) -> Unit, preview: @Composable () -> Unit,
    creativePanel: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxSize().testTag("camera-frame")) {
        toolbar()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val availableWidth = maxWidth
            val wide = availableWidth > maxHeight && availableWidth >= 480.dp
            val displayRatio = if (wide) 1f / ratio else ratio
            val previewContent: @Composable () -> Unit = {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val viewportHeight = minOf(maxHeight, maxWidth / displayRatio)
                    val panelHeight = minOf(viewportHeight * .56f, 240.dp)
                    Box(Modifier.aspectRatio(displayRatio,
                        matchHeightConstraintsFirst = wide).background(Color.Black).testTag("viewfinder")) { preview() }
                    creativePanel?.let { panel ->
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = panelHeight)) { panel() }
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { previewContent() }
                Box(Modifier.width(if (availableWidth < 700.dp) 208.dp else 224.dp).fillMaxHeight()) { dock(true) }
            } else Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) { previewContent() }
                dock(false)
            }
        }
    }
}

@Composable
private fun Viewfinder(session: CameraSession, owner: LifecycleOwner, grid: Boolean) {
    val state = session.state
    var focus by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focus) { if (focus != null) { delay(1400); focus = null } }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (session.state.ready && session.state.phase == Phase.IDLE && session.state.countdown == 0) {
                            focus = Offset(event.x, event.y)
                            session.focus(event.x, event.y)
                        }
                        performClick()
                    }
                    true
                }
                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) session.layoutChanged()
                }
                session.attach(owner, this)
            }
        }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            if (grid) for (i in 1..2) {
                drawLine(Paper.copy(alpha = .32f), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.dp.toPx())
                drawLine(Paper.copy(alpha = .32f), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.dp.toPx())
            }
            focus?.let { point ->
                val radius = 27.dp.toPx()
                val center = Offset(point.x.coerceIn(0f, size.width), point.y.coerceIn(0f, size.height))
                drawRoundRect(Champagne, topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2),
                    cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
        }
        val status = if (state.countdown > 0) "Self-timer · tap to cancel" else when (state.phase) {
            Phase.STARTING -> "Starting recording…"
            Phase.RECORDING -> "● REC  ${state.seconds / 60}:${(state.seconds % 60).toString().padStart(2, '0')}"
            Phase.FINALIZING -> "Saving video…"
            Phase.PHOTO -> "Saving photo…"
            else -> if (session.comparing) "ORIGINAL · capture paused" else if (state.originalOnly) "ORIGINAL ONLY" else session.look.getLabel()
        }
        Text(status, color = Paper, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(6.dp))
                .background(Ink.copy(alpha = .78f)).padding(horizontal = 10.dp, vertical = 7.dp)
                .semantics { liveRegion = LiveRegionMode.Polite })
        if (!state.ready && state.error == null) CircularProgressIndicator(color = Champagne, modifier = Modifier.align(Alignment.Center))
        if (state.countdown > 0) TextButton(onClick = session::stop, modifier = Modifier.align(Alignment.Center)
            .semantics { contentDescription = "Cancel timer, ${state.countdown} seconds remaining"; liveRegion = LiveRegionMode.Polite }) {
            Text("${state.countdown}", color = Paper, style = MaterialTheme.typography.displayLarge)
        }
        state.error?.let { message ->
            Column(Modifier.fillMaxSize().background(Ink.copy(alpha = .95f)).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                Text(message, color = Paper, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Button(onClick = { session.retry() }, enabled = state.phase == Phase.IDLE) { Text("Retry camera") }
                if (!state.originalOnly) OutlinedButton(onClick = { session.retry(true) }, enabled = state.phase == Phase.IDLE) { Text("Use original only") }
            }
        }
    }
}

@Composable
internal fun CameraDock(state: CameraUiState, comparing: Boolean, audio: Boolean, tool: CreativeTool?, wide: Boolean,
    mode: (CaptureMode) -> Unit, capture: () -> Unit, flip: () -> Unit, compare: () -> Unit, gallery: () -> Unit,
    selectTool: (CreativeTool) -> Unit) {
    val idle = state.phase == Phase.IDLE && state.countdown == 0
    Column(Modifier.fillMaxWidth().then(if (wide) Modifier.fillMaxHeight() else Modifier)
        .verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = if (wide) Arrangement.Center else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
            CaptureMode.entries.forEach { item ->
                Text(if (item == CaptureMode.PHOTO) "PHOTO" else "VIDEO", color = if (state.mode == item) Champagne else Muted,
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp,
                    modifier = Modifier.heightIn(min = 48.dp).selectable(state.mode == item, idle && !comparing, Role.Tab) { mode(item) }
                        .padding(horizontal = 16.dp, vertical = 16.dp))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 92.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            GalleryButton(state.saved, idle, gallery)
            CaptureButton(state.phase, state.mode, state.ready && !comparing && state.countdown == 0, capture)
            CameraIconButton(Glyph.FLIP, "Switch camera", idle && state.canFlip && !comparing, action = flip)
        }
        if (comparing) TextButton(onClick = compare, enabled = idle, modifier = Modifier.heightIn(min = 48.dp)) { Text("Return to your look") }
        else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
            CreativeTool.entries.forEach { item ->
                if ((item == CreativeTool.ADJUST && (state.maxZoom > state.minZoom || state.maxEv > state.minEv)) ||
                    (item != CreativeTool.ADJUST && !state.originalOnly)) {
                    TextButton(onClick = { selectTool(item) }, enabled = idle,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            stateDescription = if (tool == item) "Expanded" else "Collapsed"
                        }) { Text(item.label, color = if (tool == item) Champagne else Muted) }
                }
            }
        }
        if (state.mode == CaptureMode.VIDEO) Text(if (state.audioInterrupted) "Sound interrupted" else if (audio) "Sound on" else "Silent video",
            color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun GalleryButton(saved: SavedMedia?, enabled: Boolean, action: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(null, saved?.uri) {
        value = null
        if (saved != null) value = withContext(Dispatchers.IO) {
            try { context.contentResolver.loadThumbnail(saved.uri, AndroidSize(128, 128), null) }
            catch (_: Exception) { null }
        }
    }
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled, role = Role.Button, onClick = action)
        .semantics { contentDescription = if (saved == null) "Open library" else "Open last capture" }, contentAlignment = Alignment.Center) {
        val bitmap = thumbnail
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
        else CameraGlyph(Glyph.GALLERY, if (enabled) Paper else Muted.copy(alpha = .35f))
    }
}

@Composable
internal fun CreativePanel(session: CameraSession, tool: CreativeTool, editable: Boolean, close: () -> Unit) {
    val state = session.state
    Column(Modifier.fillMaxWidth().testTag("creative-panel").clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        .background(Panel.copy(alpha = .98f)).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        .padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tool.label, style = MaterialTheme.typography.titleMedium)
                if (tool == CreativeTool.LOOKS) Text("Sample", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            if (tool != CreativeTool.ADJUST) TextButton(onClick = session::compare,
                enabled = state.phase == Phase.IDLE && state.countdown == 0,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = if (session.comparing) "Return to your look" else "Compare original"
                    stateDescription = if (session.comparing) "Showing original, capture paused" else "Showing creative settings"
                }) { Text(if (session.comparing) "After" else "Before") }
            CameraIconButton(Glyph.CLOSE, "Close ${tool.label.lowercase(Locale.ROOT)}", action = close)
        }
        when (tool) {
            CreativeTool.LOOKS -> {
                LookOptions(session.look, editable, session.strength, session::selectLook)
                ValueSlider("Look intensity", session.strength, editable && session.look != Look.ORIGINAL,
                    session::changeStrength, session::persistStrength)
            }
            CreativeTool.BEAUTY -> {
                Text("Skin-color selective; similar-colored surfaces may also soften.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
                ValueSlider("Natural smooth", session.smooth, editable, session::changeSmooth, session::persistBeauty)
                ValueSlider("Soft glow", session.glow, editable, session::changeGlow, session::persistBeauty)
            }
            CreativeTool.ADJUST -> {
                if (state.maxEv > state.minEv) {
                    Text("Light  ${String.format(Locale.ROOT, "%+.1f EV", state.ev * state.evStep)}", color = Paper)
                    Slider(state.ev.toFloat(), { session.expose(it.roundToInt()) }, valueRange = state.minEv.toFloat()..state.maxEv.toFloat(),
                        steps = (state.maxEv - state.minEv - 1).coerceAtLeast(0), enabled = editable && state.ready,
                        modifier = Modifier.semantics { contentDescription = "Exposure compensation" })
                }
                if (state.maxZoom > state.minZoom) {
                    Text("Zoom  ${String.format(Locale.ROOT, "%.1f×", state.zoom)}", color = Paper)
                    Slider(state.zoom.coerceIn(state.minZoom, state.maxZoom), session::zoom,
                        valueRange = state.minZoom..state.maxZoom, enabled = editable && state.ready,
                        modifier = Modifier.semantics { contentDescription = "Camera zoom" })
                }
            }
        }
    }
}

@Composable
private fun ValueSlider(label: String, value: Float, enabled: Boolean, change: (Float) -> Unit, finish: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(if (value == 0f) "Off" else "${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = Champagne)
    }
    Slider(value, change, onValueChangeFinished = finish, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label })
}

@Composable
internal fun CameraSettings(session: CameraSession, audio: Boolean, setAudio: (Boolean) -> Unit, grid: Boolean,
    setGrid: (Boolean) -> Unit, enabled: Boolean, back: () -> Unit) {
    val state = session.state
    var recipeRevision by remember { mutableIntStateOf(0) }
    SettingsPage(back) {
            SettingsHeading("Photo")
            SettingsChoice("Timer", timerLabel(state.timer), enabled) { session.setTimer(nextTimer(state.timer)) }
            SettingsChoice("Flash", if (state.hasFlash) flashLabel(state.flash) else "Unavailable on this camera", enabled && state.hasFlash) {
                session.setFlash(nextFlash(state.flash))
            }
            SettingToggle("Mirror selfies", "Save the front camera view as you see it", state.mirror, enabled, session::setMirror)
            SettingToggle("Keep original", "Save an unprocessed photo alongside your creative edit", state.keepOriginal, enabled, session::setKeepOriginal)
            SettingsHeading("Video")
            SettingToggle("Record sound", "Optional microphone access · video only", audio, enabled, setAudio)
            Text("Quality is automatic: device-supported Full HD, then HD or SD. Recording stops when the app leaves the foreground; rotation or window changes may end a clip while it saves.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            SettingsHeading("Composition")
            SettingToggle("Composition grid", "A rule-of-thirds guide in the viewfinder", grid, enabled, setGrid)
            SettingsHeading("Creative recipes")
            Text("Three local slots for your look, intensity, smooth and glow.", color = Muted, style = MaterialTheme.typography.bodySmall)
            (1..3).forEach { slot ->
                val hasRecipe = remember(slot, recipeRevision) { session.preferences.hasRecipe(slot) }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(12.dp)
                    .testTag("recipe-card-$slot")) {
                    Text("Recipe ${slot} · ${if (hasRecipe) "Saved" else "Empty"}", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { session.saveRecipe(slot); recipeRevision++ }, enabled = enabled && !state.originalOnly,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save recipe ${slot}${if (hasRecipe) ", replace saved recipe" else ""}" }) {
                            Text(if (hasRecipe) "Replace with current" else "Save current")
                        }
                        TextButton(onClick = { session.loadRecipe(slot) }, enabled = enabled && hasRecipe && !state.originalOnly,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Load recipe ${slot}" }) { Text("Load") }
                    }
                }
            }
            OutlinedButton(onClick = session::resetCreative, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Reset look & beauty") }
            SettingsHeading("Saving")
            Text("Retries originals retained after interrupted saves. Requires free storage.", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = session::recoverPhotos, enabled = enabled && !state.recovering,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (state.recovering) "Recovering originals…" else "Recover unsaved originals")
            }
            SettingsHeading("About & help")
            Text("Private by construction", color = Paper, style = MaterialTheme.typography.titleSmall)
            Text("No account, location, analytics, ads or network permission. Photos go to DCIM/Lensqivo; videos go to Movies/Lensqivo. Android and your gallery may apply their own backup settings.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("Tap the viewfinder to focus. Compare pauses capture until you return to your look. Beauty uses skin-color selection; it does not detect faces. Keep original retains an unprocessed copy of creative photos.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            if (state.originalOnly) OutlinedButton(onClick = { session.retry(false) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Try live looks again") }
            Text("Diagnostics", color = Paper, style = MaterialTheme.typography.titleSmall)
            Text("${if (state.originalOnly) "Original-only fallback" else "Creative processing enabled"}\nCapture stream: ${state.output.ifEmpty { "not connected" }}\nLensqivo · device qualification build",
                color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SettingsPage(back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding().testTag("camera-settings")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CameraIconButton(Glyph.BACK, "Back to camera", action = back)
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingsHeading(label: String) {
    HorizontalDivider(color = Paper.copy(alpha = .1f), modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    Text(label, color = Champagne, style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SettingsChoice(label: String, value: String, enabled: Boolean, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
        .clickable(enabled, role = Role.Button, onClick = action).padding(vertical = 8.dp)
        .semantics(mergeDescendants = true) { stateDescription = value }) {
        Text(label)
        Text(value, color = if (enabled) Champagne else Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SettingToggle(title: String, detail: String, checked: Boolean, enabled: Boolean, action: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = action)
        .semantics { contentDescription = title }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) { Text(title); Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun LookSelector(selected: Look, enabled: Boolean, onSelect: (Look) -> Unit) {
    LookOptions(selected, enabled, .7f, onSelect)
}

@Composable
private fun LookOptions(selected: Look, enabled: Boolean, strength: Float, onSelect: (Look) -> Unit) {
    val context = LocalContext.current
    val samples by produceState<Map<Look, Bitmap>>(emptyMap(), strength) {
        delay(160)
        value = withContext(Dispatchers.IO) {
            val source = context.assets.open("samples/look-reference.jpg").use {
                requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }))
            }
            try {
                val pixels = IntArray(source.width * source.height)
                source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
                Look.values().associateWith { look ->
                    Bitmap.createBitmap(gradeSamplePixels(pixels, look, strength), source.width, source.height, Bitmap.Config.ARGB_8888)
                }
            } finally { source.recycle() }
        }
    }
    LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Look.values(), key = { it.name }) { look ->
            Column(Modifier.width(68.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, if (look == selected) Champagne else Color.Transparent, RoundedCornerShape(10.dp))
                .selectable(look == selected, enabled, Role.RadioButton) { onSelect(look) }
                .semantics { contentDescription = "${look.getLabel()}. ${look.getDescription()}" }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                val bitmap = samples[look]
                if (bitmap != null) Image(bitmap.asImageBitmap(), null,
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(6.dp)).testTag("look-sample-${look.name}"), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxWidth().height(64.dp).background(Panel))
                Text(look.getLabel(), color = if (look == selected) Champagne else Paper,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/** Thumbnails use the same linear color matrix; the sample is never a capture-quality promise. */
internal fun gradeSamplePixels(pixels: IntArray, look: Look, strength: Float): IntArray {
    val matrix = look.matrix(strength)
    val linear = FloatArray(256) { channel ->
        val encoded = channel / 255f
        if (encoded <= .04045f) encoded / 12.92f else ((encoded + .055f) / 1.055f).pow(2.4f)
    }
    fun encode(value: Float): Int {
        val light = value.coerceIn(0f, 1f)
        val encoded = if (light <= .0031308f) light * 12.92f else 1.055f * light.pow(1f / 2.4f) - .055f
        return (encoded * 255).roundToInt().coerceIn(0, 255)
    }
    return IntArray(pixels.size) { index ->
        val pixel = pixels[index]
        val r = linear[(pixel ushr 16) and 255]
        val g = linear[(pixel ushr 8) and 255]
        val b = linear[pixel and 255]
        fun channel(row: Int) = encode(matrix[row] * r + matrix[row + 4] * g + matrix[row + 8] * b + matrix[row + 12])
        (pixel and -0x1000000) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
    }
}

@Composable
fun CaptureButton(phase: Phase, mode: CaptureMode, ready: Boolean, action: () -> Unit) {
    val stopping = phase == Phase.STARTING || phase == Phase.RECORDING
    val enabled = stopping || (phase == Phase.IDLE && ready)
    val label = when (phase) {
        Phase.PHOTO -> "Saving photo"
        Phase.FINALIZING -> "Saving video"
        Phase.STARTING, Phase.RECORDING -> "Stop recording"
        Phase.IDLE -> if (mode == CaptureMode.PHOTO) "Take photo" else "Start recording"
    }
    Box(Modifier.size(84.dp).clip(CircleShape).clickable(enabled, role = Role.Button, onClick = action)
        .semantics { contentDescription = label; stateDescription = phase.name.lowercase(Locale.ROOT) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.dp)) {
            val color = if (enabled) Paper else Muted.copy(alpha = .4f)
            drawCircle(color, style = Stroke(2.dp.toPx()))
            if (stopping) drawRoundRect(Champagne, topLeft = Offset(size.width * .3f, size.height * .3f),
                size = Size(size.width * .4f, size.height * .4f), cornerRadius = CornerRadius(5.dp.toPx()))
            else drawCircle(if (mode == CaptureMode.VIDEO) Color(0xFFD77368) else color, radius = size.minDimension / 2 - 7.dp.toPx())
        }
        if (phase == Phase.PHOTO || phase == Phase.FINALIZING) CircularProgressIndicator(color = Ink, modifier = Modifier.size(28.dp))
    }
}

private enum class Glyph { FLASH, TIMER, SETTINGS, FLIP, GALLERY, CLOSE, BACK }

@Composable
private fun CameraIconButton(glyph: Glyph, label: String, enabled: Boolean = true, selected: Boolean = false,
    badge: String? = null, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(48.dp).semantics {
        contentDescription = label
        if (glyph == Glyph.FLASH || glyph == Glyph.TIMER) stateDescription = if (selected) "On" else "Off"
    }) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            CameraGlyph(glyph, if (!enabled) Muted.copy(alpha = .35f) else if (selected) Champagne else Paper)
            if (badge != null) Text(badge, color = Champagne, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd)
                .background(Ink).clearAndSetSemantics {})
        }
    }
}

@Composable
private fun CameraGlyph(glyph: Glyph, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(1.6f * s)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x * s, y * s), Offset(x2 * s, y2 * s), 1.6f * s)
        fun path(vararg points: Pair<Float, Float>) = Path().apply {
            points.forEachIndexed { index, point -> if (index == 0) moveTo(point.first * s, point.second * s) else lineTo(point.first * s, point.second * s) }
        }
        when (glyph) {
            Glyph.FLASH -> drawPath(path(13f to 2f, 5f to 13f, 11f to 13f, 10f to 22f, 19f to 10f, 13f to 10f, 13f to 2f), color, style = stroke)
            Glyph.TIMER -> { drawCircle(color, 8f * s, Offset(12f * s, 14f * s), style = stroke); line(9f, 2f, 15f, 2f); line(12f, 6f, 12f, 2f); line(12f, 14f, 12f, 9f); line(18f, 6f, 20f, 4f) }
            Glyph.SETTINGS -> { drawCircle(color, 7f * s, style = stroke); drawCircle(color, 2.5f * s, style = stroke); for (i in 0..7) {
                val a = i * Math.PI / 4; drawLine(color, Offset((12 + kotlin.math.cos(a) * 8).toFloat() * s, (12 + kotlin.math.sin(a) * 8).toFloat() * s), Offset((12 + kotlin.math.cos(a) * 10).toFloat() * s, (12 + kotlin.math.sin(a) * 10).toFloat() * s), 2f * s)
            } }
            Glyph.FLIP -> { drawArc(color, 30f, 140f, false, Offset(3f * s, 3f * s), Size(18f * s, 18f * s), style = stroke); drawArc(color, 210f, 140f, false, Offset(3f * s, 3f * s), Size(18f * s, 18f * s), style = stroke); drawPath(path(2f to 12f, 3f to 17f, 8f to 16f), color, style = stroke); drawPath(path(22f to 12f, 21f to 7f, 16f to 8f), color, style = stroke) }
            Glyph.GALLERY -> { drawRoundRect(color, Offset(3f * s, 3f * s), Size(18f * s, 18f * s), CornerRadius(3f * s), style = stroke); drawPath(path(4f to 18f, 9f to 12f, 13f to 16f, 16f to 12f, 21f to 18f), color, style = stroke); drawCircle(color, 1.6f * s, Offset(16f * s, 8f * s)) }
            Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(6f, 18f, 18f, 6f) }
            Glyph.BACK -> { drawPath(path(14f to 5f, 7f to 12f, 14f to 19f), color, style = stroke); line(7f, 12f, 21f, 12f) }
        }
    }
}

private fun flashLabel(mode: Int) = when (mode) { ImageCapture.FLASH_MODE_ON -> "On"; ImageCapture.FLASH_MODE_AUTO -> "Auto"; else -> "Off" }
private fun nextFlash(mode: Int) = when (mode) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO; ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON; else -> ImageCapture.FLASH_MODE_OFF }
private fun timerLabel(seconds: Int) = if (seconds == 0) "Off" else "$seconds seconds"
private fun nextTimer(seconds: Int) = when (seconds) { 0 -> 3; 3 -> 10; else -> 0 }
