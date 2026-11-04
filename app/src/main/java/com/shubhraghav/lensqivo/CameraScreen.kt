package com.shubhraghav.lensqivo

import android.view.MotionEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.shubhraghav.lensqivo.core.CaptureGate.Phase
import com.shubhraghav.lensqivo.core.Look
import java.util.Locale
import kotlin.math.roundToInt

private val Ink = Color(0xFF121411)
private val Paper = Color(0xFFF3F0E5)
private val Amber = Color(0xFFF2CB72)
private val Muted = Color(0xFFB9BDB2)
private val Panel = Color(0xFF232720)

@Composable
fun LensqivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Amber, onPrimary = Ink,
        background = Ink, onBackground = Paper, surface = Panel, onSurface = Paper,
        secondary = Amber, onSecondary = Ink), content = content)
}

@Composable
fun CameraPermissionScreen(request: () -> Unit, settings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding().verticalScroll(rememberScrollState())
        .padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("LENSQIVO", color = Amber, letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold)
        Text("A little light.\nA different feeling.", style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Serif, color = Paper)
        Text("An everyday camera with a few considered looks. Made to stay out of the moment.", color = Muted)
        Spacer(Modifier.height(24.dp))
        Text("Camera access lets you compose and capture. Microphone access is optional and requested only when you enable sound. Your captures stay on your phone.", color = Paper)
        Button(onClick = request, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open the camera") }
        OutlinedButton(onClick = settings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Android permission settings") }
        Text("No account. No ads. No network permission.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(session: CameraSession, owner: LifecycleOwner, audio: Boolean,
    setAudio: (Boolean) -> Unit, openSaved: () -> Unit, openLibrary: () -> Unit) {
    val state = session.state
    var settings by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(session.preferences.grid) }
    val snackbar = remember { SnackbarHostState() }
    DisposableEffect(owner, session) { onDispose { session.detach(owner) } }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); session.clearNotice() }
    }
    val idle = state.phase == Phase.IDLE
    Box(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LENSQIVO", color = Paper, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (state.originalOnly) "ORIGINAL ONLY" else "LIVE COLOR", color = Amber,
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                TextButton(onClick = { settings = true }, enabled = idle, modifier = Modifier.heightIn(min = 48.dp)) { Text("Settings") }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight
                val wide = availableWidth >= 600.dp && availableWidth > availableHeight
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Viewfinder(session, owner, grid, true, Modifier.weight(1f).fillMaxHeight())
                        Controls(session, audio, openSaved, openLibrary, Modifier.width((availableWidth * .38f).coerceIn(260.dp, 380.dp)).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Viewfinder(session, owner, grid, false, Modifier.weight(1f).fillMaxWidth())
                        Controls(session, audio, openSaved, openLibrary,
                            Modifier.fillMaxWidth().heightIn(max = availableHeight * .56f))
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (settings) {
        ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = Panel) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Make it yours", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
                SettingToggle("Composition grid", "A quiet rule-of-thirds guide", grid, idle) {
                    grid = it; session.preferences.grid = it
                }
                SettingToggle("Record sound", "Optional microphone access · video only", audio, idle, setAudio)
                Text("Capture", color = Amber, style = MaterialTheme.typography.labelLarge)
                Text("Photos: JPEG · quality preferred\nVideo: device-supported FHD → HD → SD\nCurrent stream: ${state.output.ifEmpty { "not connected" }}", color = Paper)
                Text("Looks are baked into the saved photo or video. Original comparison is also captured as original. Recording stops when Lensqivo leaves the foreground. Rotation or window changes may end a clip while it is saved.", color = Muted)
                if (state.originalOnly) OutlinedButton(onClick = { session.retry(false) }, enabled = idle) { Text("Try live looks again") }
                Text("Private by construction", color = Amber, style = MaterialTheme.typography.labelLarge)
                Text("No account, location, analytics, ads or network permission. Photos go to DCIM/Lensqivo; videos go to Movies/Lensqivo. Android and your chosen gallery may apply their own backup settings.", color = Muted)
                Text("Lensqivo · 0.1.0 · device qualification build", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, enabled: Boolean, action: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) { Text(title); Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = action, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun Viewfinder(session: CameraSession, owner: LifecycleOwner, grid: Boolean, wide: Boolean, modifier: Modifier) {
    val state = session.state
    Box(modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val ratio = if (state.mode == CaptureMode.PHOTO) { if (wide) 4f / 3f else 3f / 4f } else { if (wide) 16f / 9f else 9f / 16f }
        Box(Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = wide).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
            AndroidView(factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) { session.focus(event.x, event.y); performClick() }
                        true
                    }
                    addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) session.layoutChanged()
                    }
                    session.attach(owner, this)
                }
            }, modifier = Modifier.fillMaxSize())
            if (grid) Canvas(Modifier.fillMaxSize()) {
                for (i in 1..2) {
                    drawLine(Paper.copy(alpha = .35f), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.dp.toPx())
                    drawLine(Paper.copy(alpha = .35f), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.dp.toPx())
                }
            }
            val status = when (state.phase) {
                Phase.STARTING -> "Starting recording…"
                Phase.RECORDING -> "REC  ${state.seconds / 60}:${(state.seconds % 60).toString().padStart(2, '0')}"
                Phase.FINALIZING -> "Saving video…"
                Phase.PHOTO -> "Saving photo…"
                else -> if (session.comparing) "ORIGINAL · capture unprocessed" else if (state.originalOnly) "ORIGINAL ONLY" else session.look.getLabel().uppercase(Locale.ROOT)
            }
            Text(status, color = Paper, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(8.dp))
                    .background(Ink.copy(alpha = .85f)).padding(10.dp))
            if (!state.ready && state.error == null) CircularProgressIndicator(color = Amber, modifier = Modifier.align(Alignment.Center))
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
}

