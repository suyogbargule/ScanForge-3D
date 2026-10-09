package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.camera.CameraProvider
import com.example.engine.photosphere.LocalPhotoSphereStitcher
import com.example.model.DotStatus
import com.example.model.PhotoSphereDot
import com.example.model.PhotoSphereGenerator
import com.example.model.PhotoSphereSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

data class PhotoSphereUiState(
    val session: PhotoSphereSession = PhotoSphereSession(id = "current", title = "360 Panorama", createdAt = System.currentTimeMillis()),
    val deviceYawDeg: Float = 0f,       // 0..360 (compass heading)
    val devicePitchDeg: Float = 0f,     // -90..+90 (tilt up/down)
    val deviceRollDeg: Float = 0f,      // -180..+180 (horizon level)
    val activeDot: PhotoSphereDot? = null,
    val alignmentDistanceDeg: Float = 999f,
    val isAligned: Boolean = false,
    val isAutoCaptureEnabled: Boolean = true,
    val isLevel: Boolean = true,
    val isStitching: Boolean = false,
    val stitchProgress: Float = 0f,
    val stitchMessage: String = "",
    val stitchedResultFile: File? = null
)

class PhotoSphereViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    val cameraProvider = CameraProvider(application)
    private val stitcher = LocalPhotoSphereStitcher()

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val _uiState = MutableStateFlow(PhotoSphereUiState())
    val uiState: StateFlow<PhotoSphereUiState> = _uiState.asStateFlow()

    private var isTracking = false
    private var lastAutoCaptureTime = 0L

    init {
        resetSession()
    }

    fun resetSession(title: String = "360 Photo Sphere") {
        val dots = PhotoSphereGenerator.generateDefaultGrid()
        val firstTarget = dots.firstOrNull()
        _uiState.value = PhotoSphereUiState(
            session = PhotoSphereSession(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = System.currentTimeMillis(),
                dots = dots
            ),
            activeDot = firstTarget,
            isAutoCaptureEnabled = true
        )
    }

    fun startTracking() {
        isTracking = true
        if (rotationSensor != null && sensorManager != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopTracking() {
        isTracking = false
        sensorManager?.unregisterListener(this)
    }

    fun toggleAutoCapture() {
        _uiState.value = _uiState.value.copy(
            isAutoCaptureEnabled = !_uiState.value.isAutoCaptureEnabled
        )
    }

    fun setManualHeading(yawDelta: Float, pitchDelta: Float) {
        val cur = _uiState.value
        val newYaw = (cur.deviceYawDeg + yawDelta + 360f) % 360f
        val newPitch = (cur.devicePitchDeg + pitchDelta).coerceIn(-85f, 85f)
        updateOrientation(newYaw, newPitch, 0f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTracking || event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            var yawDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (yawDeg < 0) yawDeg += 360f
            val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

            updateOrientation(yawDeg, pitchDeg, rollDeg)
        }
    }

    private fun updateOrientation(yaw: Float, pitch: Float, roll: Float) {
        val state = _uiState.value
        val isLevel = abs(roll) < 12f // Within 12 degrees is level

        // Find closest pending dot
        val pendingDots = state.session.dots.filter { it.status != DotStatus.CAPTURED }
        val nearestDot = pendingDots.minByOrNull { dot ->
            angularDist(yaw, pitch, dot.azimuthDeg, dot.pitchDeg)
        }

        val dist = if (nearestDot != null) {
            angularDist(yaw, pitch, nearestDot.azimuthDeg, nearestDot.pitchDeg)
        } else 999f

        val isAligned = dist < 7.5f

        _uiState.value = state.copy(
            deviceYawDeg = yaw,
            devicePitchDeg = pitch,
            deviceRollDeg = roll,
            activeDot = nearestDot,
            alignmentDistanceDeg = dist,
            isAligned = isAligned,
            isLevel = isLevel
        )

        // Auto capture if aligned & level
        if (isAligned && isLevel && state.isAutoCaptureEnabled && nearestDot != null) {
            val now = System.currentTimeMillis()
            if (now - lastAutoCaptureTime > 1100L) {
                lastAutoCaptureTime = now
                captureDot(nearestDot)
            }
        }
    }

    fun captureManual() {
        val target = _uiState.value.activeDot ?: return
        captureDot(target)
    }

    private fun captureDot(dot: PhotoSphereDot) {
        triggerHaptic()
        val state = _uiState.value
        val sessionDir = File(getApplication<Application>().filesDir, "photosphere/${state.session.id}")
        sessionDir.mkdirs()
        val file = File(sessionDir, "dot_${dot.id}_${System.currentTimeMillis()}.jpg")

        viewModelScope.launch {
            val success = cameraProvider.captureFrame(file, dot.azimuthDeg, dot.pitchDeg)
            if (success) {
                val updatedDots = state.session.dots.map {
                    if (it.id == dot.id) it.copy(status = DotStatus.CAPTURED, imagePath = file.absolutePath)
                    else it
                }

                val updatedSession = state.session.copy(dots = updatedDots)
                val nextNearest = updatedDots.filter { it.status != DotStatus.CAPTURED }
                    .minByOrNull { angularDist(state.deviceYawDeg, state.devicePitchDeg, it.azimuthDeg, it.pitchDeg) }

                _uiState.value = state.copy(
                    session = updatedSession,
                    activeDot = nextNearest
                )
            }
        }
    }

    fun stitchCurrentSession(onCompleted: (File) -> Unit) {
        val state = _uiState.value
        val sessionDir = File(getApplication<Application>().filesDir, "photosphere/${state.session.id}")
        val outputFile = File(sessionDir, "stitched_360_panorama.jpg")

        viewModelScope.launch {
            _uiState.value = state.copy(
                isStitching = true,
                stitchProgress = 0.05f,
                stitchMessage = "Beginning local on-device 360 stitching..."
            )

            val resultFile = stitcher.stitchPanorama(state.session.dots, outputFile) { prog, msg ->
                _uiState.value = _uiState.value.copy(
                    stitchProgress = prog,
                    stitchMessage = msg
                )
            }

            _uiState.value = _uiState.value.copy(
                isStitching = false,
                stitchProgress = 1.0f,
                stitchedResultFile = resultFile,
                session = state.session.copy(
                    stitchedPanoramaPath = resultFile.absolutePath,
                    isStitched = true
                )
            )

            onCompleted(resultFile)
        }
    }

    private fun angularDist(yaw1: Float, pitch1: Float, yaw2: Float, pitch2: Float): Float {
        val dYaw = shortestAngle(yaw1, yaw2)
        val dPitch = abs(pitch1 - pitch2)
        return sqrt(dYaw * dYaw + dPitch * dPitch)
    }

    private fun shortestAngle(a: Float, b: Float): Float {
        val diff = (a - b + 180f) % 360f - 180f
        return abs(if (diff < -180f) diff + 360f else diff)
    }

    private fun triggerHaptic() {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(60)
            }
        } catch (_: Exception) {}
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
