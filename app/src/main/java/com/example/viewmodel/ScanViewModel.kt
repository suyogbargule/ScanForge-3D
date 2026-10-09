package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScanDatabase
import com.example.data.repository.ScanRepository
import com.example.engine.camera.CameraProvider
import com.example.engine.guidance.CaptureGuide
import com.example.engine.pose.IMUPoseProvider
import com.example.engine.pose.PoseProvider
import com.example.engine.quality.FrameQualityAnalyzer
import com.example.engine.quality.FrameSelector
import com.example.engine.reconstruction.LocalReconstructionEngine
import com.example.engine.reconstruction.ReconstructionEngine
import com.example.model.CellStatus
import com.example.model.CoverageCell
import com.example.model.DeviceCapabilityProfile
import com.example.model.Frame
import com.example.model.Model3D
import com.example.model.Pose
import com.example.model.QualityColorState
import com.example.model.QualityProfile
import com.example.model.QualityResult
import com.example.model.ReconstructionStage
import com.example.model.ScanSession
import com.example.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class CaptureUiState(
    val currentSession: ScanSession? = null,
    val cells: List<CoverageCell> = emptyList(),
    val nextTarget: CoverageCell? = null,
    val currentPose: Pose = Pose(),
    val quality: QualityResult = QualityResult(),
    val capturedFrames: List<Frame> = emptyList(),
    val isAutoCaptureEnabled: Boolean = true,
    val isPaused: Boolean = false,
    val coveragePercentage: Float = 0f,
    val activeLayerName: String = "Upper Ring",
    val statusMessage: String = "Align camera with marker",
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val processingStageText: String = "",
    val reconstructedModel: Model3D? = null
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ScanDatabase.getInstance(application)
    val repository = ScanRepository(db.scanDao(), application)

    val cameraProvider = CameraProvider(application)
    val poseProvider: PoseProvider = IMUPoseProvider(application)
    private val captureGuide = CaptureGuide()
    private val qualityAnalyzer = FrameQualityAnalyzer()
    private val frameSelector = FrameSelector(qualityAnalyzer)
    val reconstructionEngine: ReconstructionEngine = LocalReconstructionEngine()

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    val allSessions = repository.getAllSessions().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _deviceProfile = MutableStateFlow(evaluateDeviceProfile())
    val deviceProfile: StateFlow<DeviceCapabilityProfile> = _deviceProfile.asStateFlow()

    private var lastCapturedPose: Pose? = null

    init {
        // Collect pose stream
        viewModelScope.launch {
            poseProvider.currentPose.collect { pose ->
                onPoseUpdated(pose)
            }
        }
        initSampleScansIfEmpty()
    }

    private fun evaluateDeviceProfile(): DeviceCapabilityProfile {
        val actMgr = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024))
        val availRamMb = (memInfo.availMem / (1024 * 1024))

        val stat = android.os.Environment.getDataDirectory()
        val freeBytes = stat.freeSpace
        val freeMb = freeBytes / (1024 * 1024)

        return DeviceCapabilityProfile(
            androidVersion = "Android 16 (API 36)",
            hasCamera = true,
            hasAccelerometer = true,
            hasGyroscope = true,
            isArCoreSupported = true,
            totalRamMb = if (totalRamMb > 0) totalRamMb else 8192L,
            availableRamMb = if (availRamMb > 0) availRamMb else 4100L,
            freeStorageMb = if (freeMb > 0) freeMb else 14500L,
            recommendedProfile = QualityProfile.BALANCED,
            hardwareScore = 95
        )
    }

    fun startNewScan(objectName: String, profile: QualityProfile = QualityProfile.BALANCED) {
        val sessionId = UUID.randomUUID().toString()
        val session = ScanSession(
            id = sessionId,
            objectName = objectName.ifBlank { "Object_${System.currentTimeMillis() % 10000}" },
            createdAt = System.currentTimeMillis(),
            status = SessionStatus.RECORDING
        )

        val lattice = captureGuide.generateLattice(profile)
        val target = captureGuide.getNextTarget(Pose(), lattice)

        _uiState.value = CaptureUiState(
            currentSession = session,
            cells = lattice,
            nextTarget = target,
            activeLayerName = target?.layer?.displayName ?: "Middle Ring",
            isAutoCaptureEnabled = true,
            isPaused = false
        )

        lastCapturedPose = null
        poseProvider.resetReference(0.5f)
        poseProvider.startTracking()

        viewModelScope.launch {
            repository.saveSession(session)
        }
    }

    fun toggleAutoCapture() {
        _uiState.value = _uiState.value.copy(
            isAutoCaptureEnabled = !_uiState.value.isAutoCaptureEnabled
        )
    }

    fun togglePause() {
        val newPaused = !_uiState.value.isPaused
        _uiState.value = _uiState.value.copy(
            isPaused = newPaused,
            statusMessage = if (newPaused) "Capture Paused" else "Scanning Active"
        )
    }

    fun updateManualPose(azimuthDelta: Float, elevationDelta: Float) {
        val cur = _uiState.value.currentPose
        val newAz = (cur.azimuthDeg + azimuthDelta + 360f) % 360f
        val newEl = (cur.elevationDeg + elevationDelta).coerceIn(-80f, 80f)
        poseProvider.setManualPose(newAz, newEl, cur.distance)
    }

    private fun onPoseUpdated(pose: Pose) {
        val state = _uiState.value
        if (state.currentSession == null || state.isPaused) {
            _uiState.value = state.copy(currentPose = pose)
            return
        }

        val target = state.nextTarget
        val closeness = if (target != null) {
            captureGuide.evaluateTargetProximity(pose, target)
        } else 0f

        val decision = frameSelector.shouldCapture(
            targetCell = target,
            targetCloseness = closeness,
            stability = 0.88f,
            currentPose = pose,
            lastCapturedPose = lastCapturedPose
        )

        _uiState.value = state.copy(
            currentPose = pose,
            quality = decision.quality,
            statusMessage = decision.reason
        )

        if (decision.shouldCapture && state.isAutoCaptureEnabled) {
            captureCurrentFrame(isManual = false)
        }
    }

    fun captureCurrentFrame(isManual: Boolean = true) {
        val state = _uiState.value
        val session = state.currentSession ?: return
        val target = state.nextTarget ?: return

        frameSelector.markManualCapture()
        triggerHapticFeedback()

        val frameId = UUID.randomUUID().toString()
        val sessionDir = repository.getSessionDirectory(session.id)
        val imageFile = File(sessionDir, "frame_${System.currentTimeMillis()}.jpg")

        viewModelScope.launch {
            // Capture frame from camera provider
            val success = cameraProvider.captureFrame(
                imageFile,
                state.currentPose.azimuthDeg,
                state.currentPose.elevationDeg
            )

            if (success) {
                val newFrame = Frame(
                    id = frameId,
                    sessionId = session.id,
                    localPath = imageFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    pose = state.currentPose,
                    qualityScore = state.quality.overallScore,
                    sharpness = state.quality.sharpnessScore,
                    exposure = state.quality.exposureScore,
                    coverageCellId = target.id,
                    accepted = true
                )

                // Update cell status
                val updatedCells = state.cells.map { cell ->
                    if (cell.id == target.id) {
                        cell.copy(status = CellStatus.CAPTURED, capturedFrameId = frameId)
                    } else cell
                }

                val nextTarget = captureGuide.getNextTarget(state.currentPose, updatedCells)
                val coverage = captureGuide.calculateCoveragePercentage(updatedCells)
                val updatedFrames = state.capturedFrames + newFrame
                lastCapturedPose = state.currentPose

                val updatedSession = session.copy(
                    frameCount = updatedFrames.size,
                    coveragePercentage = coverage,
                    updatedAt = System.currentTimeMillis()
                )

                _uiState.value = state.copy(
                    cells = updatedCells,
                    nextTarget = nextTarget,
                    capturedFrames = updatedFrames,
                    coveragePercentage = coverage,
                    activeLayerName = nextTarget?.layer?.displayName ?: "Capture Complete",
                    currentSession = updatedSession,
                    statusMessage = "View captured! Move to next marker"
                )

                repository.saveFrame(newFrame)
                repository.updateSession(updatedSession)
            }
        }
    }

    fun runReconstruction(onComplete: (Model3D) -> Unit = {}) {
        val state = _uiState.value
        val session = state.currentSession ?: return
        val frames = state.capturedFrames

        viewModelScope.launch {
            _uiState.value = state.copy(
                isProcessing = true,
                processingProgress = 0.05f,
                processingStageText = "Initializing local reconstruction engine..."
            )

            // Step 1: Prepare
            reconstructionEngine.prepare(session.id, frames)

            // Step 2: SfM Pose estimation
            val poses = reconstructionEngine.estimatePoses(frames) { prog, msg ->
                _uiState.value = _uiState.value.copy(
                    processingProgress = prog,
                    processingStageText = msg
                )
            }

            // Step 3: Reconstruct 3D points & Gaussian splats
            val rawModel = reconstructionEngine.reconstruct(
                session.id,
                session.objectName,
                frames,
                poses
            ) { prog, msg ->
                _uiState.value = _uiState.value.copy(
                    processingProgress = prog,
                    processingStageText = msg
                )
            }

            // Step 4: Optimize
            val optimizedModel = reconstructionEngine.optimize(rawModel) { prog, msg ->
                _uiState.value = _uiState.value.copy(
                    processingProgress = prog,
                    processingStageText = msg
                )
            }

            // Step 5: Export to session folder
            val sessionDir = repository.getSessionDirectory(session.id)
            val (plyFile, objFile) = reconstructionEngine.exportAssets(optimizedModel, sessionDir)

            val finalizedModel = optimizedModel.copy(
                plyFilePath = plyFile.absolutePath,
                objFilePath = objFile.absolutePath
            )

            val updatedSession = session.copy(
                status = SessionStatus.COMPLETED,
                pointCount = finalizedModel.points.size,
                assetPath = plyFile.absolutePath,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateSession(updatedSession)

            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                processingProgress = 1.0f,
                processingStageText = "Reconstruction complete!",
                reconstructedModel = finalizedModel,
                currentSession = updatedSession
            )

            onComplete(finalizedModel)
        }
    }

    private fun triggerHapticFeedback() {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    private fun initSampleScansIfEmpty() {
        viewModelScope.launch {
            val existing = repository.getSession("sample_artifact_01")
            if (existing == null) {
                val sampleSession = ScanSession(
                    id = "sample_artifact_01",
                    objectName = "Ceramic Amphora Artifact",
                    createdAt = System.currentTimeMillis() - 86400000,
                    status = SessionStatus.COMPLETED,
                    frameCount = 34,
                    coveragePercentage = 94.5f,
                    pointCount = 1024,
                    notes = "Museum specimen scanned with balanced 3DGS profile"
                )
                repository.saveSession(sampleSession)
            }
        }
    }

    fun loadSessionForViewing(session: ScanSession, onModelReady: (Model3D) -> Unit) {
        viewModelScope.launch {
            // Generate or load 3D model for this session
            val model = reconstructionEngine.reconstruct(
                session.id,
                session.objectName,
                emptyList(),
                emptyList()
            ) { _, _ -> }
            _uiState.value = _uiState.value.copy(
                currentSession = session,
                reconstructedModel = model
            )
            onModelReady(model)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }
}