@Composable
private fun Controls(session: CameraSession, audio: Boolean, openSaved: () -> Unit, openLibrary: () -> Unit, modifier: Modifier) {
    val state = session.state
    val idle = state.phase == Phase.IDLE
    var adjustments by remember { mutableStateOf(false) }
    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CaptureMode.entries.forEach { mode ->
                    FilterChip(selected = state.mode == mode, onClick = { session.changeMode(mode) }, enabled = idle,
                        label = { Text(if (mode == CaptureMode.PHOTO) "PHOTO" else "VIDEO", letterSpacing = 2.sp) },
                        modifier = Modifier.padding(horizontal = 4.dp).heightIn(min = 48.dp))
                }
            }
            if (!state.originalOnly) {
                LookSelector(session.look, idle && !session.comparing, session::selectLook)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(session.look.getDescription(), color = Muted, style = MaterialTheme.typography.bodySmall)
                        Slider(value = session.strength, onValueChange = session::setStrength, onValueChangeFinished = session::persistStrength,
                            enabled = idle && !session.comparing && session.look != Look.ORIGINAL,
                            modifier = Modifier.semantics { contentDescription = "Look intensity" })
                    }
                    Text("${(session.strength * 100).roundToInt()}%", color = Paper, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp))
                }
                OutlinedButton(onClick = session::compare, enabled = idle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = if (session.comparing) "Showing original" else "Showing selected look" }) {
                    Text(if (session.comparing) "Return to look" else "Compare with original")
                }
            }
            if (state.maxZoom > state.minZoom || state.maxEv > state.minEv) {
                TextButton(onClick = { adjustments = !adjustments }, enabled = idle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = if (adjustments) "Expanded" else "Collapsed" }) {
                    Text(if (adjustments) "Close adjustments" else "Adjust light & zoom")
                }
            }
            if (adjustments && state.maxZoom > state.minZoom) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(String.format(Locale.ROOT, "%.1f×", state.zoom), color = Paper, modifier = Modifier.width(48.dp))
                    Slider(state.zoom.coerceIn(state.minZoom, state.maxZoom), session::zoom,
                        valueRange = state.minZoom..state.maxZoom, enabled = state.ready && idle,
                        modifier = Modifier.semantics { contentDescription = "Camera zoom" })
                }
            }
            if (adjustments && state.maxEv > state.minEv) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(String.format(Locale.ROOT, "%+.1f EV", state.ev * state.evStep), color = Paper,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                    Slider(state.ev.toFloat(), { session.expose(it.roundToInt()) }, valueRange = state.minEv.toFloat()..state.maxEv.toFloat(),
                        steps = (state.maxEv - state.minEv - 1).coerceAtLeast(0), enabled = state.ready && idle,
                        modifier = Modifier.semantics { contentDescription = "Exposure compensation" })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = if (state.saved != null) openSaved else openLibrary, enabled = idle, modifier = Modifier.size(72.dp, 64.dp)) {
                Text(if (state.saved != null) "Last shot" else "Library", color = if (idle) Paper else Muted)
            }
            CaptureButton(state.phase, state.mode, state.ready) { session.capture(audio) }
            TextButton(onClick = session::flip, enabled = idle && state.canFlip, modifier = Modifier.size(72.dp, 64.dp)) { Text("Flip") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.mode == CaptureMode.VIDEO) { if (state.audioInterrupted) "SOUND INTERRUPTED" else if (audio) "SOUND ON" else "SILENT VIDEO" } else "JPEG · ${state.output}",
                color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (state.saved != null) TextButton(onClick = openLibrary, enabled = idle) { Text("Library") }
        }
    }
}

