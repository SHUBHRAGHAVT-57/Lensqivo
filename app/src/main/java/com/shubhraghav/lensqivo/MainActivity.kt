package com.shubhraghav.lensqivo

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class CameraModel(application: Application) : AndroidViewModel(application) {
    val session = CameraSession(application)
    override fun onCleared() { session.close() }
}

class MainActivity : ComponentActivity() {
    private val model: CameraModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val session = model.session
            var cameraAllowed by remember { mutableStateOf(hasPermission(Manifest.permission.CAMERA)) }
            var audioAllowed by remember { mutableStateOf(hasPermission(Manifest.permission.RECORD_AUDIO)) }
            var audioEnabled by remember { mutableStateOf(session.preferences.audio && audioAllowed) }
            val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraAllowed = it }
            val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                audioAllowed = granted
                audioEnabled = granted
                session.preferences.audio = granted
                if (!granted) Toast.makeText(this, "Microphone access is off. Silent video is still available.", Toast.LENGTH_LONG).show()
            }
            val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) openMedia(SavedMedia(uri, contentResolver.getType(uri) ?: "image/*"))
            }
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        cameraAllowed = hasPermission(Manifest.permission.CAMERA)
                        audioAllowed = hasPermission(Manifest.permission.RECORD_AUDIO)
                        if (!audioAllowed) { audioEnabled = false; session.preferences.audio = false }
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            LensqivoTheme {
                if (!cameraAllowed) {
                    CameraPermissionScreen(
                        request = { cameraPermission.launch(Manifest.permission.CAMERA) },
                        settings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) })
                } else {
                    CameraScreen(session, this, audioEnabled,
                        setAudio = { enabled ->
                            if (enabled && !audioAllowed) microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            else { audioEnabled = enabled; session.preferences.audio = enabled }
                        },
                        openSaved = { session.state.saved?.let(::openMedia) },
                        openLibrary = { library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) })
                }
            }
        }
    }

    private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun openMedia(media: SavedMedia) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(media.uri, media.mime)
                clipData = ClipData.newRawUri("Lensqivo capture", media.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "No available app could open this media. Find captures in your gallery's Lensqivo folder.", Toast.LENGTH_LONG).show()
        }
    }
}
