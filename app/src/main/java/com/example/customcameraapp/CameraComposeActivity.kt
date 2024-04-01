package com.example.customcameraapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.example.customcameraapp.ui.theme.CustomCameraAppTheme
import java.text.SimpleDateFormat
import java.util.Locale

class CameraComposeActivity : ComponentActivity() {


    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    )
    { permissions ->
        // Handle Permission granted/rejected
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value)
                permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                baseContext,
                "Permission request denied",
                Toast.LENGTH_SHORT
            ).show()
            mainContent(false)
        } else {
            mainContent(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permissions
        if (hasRequiredPermissions()) {
            mainContent(true)
        } else {
            requestPermissions()
        }
    }

    private fun mainContent(hasAllPermission: Boolean) {
        setContent {
            CustomCameraAppTheme {
                if (hasAllPermission) {
                    ComposeCamera()
                } else {
                    NoPermissionContent()
                }
            }
        }
    }


    @Composable
    fun NoPermissionContent() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Permission Denied")
            Button(onClick = { requestPermissions() }) {
                Text("Request Permission")
            }
        }
    }

    @Composable
    fun ComposeCamera() {
        val controller = remember {
            LifecycleCameraController(this).apply {
                setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
                videoCaptureQualitySelector = QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )
                previewTargetSize = CameraController.OutputSize(Size(1920, 1080))
            }
        }
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            CameraPreview(controller, Modifier.fillMaxSize(fraction = .9f))
            ActionButtons(controller, Modifier.fillMaxSize())
        }
    }


    @Composable
    fun ActionButtons(
        controller: LifecycleCameraController,
        modifier: Modifier = Modifier
    ) {
        var recording: Recording? by remember { mutableStateOf(null) }
        val isRecording by remember(recording) { mutableStateOf(recording != null) }

        var isPaused by remember { mutableStateOf(false) }
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = if (!isRecording) Arrangement.Center else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isRecording) {
                Button(onClick = {
                    recording = startRecordingVideo(controller) {
                        when (it) {
                            is VideoRecordEvent.Finalize -> {
                                recording?.close()
                                recording = null
                                Toast.makeText(
                                    baseContext,
                                    "Recording saved into ${it.outputResults.outputUri}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            is VideoRecordEvent.Pause -> {
                                isPaused = true
                                Toast.makeText(baseContext, "Recording Paused", Toast.LENGTH_SHORT)
                                    .show()
                            }

                            is VideoRecordEvent.Resume -> {
                                Toast.makeText(baseContext, "Recording Resumed", Toast.LENGTH_SHORT)
                                    .show()
                                isPaused = false
                            }

                            is VideoRecordEvent.Status -> {
                                println("Number of bytes: ${it.recordingStats.numBytesRecorded}")
                            }
                        }
                    }
                }) {
                    Text(
                        "Start Recording", style = MaterialTheme.typography.bodyLarge.copy(
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp

                        )
                    )
                }
            }

            if (isRecording && !isPaused) {
                Button(onClick = { recording?.pause() }) {
                    Text(
                        "Pause Recording", style = MaterialTheme.typography.bodyLarge.copy(
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp

                        )
                    )
                }
            }
            if (isPaused) {
                Button(onClick = { recording?.resume() }) {
                    Text(
                        "Resume Recording", style = MaterialTheme.typography.bodyLarge.copy(
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp

                        )
                    )
                }
            }
            if (isRecording) {
                Button(onClick = { recording?.stop() }) {
                    Text(
                        "Stop Recording", style = MaterialTheme.typography.bodyLarge.copy(
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp

                        )
                    )
                }
            }

            if (isRecording) {
                Button(onClick = { takePhoto(controller) }) {
                    Text(
                        "Take Photo", style = MaterialTheme.typography.bodyLarge.copy(
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp

                        )
                    )
                }
            }
        }
    }


    private fun takePhoto(controller: LifecycleCameraController) {


        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()


        controller.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        baseContext,
                        "Photo saved into ${outputFileResults.savedUri}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        baseContext,
                        "Photo capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

    }


    @SuppressLint("MissingPermission")
    private fun startRecordingVideo(
        controller: LifecycleCameraController,
        events: Consumer<VideoRecordEvent>
    ): Recording {

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        return controller.startRecording(
            mediaStoreOutputOptions,
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(this),
            events
        )
    }


    private fun requestPermissions() {
        // TODO: Check if we need to navigate user to the app settings page if previously denied?
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }


    private fun hasRequiredPermissions() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }


    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}