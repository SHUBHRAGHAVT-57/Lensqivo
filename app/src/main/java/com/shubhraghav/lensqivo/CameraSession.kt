package com.shubhraghav.lensqivo

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.media3.effect.Media3Effect
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.AudioStats
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.shubhraghav.lensqivo.core.CaptureGate
import com.shubhraghav.lensqivo.core.CaptureGate.Phase
import com.shubhraghav.lensqivo.core.Look
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class CaptureMode { PHOTO, VIDEO }
data class SavedMedia(val uri: Uri, val mime: String)
data class CameraUiState(
    val mode: CaptureMode = CaptureMode.PHOTO,
    val phase: Phase = Phase.IDLE,
    val ready: Boolean = false,
    val front: Boolean = false,
    val canFlip: Boolean = false,
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val ev: Int = 0,
    val minEv: Int = 0,
    val maxEv: Int = 0,
    val evStep: Float = 0f,
    val seconds: Long = 0,
    val output: String = "",
    val error: String? = null,
    val notice: String? = null,
    val saved: SavedMedia? = null,
    val originalOnly: Boolean = false,
    val audioInterrupted: Boolean = false,
    val hasFlash: Boolean = false,
    val flash: Int = ImageCapture.FLASH_MODE_OFF,
    val countdown: Int = 0,
    val timer: Int = 0,
    val mirror: Boolean = true,
    val keepOriginal: Boolean = true,
    val recovering: Boolean = false,
)