@Composable
fun LookSelector(selected: Look, enabled: Boolean, onSelect: (Look) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(Look.values().toList(), key = { it.name }) { look ->
            Column(Modifier.width(82.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, if (look == selected) Amber else Color.Transparent, RoundedCornerShape(10.dp))
                .selectable(look == selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(look) })
                .semantics(mergeDescendants = true) {}.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val tones = when (look) {
                    Look.ORIGINAL -> listOf(Color(0xFF91A5A2), Color(0xFFCFB58D), Color(0xFF435548))
                    Look.SOL -> listOf(Color(0xFFD4AC77), Color(0xFFE6C7A0), Color(0xFF63704D))
                    Look.DUSK -> listOf(Color(0xFF7B94A5), Color(0xFFBCA7A2), Color(0xFF3F5655))
                    Look.SILVER -> listOf(Color(0xFFBDBCB5), Color(0xFF767972), Color(0xFF343B36))
                }
                Canvas(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(5.dp))) {
                    drawRect(Brush.linearGradient(tones))
                    drawCircle(tones[1], radius = size.height * .3f, center = Offset(size.width * .7f, size.height * .35f))
                    drawRect(tones[2], topLeft = Offset(0f, size.height * .75f), size = Size(size.width, size.height * .25f))
                }
                Text(look.getLabel(), color = if (look == selected) Amber else Paper, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
            }
        }
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
    Box(Modifier.size(84.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = action)
        .semantics { contentDescription = label; stateDescription = phase.name.lowercase(Locale.ROOT) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.dp)) {
            val color = if (enabled) Paper else Muted.copy(alpha = .4f)
            drawCircle(color, style = Stroke(2.dp.toPx()))
            if (stopping) drawRoundRect(Amber, topLeft = Offset(size.width * .3f, size.height * .3f),
                size = Size(size.width * .4f, size.height * .4f), cornerRadius = CornerRadius(5.dp.toPx()))
            else drawCircle(if (mode == CaptureMode.VIDEO) Amber else color, radius = size.minDimension / 2 - 7.dp.toPx())
        }
        if (phase == Phase.PHOTO || phase == Phase.FINALIZING) CircularProgressIndicator(color = Ink, modifier = Modifier.size(28.dp))
    }
}
