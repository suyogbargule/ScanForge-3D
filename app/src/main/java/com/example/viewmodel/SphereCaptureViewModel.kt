package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Surface
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScanDatabase
import com.example.data.repository.ScanRepository
import com.example.engine.camera.CameraProvider
import com.example.engine.coverage.SphereCoverageChecker
import com.example.engine.quality.CaptureQualityGate
import com.example.engine.sensors.OrientationSnapshot
import com.example.engine.sensors.SphereSensorTracker
import com.example.engine.storage.SessionFileStorage
import com.example.model.CameraFovInfo
import com.example.model.CaptureSessionSummary
import com.example.model.CaptureSettings
import com.example.model.CaptureState
import com.example.model.CaptureTarget
import com.example.model.CoverageReport
import com.example.model.DeviceDiagnostics
import com.example.model.GuidanceMode
import com.example.model.SensorMode
import com.example.model.SessionStateStatus
import com.example.model.SphereLayer
import com.example.model.SphereTargetBuilder
import com.example.model.TargetStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2

data class CaptureUiState(
    val sessionId: String = "",
    val sessionTitle: String = "",
    val targets: List<CaptureTarget> = SphereTargetBuilder.buildTargets(),
    val activeTarget: CaptureTarget? = null,
    val captureState: CaptureState = CaptureState.CALIBRATING,
    val holdProgress: Float = 0f,
    val angleErrorDeg: Float = 180f,
    val arrowAngleRad: Float = 0f,
    val showArrow: Boolean = true,
    val guidanceHint: String = "Hold the phone level • Stand still, elbows in",
    val isLevel: Boolean = true,
    val isSteady: Boolean = true,
    val aeAfLocked: Boolean = false,
    val viewMatrix4x4: FloatArray = FloatArray(16).apply {
        this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
    },
    val cameraForward: FloatArray = floatArrayOf(0f, 1f, 0f),
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val gyroMagRadSec: Float = 0f,
    val activeSensorLabel: String = "Initializing…",
    val hasLiveSensor: Boolean = true,
    val cameraFov: CameraFovInfo = CameraFovInfo(),
    val availableStorageMb: Long = 1024L,
    val storageOk: Boolean = true,
    val qualityAlertMessage: String? = null,
    val shutterFlashTrigger: Long = 0L,
    val singleRetakeTargetId: Int? = null,
    val sessionCompletedTrigger: String? = null,
    val settings: CaptureSettings = CaptureSettings()
) {
    val completedCount: Int get() = targets.count { it.status == TargetStatus.COMPLETED }
    val flaggedCount: Int get() = targets.count { it.exposureWarning || it.textureWarning || it.status == TargetStatus.NEEDS_RETAKE }
    val progressPercent: Int get() = ((completedCount / 30f) * 100f).toInt()

    fun layerCount(layer: SphereLayer): Pair<Int, Int> {
        val inLayer = targets.filter { it.layer == layer }
        val done = inLayer.count { it.status == TargetStatus.COMPLETED }
        return done to inLayer.size
    }
}

data class ReviewUiState(
    val sessionId: String = "",
    val title: String = "",
    val targets: List<CaptureTarget> = emptyList(),
    val coverageReport: CoverageReport = CoverageReport(),
    val cameraFov: CameraFovInfo = CameraFovInfo(),
    val isExporting: Boolean = false,
    val exportMessage: String? = null
)

class SphereCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ScanDatabase.getInstance(application)
    private val repository = ScanRepository(database.scanDao())
    private val fileStorage = SessionFileStorage(application)
    private val qualityGate = CaptureQualityGate()
    val cameraProvider = CameraProvider(application)
    val sensorTracker = SphereSensorTracker(application)

    val savedSessions: StateFlow<List<CaptureSessionSummary>> = repository.observeAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _captureUi = MutableStateFlow(CaptureUiState())
    val captureUi: StateFlow<CaptureUiState> = _captureUi.asStateFlow()

    private val _reviewUi = MutableStateFlow(ReviewUiState())
    val reviewUi: StateFlow<ReviewUiState> = _reviewUi.asStateFlow()

    private val _settings = MutableStateFlow(CaptureSettings())
    val settings: StateFlow<CaptureSettings> = _settings.asStateFlow()

    private var latestSnapshot: OrientationSnapshot? = null
    private var holdStartMs: Long = 0L
    private var captureOrderCounter: Int = 0
    private var boundPreviewView: PreviewView? = null
    private var sessionCreatedAtMs: Long = 0L

    fun getDeviceDiagnostics(): DeviceDiagnostics {
        return DeviceDiagnostics(
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            hasBackCamera = cameraProvider.hasBackCamera(),
            hasGameRotationVector = sensorTracker.hasGameRotationVector(),
            hasRotationVector = sensorTracker.hasRotationVector(),
            hasGyroscope = sensorTracker.hasGyroscope(),
            hasAccelerometerAndMag = sensorTracker.hasAccelAndMag(),
            freeStorageMb = fileStorage.getAvailableStorageMb(),
            cameraFov = getEffectiveFov(cameraProvider.readCameraFovInfo())
        )
    }

    fun updateSettings(transform: (CaptureSettings) -> CaptureSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        val currentFov = getEffectiveFov(cameraProvider.readCameraFovInfo())
        _captureUi.value = _captureUi.value.copy(settings = updated, cameraFov = currentFov)
    }

    fun startNewSession() {
        val now = System.currentTimeMillis()
        val idStamp = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(Date(now))
        val sessionId = idStamp
        val title = "Sphere ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(now))}"
        val targets = SphereTargetBuilder.buildTargets()
        val freeMb = fileStorage.getAvailableStorageMb()
        val fov = getEffectiveFov(cameraProvider.readCameraFovInfo())

        sessionCreatedAtMs = now
        captureOrderCounter = 0
        holdStartMs = 0L
        sensorTracker.setYawOffset(0f)

        val firstTarget = SphereTargetBuilder.getNextGuidedTarget(targets)

        _captureUi.value = CaptureUiState(
            sessionId = sessionId,
            sessionTitle = title,
            targets = targets,
            activeTarget = firstTarget,
            captureState = CaptureState.CALIBRATING,
            cameraFov = fov,
            availableStorageMb = freeMb,
            storageOk = freeMb >= 150L,
            settings = _settings.value,
            guidanceHint = "Hold the phone level • Stand still, elbows in"
        )
    }

    fun resumeSession(sessionId: String, singleTargetId: Int? = null, onLoaded: () -> Unit) {
        viewModelScope.launch {
            val summary = repository.getSession(sessionId) ?: return@launch
            val targets = repository.loadTargets(sessionId)
            val freeMb = fileStorage.getAvailableStorageMb()
            val fov = getEffectiveFov(
                CameraFovInfo(
                    hFovDeg = summary.fovHDeg,
                    vFovDeg = summary.fovVDeg,
                    isEstimated = false,
                    lensSummary = "Session ${summary.fovHDeg.toInt()}° × ${summary.fovVDeg.toInt()}°"
                )
            )

            sessionCreatedAtMs = summary.createdAt
            captureOrderCounter = targets.maxOfOrNull { it.captureOrder } ?: 0
            holdStartMs = 0L
            sensorTracker.setYawOffset(summary.yawOffsetDeg)

            val nextTarget = if (singleTargetId != null) {
                targets.find { it.id == singleTargetId }
            } else {
                pickNextTarget(targets, floatArrayOf(0f, 1f, 0f))
            }

            _captureUi.value = CaptureUiState(
                sessionId = summary.sessionId,
                sessionTitle = summary.title,
                targets = targets,
                activeTarget = nextTarget,
                captureState = if (singleTargetId != null) CaptureState.SEARCHING else CaptureState.CALIBRATING,
                cameraFov = fov,
                availableStorageMb = freeMb,
                storageOk = freeMb >= 150L,
                singleRetakeTargetId = singleTargetId,
                settings = _settings.value,
                guidanceHint = if (singleTargetId != null) {
                    "Retaking position #$singleTargetId"
                } else {
                    "Hold the phone level • Tap START to resume"
                }
            )
            onLoaded()
        }
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        boundPreviewView = previewView
        cameraProvider.bindCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            onBound = { detectedFov ->
                val effective = getEffectiveFov(detectedFov)
                _captureUi.value = _captureUi.value.copy(cameraFov = effective)
                if (_captureUi.value.captureState != CaptureState.CALIBRATING) {
                    val locked = cameraProvider.lockCameraControls(previewView)
                    _captureUi.value = _captureUi.value.copy(aeAfLocked = locked)
                }
            },
            onError = { err ->
                _captureUi.value = _captureUi.value.copy(
                    qualityAlertMessage = "Camera preview unavailable: ${err.localizedMessage ?: "fallback active"}"
                )
            }
        )
    }

    fun startTracking() {
        val mode = _settings.value.sensorMode
        sensorTracker.start(mode) { snapshot ->
            onOrientationUpdate(snapshot)
        }
    }

    fun stopTracking() {
        sensorTracker.stop()
        cameraProvider.unlockCameraControls()
    }

    /**
     * Triggered when user taps START on Calibration screen (Screen 1 in Figure 5):
     * 1. Locks AE, AWB, and AF
     * 2. Anchors yaw offset so forward heading = 0° (only on fresh session)
     * 3. Saves initial session record to Room + session.json
     * 4. Begins guided search for target #10
     */
    fun onStartCalibrationConfirm() {
        val state = _captureUi.value
        val locked = cameraProvider.lockCameraControls(boundPreviewView)

        val yawOffset = if (state.completedCount == 0 && state.singleRetakeTargetId == null) {
            sensorTracker.anchorYawToCurrentHeading()
        } else {
            sensorTracker.getYawOffset()
        }

        val nextTarget = pickNextTarget(state.targets, state.cameraForward)
        _captureUi.value = state.copy(
            captureState = CaptureState.SEARCHING,
            aeAfLocked = locked,
            activeTarget = nextTarget,
            holdProgress = 0f,
            qualityAlertMessage = null
        )

        persistCurrentSessionState(yawOffset)
    }

    fun onManualPan(yawDeltaDeg: Float, pitchDeltaDeg: Float) {
        sensorTracker.setManualDelta(yawDeltaDeg, pitchDeltaDeg)
    }

    fun toggleGuidanceMode() {
        val next = if (_settings.value.guidanceMode == GuidanceMode.GUIDED) {
            GuidanceMode.FREE
        } else {
            GuidanceMode.GUIDED
        }
        updateSettings { it.copy(guidanceMode = next) }
        val state = _captureUi.value
        if (state.singleRetakeTargetId == null && state.captureState == CaptureState.SEARCHING) {
            val target = pickNextTarget(state.targets, state.cameraForward)
            _captureUi.value = _captureUi.value.copy(activeTarget = target)
        }
    }

    fun clearQualityAlert() {
        _captureUi.value = _captureUi.value.copy(qualityAlertMessage = null)
    }

    fun triggerManualShutter() {
        val state = _captureUi.value
        val target = state.activeTarget ?: return
        if (state.captureState == CaptureState.CAPTURING) return
        if (state.captureState == CaptureState.CALIBRATING) {
            onStartCalibrationConfirm()
            return
        }
        executeCapture(target)
    }

    private fun onOrientationUpdate(snapshot: OrientationSnapshot) {
        latestSnapshot = snapshot
        val state = _captureUi.value
        val active = state.activeTarget

        // In Free mode while SEARCHING, continuously track nearest uncompleted target
        val resolvedTarget = if (
            state.captureState == CaptureState.SEARCHING &&
            state.singleRetakeTargetId == null &&
            _settings.value.guidanceMode == GuidanceMode.FREE
        ) {
            SphereTargetBuilder.getNearestUncompletedTarget(state.targets, snapshot.cameraForward) ?: active
        } else {
            active
        }

        if (state.captureState == CaptureState.CALIBRATING) {
            _captureUi.value = state.copy(
                viewMatrix4x4 = snapshot.viewMatrix4x4,
                cameraForward = snapshot.cameraForward,
                pitchDeg = snapshot.pitchDeg,
                rollDeg = snapshot.rollDeg,
                gyroMagRadSec = snapshot.gyroMagnitudeRadSec,
                isLevel = snapshot.isLevel,
                activeSensorLabel = snapshot.activeSensorLabel,
                hasLiveSensor = snapshot.hasLiveSensor,
                guidanceHint = if (snapshot.isLevel) {
                    "Level OK • Tap START, stand still with elbows in"
                } else {
                    "Hold the phone level • Stand still, elbows in"
                }
            )
            return
        }

        if (resolvedTarget == null || state.captureState == CaptureState.CAPTURING) {
            _captureUi.value = state.copy(
                viewMatrix4x4 = snapshot.viewMatrix4x4,
                cameraForward = snapshot.cameraForward,
                pitchDeg = snapshot.pitchDeg,
                rollDeg = snapshot.rollDeg,
                gyroMagRadSec = snapshot.gyroMagnitudeRadSec,
                activeSensorLabel = snapshot.activeSensorLabel,
                hasLiveSensor = snapshot.hasLiveSensor
            )
            return
        }

        // Compute angle error to active target (Section 17.2)
        val targetDir = resolvedTarget.directionVector()
        val angleErr = SphereTargetBuilder.angleErrorDeg(snapshot.cameraForward, targetDir)

        // Transform active tile center into camera space: v = view * tileCentre (Section 23.2 Step 10)
        // Note: viewMatrix4x4 is OpenGL column-major 4x4
        val m = snapshot.viewMatrix4x4
        val vx = m[0] * targetDir[0] + m[4] * targetDir[1] + m[8] * targetDir[2]
        val vy = m[1] * targetDir[0] + m[5] * targetDir[1] + m[9] * targetDir[2]
        // Screen space: +X is right, +Y is up in OpenGL camera coords -> Canvas +Y is down, so negate vy
        val arrowAngle = atan2(-vy, vx)

        val (yawDelta, pitchDelta) = SphereTargetBuilder.computeGuidanceDeltas(snapshot.cameraForward, resolvedTarget)

        // Section 17.3 & 22.4 tolerances: enter < 3.5° (or 4.5° if no gyro), leave > 5.5°
        val hasGyro = sensorTracker.hasGyroscope()
        val enterDeg = if (hasGyro) 3.5f else 4.5f
        val leaveDeg = 5.5f
        val steadyThreshold = if (hasGyro) 0.05f else 0.12f
        val isSteady = !snapshot.hasLiveSensor || snapshot.gyroMagnitudeRadSec < steadyThreshold

        val now = System.currentTimeMillis()
        var nextState = state.captureState
        var nextProgress = 0f

        when (state.captureState) {
            CaptureState.SEARCHING -> {
                if (angleErr < enterDeg) {
                    nextState = CaptureState.ALIGNED
                    triggerHapticTick()
                }
            }
            CaptureState.ALIGNED -> {
                if (angleErr > leaveDeg) {
                    nextState = CaptureState.SEARCHING
                } else if (isSteady) {
                    holdStartMs = now
                    nextState = CaptureState.HOLDING
                }
            }
            CaptureState.HOLDING -> {
                if (angleErr > leaveDeg || !isSteady) {
                    holdStartMs = 0L
                    nextState = if (angleErr > leaveDeg) CaptureState.SEARCHING else CaptureState.ALIGNED
                    nextProgress = 0f
                } else {
                    val holdTargetMs = if (hasGyro) {
                        _settings.value.holdDurationMs
                    } else {
                        (_settings.value.holdDurationMs + 200L).coerceAtMost(1100L)
                    }
                    nextProgress = ((now - holdStartMs).toFloat() / holdTargetMs.toFloat()).coerceIn(0f, 1f)
                    if (nextProgress >= 1f) {
                        nextState = CaptureState.CAPTURING
                    }
                }
            }
            else -> {}
        }

        val hint = when (nextState) {
            CaptureState.HOLDING -> if (isSteady) "Hold still" else "Keep phone steady…"
            CaptureState.ALIGNED -> "Steady… hold still"
            CaptureState.CAPTURING -> "Capturing…"
            else -> SphereTargetBuilder.formatGuidanceHint(yawDelta, pitchDelta, resolvedTarget.pitchDeg)
        }

        _captureUi.value = state.copy(
            activeTarget = resolvedTarget,
            captureState = nextState,
            holdProgress = nextProgress,
            angleErrorDeg = angleErr,
            arrowAngleRad = arrowAngle,
            showArrow = angleErr >= 4.0f,
            guidanceHint = hint,
            isLevel = snapshot.isLevel,
            isSteady = isSteady,
            viewMatrix4x4 = snapshot.viewMatrix4x4,
            cameraForward = snapshot.cameraForward,
            pitchDeg = snapshot.pitchDeg,
            rollDeg = snapshot.rollDeg,
            gyroMagRadSec = snapshot.gyroMagnitudeRadSec,
            activeSensorLabel = snapshot.activeSensorLabel,
            hasLiveSensor = snapshot.hasLiveSensor
        )

        if (nextState == CaptureState.CAPTURING) {
            executeCapture(resolvedTarget)
        }
    }

    private fun executeCapture(target: CaptureTarget) {
        val state = _captureUi.value
        val snapshot = latestSnapshot
        val rotMatrix = snapshot?.rotationMatrix3x3?.copyOf()
            ?: SphereCoverageChecker.idealRotationMatrix(target.yawDeg, target.pitchDeg)
        val quat = snapshot?.quaternion?.copyOf() ?: floatArrayOf(0f, 0f, 0f, 1f)
        val capturedAngleErr = state.angleErrorDeg

        _captureUi.value = state.copy(
            captureState = CaptureState.CAPTURING,
            shutterFlashTrigger = System.currentTimeMillis(),
            qualityAlertMessage = null
        )
        triggerConfirmHaptic()

        viewModelScope.launch {
            val tempFile = fileStorage.getTempImageFile(state.sessionId, target.id)
            val thumbFile = fileStorage.getThumbFile(state.sessionId, target.id)
            val finalFile = fileStorage.getFinalImageFile(state.sessionId, target.id)

            val capturedOk = cameraProvider.captureToTempFile(tempFile, target.yawDeg, target.pitchDeg)
            if (!capturedOk) {
                tempFile.delete()
                holdStartMs = 0L
                _captureUi.value = _captureUi.value.copy(
                    captureState = CaptureState.SEARCHING,
                    holdProgress = 0f,
                    qualityAlertMessage = "Capture failed • Try again"
                )
                return@launch
            }

            // Run Section 18 Quality Gate
            val gate = qualityGate.evaluateAndGenerateThumb(
                imageFile = tempFile,
                thumbFile = thumbFile,
                blurPreset = _settings.value.blurPreset
            )

            if (!gate.passedBlur) {
                // Reject blurred frame, keep same target active (Section 18 & 23.2 Step 9)
                tempFile.delete()
                holdStartMs = 0L
                _captureUi.value = _captureUi.value.copy(
                    captureState = CaptureState.ALIGNED,
                    holdProgress = 0f,
                    qualityAlertMessage = gate.warningMessage ?: "Image blurry • Hold still"
                )
                return@launch
            }

            // Safe atomic promotion to img_XX.jpg
            val checksum = fileStorage.promoteTempImage(tempFile, finalFile)
            if (checksum == null) {
                tempFile.delete()
                holdStartMs = 0L
                _captureUi.value = _captureUi.value.copy(
                    captureState = CaptureState.SEARCHING,
                    holdProgress = 0f,
                    qualityAlertMessage = "Failed saving image • Check storage"
                )
                return@launch
            }

            captureOrderCounter++
            val updatedTarget = target.copy(
                status = TargetStatus.COMPLETED,
                imagePath = finalFile.absolutePath,
                thumbPath = thumbFile.absolutePath,
                rotationMatrix = rotMatrix,
                quaternion = quat,
                angleErrorDeg = capturedAngleErr,
                blurScore = gate.blurScore,
                exposureWarning = gate.exposureWarning,
                textureWarning = gate.textureWarning,
                qualityNote = gate.warningMessage,
                timestamp = System.currentTimeMillis(),
                captureOrder = captureOrderCounter,
                imageWidth = gate.imageWidth,
                imageHeight = gate.imageHeight,
                checksumSha256 = checksum
            )

            val updatedTargets = _captureUi.value.targets.map {
                if (it.id == target.id) updatedTarget else it
            }

            // Persist incrementally after EVERY capture (Section 22.2)
            repository.saveTarget(state.sessionId, updatedTarget)
            val yawOffset = sensorTracker.getYawOffset()
            fileStorage.writeSessionJson(
                sessionId = state.sessionId,
                yawOffsetDeg = yawOffset,
                cameraFov = _captureUi.value.cameraFov,
                targets = updatedTargets
            )

            val isSingleRetake = _captureUi.value.singleRetakeTargetId != null
            val allCompleted = updatedTargets.all { it.status == TargetStatus.COMPLETED }

            val nextTarget = if (isSingleRetake || allCompleted) {
                null
            } else {
                pickNextTarget(updatedTargets, _captureUi.value.cameraForward)
            }

            holdStartMs = 0L
            _captureUi.value = _captureUi.value.copy(
                targets = updatedTargets,
                activeTarget = nextTarget,
                captureState = CaptureState.SEARCHING,
                holdProgress = 0f,
                qualityAlertMessage = gate.warningMessage,
                sessionCompletedTrigger = if (isSingleRetake || allCompleted) state.sessionId else null
            )

            persistCurrentSessionState(yawOffset)
        }
    }

    fun consumeSessionCompletedTrigger() {
        _captureUi.value = _captureUi.value.copy(sessionCompletedTrigger = null)
    }

    fun pauseAndSaveCurrentSession() {
        val state = _captureUi.value
        if (state.sessionId.isNotEmpty() && state.completedCount > 0) {
            persistCurrentSessionState(sensorTracker.getYawOffset())
        }
    }

    private fun persistCurrentSessionState(yawOffsetDeg: Float) {
        val state = _captureUi.value
        if (state.sessionId.isEmpty()) return

        viewModelScope.launch {
            val coverage = if (state.completedCount > 0) {
                SphereCoverageChecker.evaluateSessionCoverage(
                    targets = state.targets,
                    cameraFov = state.cameraFov,
                    n = 1000,
                    validateFilesOnDisk = false
                ).coveragePercent
            } else 0f

            val status = if (state.completedCount >= 30 && state.targets.all { it.status == TargetStatus.COMPLETED }) {
                SessionStateStatus.READY
            } else {
                SessionStateStatus.INCOMPLETE
            }

            val firstThumb = state.targets.firstOrNull { it.thumbPath != null }?.thumbPath
            val summary = CaptureSessionSummary(
                sessionId = state.sessionId,
                title = state.sessionTitle,
                createdAt = sessionCreatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                yawOffsetDeg = yawOffsetDeg,
                status = status,
                completedCount = state.completedCount,
                flaggedCount = state.flaggedCount,
                coveragePercent = coverage,
                thumbnailPath = firstThumb,
                sessionDirPath = fileStorage.getSessionDir(state.sessionId).absolutePath,
                fovHDeg = state.cameraFov.hFovDeg,
                fovVDeg = state.cameraFov.vFovDeg
            )
            repository.saveSession(summary, state.targets)
            fileStorage.writeSessionJson(
                sessionId = state.sessionId,
                yawOffsetDeg = yawOffsetDeg,
                cameraFov = state.cameraFov,
                targets = state.targets
            )
        }
    }

    fun loadReviewSession(sessionId: String) {
        viewModelScope.launch {
            val summary = repository.getSession(sessionId) ?: return@launch
            val targets = repository.loadTargets(sessionId)
            val fov = CameraFovInfo(
                hFovDeg = summary.fovHDeg,
                vFovDeg = summary.fovVDeg,
                isEstimated = false
            )
            val report = SphereCoverageChecker.evaluateSessionCoverage(
                targets = targets,
                cameraFov = fov,
                n = 4000,
                validateFilesOnDisk = true
            )

            // Mark gap positions if coverage < 99% and they aren't flagged yet
            val updatedTargets = if (report.coverageFraction < 0.99f && report.gapTargetIds.isNotEmpty()) {
                targets.map { t ->
                    if (t.id in report.gapTargetIds && t.status == TargetStatus.COMPLETED && t.qualityNote == null) {
                        t.copy(qualityNote = "Coverage gap nearby")
                    } else t
                }
            } else targets

            _reviewUi.value = ReviewUiState(
                sessionId = summary.sessionId,
                title = summary.title,
                targets = updatedTargets,
                coverageReport = report,
                cameraFov = fov
            )

            // Update Room coverage percentage
            repository.updateSessionSummary(
                summary.copy(
                    coveragePercent = report.coveragePercent,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun markTargetForRetake(targetId: Int) {
        val review = _reviewUi.value
        val updated = review.targets.map {
            if (it.id == targetId) it.copy(status = TargetStatus.NEEDS_RETAKE) else it
        }
        _reviewUi.value = review.copy(targets = updated)
        viewModelScope.launch {
            val changed = updated.find { it.id == targetId } ?: return@launch
            repository.saveTarget(review.sessionId, changed)
        }
    }

    fun exportSessionZip(context: Context, sessionId: String) {
        viewModelScope.launch {
            _reviewUi.value = _reviewUi.value.copy(isExporting = true, exportMessage = null)
            val zipFile = fileStorage.exportSessionZip(sessionId)
            if (zipFile == null) {
                _reviewUi.value = _reviewUi.value.copy(
                    isExporting = false,
                    exportMessage = "Failed to build ZIP archive"
                )
                return@launch
            }

            // Update Room status to EXPORTED
            repository.getSession(sessionId)?.let { summary ->
                repository.updateSessionSummary(
                    summary.copy(status = SessionStateStatus.EXPORTED, updatedAt = System.currentTimeMillis())
                )
            }

            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Scan 3D 30-Image Capture ($sessionId)")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(shareIntent, "Export 30-Shot Session ZIP").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                _reviewUi.value = _reviewUi.value.copy(
                    isExporting = false,
                    exportMessage = "Exported ${zipFile.name} (${zipFile.length() / 1024} KB)"
                )
            } catch (e: Exception) {
                _reviewUi.value = _reviewUi.value.copy(
                    isExporting = false,
                    exportMessage = "Saved ZIP at ${zipFile.name}"
                )
            }
        }
    }

    fun deleteSession(summary: CaptureSessionSummary) {
        viewModelScope.launch {
            repository.deleteSession(summary.sessionId, summary.sessionDirPath)
        }
    }

    private fun pickNextTarget(targets: List<CaptureTarget>, cameraFwd: FloatArray): CaptureTarget? {
        return when (_settings.value.guidanceMode) {
            GuidanceMode.GUIDED -> SphereTargetBuilder.getNextGuidedTarget(targets)
            GuidanceMode.FREE -> SphereTargetBuilder.getNearestUncompletedTarget(targets, cameraFwd)
        }
    }

    private fun getEffectiveFov(detected: CameraFovInfo): CameraFovInfo {
        val customH = _settings.value.customHFovDeg
        val customV = _settings.value.customVFovDeg
        return if (customH != null && customV != null) {
            detected.copy(
                hFovDeg = customH,
                vFovDeg = customV,
                isEstimated = false,
                lensSummary = "Custom ${customH.toInt()}° × ${customV.toInt()}°"
            )
        } else detected
    }

    private fun triggerHapticTick() {
        vibrateMs(18L)
    }

    private fun triggerConfirmHaptic() {
        vibrateMs(45L)
    }

    @Suppress("DEPRECATION")
    private fun vibrateMs(durationMs: Long) {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val v = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}