/** Main-thread-owned CameraX session. Recording ownership ends only at Finalize. */
@androidx.annotation.OptIn(UnstableApi::class)
class CameraSession(private val context: Context) : AutoCloseable {
    val preferences = Preferences(context)
    var state by mutableStateOf(CameraUiState(timer = preferences.timer,
        mirror = preferences.mirror, keepOriginal = preferences.keepOriginal))
        private set
    var look by mutableStateOf(preferences.look)
        private set
    var strength by mutableFloatStateOf(preferences.strength)
        private set
    var comparing by mutableStateOf(false)
        private set
    var smooth by mutableFloatStateOf(preferences.smooth)
        private set
    var glow by mutableFloatStateOf(preferences.glow)
        private set
    private val gate = CaptureGate()
    private val executor = ContextCompat.getMainExecutor(context)
    private val render = AtomicReference(RenderRecipe(look, strength, smooth, glow))
    internal val renderMatrix: FloatArray get() = render.get().let { it.look.matrix(it.strength) }
    private val color = CreativeEffect { render.get() }
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "lensqivo-photo") }
    private val photos = PhotoStore(context)
    private val timerHandler = Handler(Looper.getMainLooper())
    private var countdownId = 0L
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var owner: LifecycleOwner? = null
    private var view: PreviewView? = null
    private var photo: ImageCapture? = null
    private var video: VideoCapture<Recorder>? = null
    private var effect: Media3Effect? = null
    private var recording: Recording? = null
    private var useCases = emptyList<UseCase>()
    private var generation = 0
    private var closed = false
    private var needsBind = true
    private var loadingProvider = false
    private val observer = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { stop() }
        override fun onStart(owner: LifecycleOwner) {
            if (needsBind && gate.isIdle()) bind()
        }
    }

    init { loadProvider(); recoverPhotos() }

    private fun loadProvider() {
        if (loadingProvider || closed) return
        loadingProvider = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            loadingProvider = false
            if (!closed) {
                try {
                    provider = future.get()
                    bind()
                } catch (_: Exception) {
                    state = state.copy(error = "Camera could not start. Check camera access, then retry.")
                }
            }
        }, executor)
    }

    fun attach(lifecycleOwner: LifecycleOwner, preview: PreviewView) {
        if (closed) return
        removeObservers()
        owner = lifecycleOwner
        view = preview
        lifecycleOwner.lifecycle.addObserver(observer)
        needsBind = true
        if (!gate.isIdle()) stop() else bind()
    }

    fun detach(lifecycleOwner: LifecycleOwner) {
        if (owner !== lifecycleOwner) return
        stop()
        removeObservers()
        owner = null
        view = null
        needsBind = true
        if (gate.isIdle()) releaseBinding()
    }

    fun layoutChanged() {
        if (gate.isIdle()) bind() else needsBind = true
    }

    fun retry(originalOnly: Boolean = state.originalOnly) {
        if (!gate.isIdle() || closed) return
        state = state.copy(originalOnly = originalOnly, error = null)
        comparing = false
        updateMatrix()
        if (provider == null) loadProvider() else bind()
    }

    @SuppressLint("MissingPermission")
    private fun bind() {
        val currentOwner = owner ?: return
        val previewView = view ?: return
        val currentProvider = provider ?: return
        if (closed || !gate.isIdle() || previewView.width == 0 || previewView.height == 0) return
        if (!currentOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            state = state.copy(ready = false, error = "Camera access is off. Enable it in Android settings.")
            return
        }
        releaseBinding()
        val token = ++generation
        state = state.copy(ready = false, error = null, zoom = 1f, ev = 0, output = "")
        try {
            val rear = currentProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val front = currentProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            val useFront = if (state.front) front else !rear && front
            check(rear || front)
            val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val preview = Preview.Builder().setTargetRotation(rotation).build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val builder = UseCaseGroup.Builder()
                .setViewPort(requireNotNull(previewView.viewPort))
                .addUseCase(preview)
            if (state.mode == CaptureMode.PHOTO) {
                photo = ImageCapture.Builder()
                    .setTargetRotation(rotation)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(97).setFlashMode(preferences.flash).build()
                builder.addUseCase(requireNotNull(photo))
                useCases = listOf(preview, requireNotNull(photo))
            } else {
                val recorder = Recorder.Builder().setQualitySelector(
                    QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                ).build()
                video = VideoCapture.Builder(recorder).setTargetRotation(rotation)
                    .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY).build()
                builder.addUseCase(requireNotNull(video))
                useCases = listOf(preview, requireNotNull(video))
            }
            if (!state.originalOnly) {
                effect = Media3Effect(context,
                    if (state.mode == CaptureMode.PHOTO) CameraEffect.PREVIEW
                    else CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
                    executor) {
                    if (!closed && generation == token) {
                        state = state.copy(ready = false,
                            error = "Live looks stopped on this device. Retry, or use original-only capture.")
                        stop()
                    }
                }.also { it.setEffects(listOf(color)); builder.addEffect(it) }
            }
            camera = currentProvider.bindToLifecycle(currentOwner, selector, builder.build())
            needsBind = false
            val active = requireNotNull(camera)
            val zoom = active.cameraInfo.zoomState.value
            val exposure = active.cameraInfo.exposureState
            val hasFlash = active.cameraInfo.hasFlashUnit()
            photo?.flashMode = if (hasFlash) preferences.flash else ImageCapture.FLASH_MODE_OFF
            val resolution = photo?.resolutionInfo?.resolution ?: video?.resolutionInfo?.resolution
            state = state.copy(front = useFront, canFlip = rear && front,
                hasFlash = hasFlash, flash = photo?.flashMode ?: ImageCapture.FLASH_MODE_OFF,
                minZoom = zoom?.minZoomRatio ?: 1f, maxZoom = zoom?.maxZoomRatio ?: 1f,
                zoom = zoom?.zoomRatio ?: 1f,
                minEv = exposure.exposureCompensationRange.lower,
                maxEv = exposure.exposureCompensationRange.upper,
                evStep = exposure.exposureCompensationStep.toFloat(),
                output = resolution?.let { "${it.width} × ${it.height}" } ?: "Device-selected size")
            previewView.previewStreamState.removeObservers(currentOwner)
            previewView.previewStreamState.observe(currentOwner) {
                if (generation == token && !closed) state = state.copy(
                    ready = it == PreviewView.StreamState.STREAMING && state.error == null)
            }
            active.cameraInfo.cameraState.observe(currentOwner) {
                if (generation == token && it.error != null && !closed) {
                    state = state.copy(ready = false, error = "Camera is unavailable. Close other camera apps and retry.")
                    stop()
                }
            }
        } catch (_: Exception) {
            releaseBinding()
            state = state.copy(ready = false, error = "This camera setup is unavailable. Retry, or use original-only capture.")
        }
    }

    fun changeMode(mode: CaptureMode) {
        if (!gate.isIdle() || state.mode == mode) return
        state = state.copy(mode = mode)
        bind()
    }

    fun flip() {
        if (!gate.isIdle() || !state.canFlip) return
        state = state.copy(front = !state.front)
        bind()
    }

    fun selectLook(value: Look) {
        if (!gate.isIdle() || state.originalOnly) return
        look = value
        preferences.look = value
        comparing = false
        updateMatrix()
    }

    fun changeStrength(value: Float) {
        if (!gate.isIdle() || !value.isFinite() || state.originalOnly) return
        strength = value.coerceIn(0f, 1f)
        updateMatrix()
    }
    fun persistStrength() { preferences.strength = strength }
    fun compare() {
        if (!gate.isIdle() || state.originalOnly) return
        comparing = !comparing
        updateMatrix()
    }
    private fun updateMatrix() {
        render.set(if (comparing || state.originalOnly) RenderRecipe() else RenderRecipe(look, strength, smooth, glow))
    }

    fun changeSmooth(value: Float) {
        if (!gate.isIdle() || state.originalOnly || !value.isFinite()) return
        smooth = value.coerceIn(0f, 1f); comparing = false; updateMatrix()
    }
    fun changeGlow(value: Float) {
        if (!gate.isIdle() || state.originalOnly || !value.isFinite()) return
        glow = value.coerceIn(0f, 1f); comparing = false; updateMatrix()
    }
    fun persistBeauty() { preferences.smooth = smooth; preferences.glow = glow }
    fun setFlash(value: Int) {
        if (!gate.isIdle() || !state.hasFlash || state.mode != CaptureMode.PHOTO || value !in 0..2) return
        preferences.flash = value; photo?.flashMode = value; state = state.copy(flash = value)
    }
    fun setTimer(value: Int) {
        if (!gate.isIdle() || value !in listOf(0, 3, 10)) return
        preferences.timer = value; state = state.copy(timer = value)
    }
    fun setMirror(value: Boolean) {
        if (!gate.isIdle()) return
        preferences.mirror = value; state = state.copy(mirror = value)
    }
    fun setKeepOriginal(value: Boolean) {
        if (!gate.isIdle()) return
        preferences.keepOriginal = value; state = state.copy(keepOriginal = value)
    }
    fun saveRecipe(slot: Int) {
        if (!gate.isIdle() || state.originalOnly) return
        preferences.saveRecipe(slot, look, strength, smooth, glow)
        state = state.copy(notice = "Recipe $slot saved on this phone.")
    }
    fun loadRecipe(slot: Int) {
        if (!gate.isIdle() || state.originalOnly) return
        val recipe = preferences.recipe(slot) ?: return
        look = recipe.look; strength = recipe.strength; smooth = recipe.smooth; glow = recipe.glow
        preferences.look = look; persistStrength(); persistBeauty(); comparing = false; updateMatrix()
    }
    fun resetCreative() {
        if (!gate.isIdle()) return
        look = Look.ORIGINAL; strength = .7f; smooth = 0f; glow = 0f; comparing = false
        preferences.look = look; persistStrength(); persistBeauty(); updateMatrix()
    }

    fun recoverPhotos() {
        if (!gate.isIdle() || state.recovering || closed) return
        state = state.copy(recovering = true)
        worker.execute {
            val result = runCatching { photos.recover() }
            val latest = runCatching { photos.latest() }.getOrNull()
            executor.execute {
                if (!closed) state = state.copy(recovering = false,
                    saved = result.getOrNull()?.lastOrNull()?.media ?: state.saved ?: latest,
                    notice = when {
                        result.isFailure -> "Unsaved originals remain private. Free storage, then retry recovery in Settings."
                        result.getOrThrow().isNotEmpty() -> "${result.getOrThrow().size} interrupted photo(s) recovered as originals."
                        else -> state.notice
                    })
            }
        }
    }

    fun zoom(value: Float) {
        val active = camera ?: return
        val target = value.coerceIn(state.minZoom, state.maxZoom)
        val future = active.cameraControl.setZoomRatio(target)
        future.addListener({
            try { future.get(); state = state.copy(zoom = target) } catch (_: Exception) { /* Superseded gestures are normal. */ }
        }, executor)
    }

    fun expose(index: Int) {
        val active = camera ?: return
        val future = active.cameraControl.setExposureCompensationIndex(index.coerceIn(state.minEv, state.maxEv))
        future.addListener({
            try { state = state.copy(ev = future.get()) } catch (_: Exception) {
                if (!closed) state = state.copy(notice = "Exposure could not be changed.")
            }
        }, executor)
    }

    fun focus(x: Float, y: Float) {
        val preview = view ?: return
        val active = camera ?: return
        if (!state.ready) return
        val point = preview.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        if (!active.cameraInfo.isFocusMeteringSupported(action)) return
        val future = active.cameraControl.startFocusAndMetering(action)
        future.addListener({
            try { future.get() } catch (_: Exception) { /* A new tap may cancel the previous focus. */ }
        }, executor)
    }

    fun capture(audio: Boolean) {
        if (gate.getPhase() == Phase.STARTING || gate.getPhase() == Phase.RECORDING) { stop(); return }
        if (closed || !state.ready || !gate.isIdle() || comparing) return
        if (state.mode == CaptureMode.PHOTO) takePhoto() else startVideo(audio)
    }

    private fun mediaValues(mime: String, directory: String, extension: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Lensqivo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension")
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
    }

    private fun takePhoto() {
        val capture = photo ?: return
        val id = gate.beginPhoto()
        if (id == 0L) return
        syncPhase()
        val recipe = render.get()
        val mirror = state.front && state.mirror
        val keepOriginal = state.keepOriginal
        fun shoot() = performPhoto(id, capture, recipe, mirror, keepOriginal)
        if (state.timer == 0) shoot() else {
            countdownId = id
            state = state.copy(countdown = state.timer)
            val tick = object : Runnable {
                override fun run() {
                    if (countdownId != id || closed) return
                    state = state.copy(countdown = state.countdown - 1)
                    if (state.countdown == 0) { countdownId = 0L; shoot() }
                    else timerHandler.postDelayed(this, 1_000)
                }
            }
            timerHandler.postDelayed(tick, 1_000)
        }
    }

    private fun performPhoto(id: Long, capture: ImageCapture, recipe: RenderRecipe, mirror: Boolean, keepOriginal: Boolean) {
        worker.execute {
            val input = runCatching { photos.newInput() }.getOrNull()
            executor.execute shutter@{
                if (input == null) { finish(id, null, "Capture storage unavailable. Free space and retry."); return@shutter }
                if (closed || owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != true || capture !== photo) {
                    finish(id, null, "Capture cancelled before the shutter opened."); return@shutter
                }
                val options = ImageCapture.OutputFileOptions.Builder(input)
                    .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = mirror }).build()
                try {
                    capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            worker.execute {
                                val saved = runCatching { photos.export(input, recipe, keepOriginal) }
                                executor.execute {
                                    val value = saved.getOrNull()
                                    finish(id, value?.media, value?.message
                                        ?: "Photo remains private after a save failure. Free storage, then recover the original in Settings.")
                                }
                            }
                        }
                        override fun onError(exception: ImageCaptureException) {
                            worker.execute { if (runCatching { photos.requireCompleteJpeg(input) }.isFailure) input.delete() }
                            finish(id, null, "Photo could not be captured. Check storage and camera access, then retry.")
                        }
                    })
                } catch (_: Exception) {
                    worker.execute { if (runCatching { photos.requireCompleteJpeg(input) }.isFailure) input.delete() }
                    finish(id, null, "Photo could not start. Check camera access and storage.")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startVideo(audio: Boolean) {
        val capture = video ?: return
        val id = gate.beginVideo()
        if (id == 0L) return
        state = state.copy(audioInterrupted = false)
        syncPhase()
        try {
            val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(mediaValues("video/mp4", "Movies/Lensqivo", "mp4")).build()
            var pending = capture.output.prepareRecording(context, options)
            if (audio && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pending = pending.withAudioEnabled()
            }
            recording = pending.start(executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> { gate.started(id); syncPhase() }
                    is VideoRecordEvent.Status -> {
                        val audioState = event.recordingStats.audioStats.audioState
                        val interrupted = audio && audioState != AudioStats.AUDIO_STATE_ACTIVE
                        state = state.copy(seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000,
                            audioInterrupted = state.audioInterrupted || interrupted)
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording?.close()
                        recording = null
                        val uri = event.outputResults.outputUri.takeUnless { it == Uri.EMPTY }
                        val broken = event.error in setOf(VideoRecordEvent.Finalize.ERROR_UNKNOWN,
                            VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR, VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
                            VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA, VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS)
                        if (broken && uri != null) {
                            // ContentResolver I/O stays off the main callback executor.
                            Thread({ runCatching { context.contentResolver.delete(uri, null, null) } }, "lensqivo-cleanup").start()
                        }
                        val media = if (!broken) uri?.let { SavedMedia(it, "video/mp4") } else null
                        val message = when {
                            !event.hasError() && media != null && state.audioInterrupted -> "Video saved. Sound was unavailable for part or all of this clip."
                            !event.hasError() && media != null -> "Video saved to Movies/Lensqivo."
                            media != null -> "Recording stopped early. A clip was recovered; open it to check playback."
                            else -> "Video could not be saved. Check storage and camera access."
                        }
                        finish(id, media, message)
                    }
                }
            }
        } catch (_: Exception) {
            recording?.close()
            recording = null
            finish(id, null, "Recording could not start. Check storage, camera and microphone access.")
        }
    }

    fun stop() {
        if (countdownId != 0L) {
            val id = countdownId; countdownId = 0L
            timerHandler.removeCallbacksAndMessages(null)
            state = state.copy(countdown = 0)
            finish(id, null, "Self-timer cancelled.")
        }
        if (gate.requestStop()) {
            syncPhase()
            recording?.stop()
        }
    }

    private fun syncPhase() { state = state.copy(phase = gate.getPhase()) }
    private fun finish(id: Long, saved: SavedMedia?, message: String) {
        if (!gate.complete(id)) return
        state = state.copy(phase = gate.getPhase(), seconds = 0, saved = saved ?: state.saved, notice = message)
        if (closed || owner == null) releaseBinding() else if (needsBind) bind()
        if (closed) worker.shutdown()
    }
    fun clearNotice() { state = state.copy(notice = null) }

    private fun releaseBinding() {
        generation++
        if (useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        camera?.cameraInfo?.cameraState?.let { info -> owner?.let { info.removeObservers(it) } }
        camera = null
        photo = null
        video = null
        useCases = emptyList()
        effect?.close()
        effect = null
        state = state.copy(ready = false)
    }

    private fun removeObservers() {
        owner?.let {
            it.lifecycle.removeObserver(observer)
            camera?.cameraInfo?.cameraState?.removeObservers(it)
            view?.previewStreamState?.removeObservers(it)
        }
    }

    override fun close() {
        closed = true
        removeObservers()
        stop()
        owner = null
        view = null
        if (gate.isIdle()) releaseBinding()
        if (gate.isIdle()) worker.shutdown()
    }
}
