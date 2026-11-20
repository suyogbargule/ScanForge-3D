package com.example.model

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class SphereLayer(val displayName: String, val pitchDeg: Float, val targetCount: Int) {
    ZENITH("Zenith", 90f, 1),
    UPPER("Upper", 45f, 8),
    CENTRE("Centre", 0f, 12),
    LOWER("Lower", -45f, 8),
    NADIR("Nadir", -90f, 1)
}

enum class TargetStatus {
    REMAINING,
    COMPLETED,
    NEEDS_RETAKE
}

enum class CaptureState {
    CALIBRATING, // Screen 1: START button, level check, AE/AF/AWB lock on tap
    SEARCHING,   // Screen 2-3: Arrow guides to target, tile glides in
    ALIGNED,     // Target inside hole (< 3° enter, > 5° exit)
    HOLDING,     // Steady (< 0.05 rad/s), ring fills over 0.7-1.0s
    CAPTURING    // ImageCapture + Quality Gate running
}

enum class GuidanceMode(val label: String) {
    GUIDED("Guided Order (Recommended)"),
    FREE("Free Order (Nearest Target)")
}

enum class SensorMode(val label: String) {
    AUTO_GAME_VECTOR("Game Rotation Vector (Recommended)"),
    ABSOLUTE_NORTH("Rotation Vector (Compass North)"),
    MANUAL("Manual Mode (No Gyro / Fallback)")
}

enum class BlurThresholdPreset(val label: String, val minLaplacianVariance: Double) {
    LENIENT("Lenient (25)", 25.0),
    STANDARD("Standard (55)", 55.0),
    STRICT("Strict (100)", 100.0)
}

enum class SessionStateStatus(val label: String) {
    INCOMPLETE("Incomplete"),
    READY("Ready"),
    EXPORTED("Exported")
}

data class CaptureTarget(
    val id: Int,                    // 1..30 matching Section 15 table
    val layer: SphereLayer,
    val yawDeg: Float,              // relative to first shot (0..330)
    val pitchDeg: Float,            // +90, +45, 0, -45, -90
    val status: TargetStatus = TargetStatus.REMAINING,
    val imagePath: String? = null,
    val thumbPath: String? = null,
    val rotationMatrix: FloatArray? = null, // 3x3 row-major device->world
    val quaternion: FloatArray? = null,     // [x, y, z, w]
    val angleErrorDeg: Float = 0f,
    val blurScore: Float = 0f,
    val exposureWarning: Boolean = false,
    val textureWarning: Boolean = false,
    val qualityNote: String? = null,
    val timestamp: Long = 0L,
    val captureOrder: Int = 0,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val checksumSha256: String? = null
) {
    val fileName: String get() = "img_%02d.jpg".format(id)
    val thumbFileName: String get() = "thumb_%02d.jpg".format(id)

    fun directionVector(): FloatArray = SphereTargetBuilder.dir(yawDeg, pitchDeg)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CaptureTarget) return false
        return id == other.id &&
            layer == other.layer &&
            yawDeg == other.yawDeg &&
            pitchDeg == other.pitchDeg &&
            status == other.status &&
            imagePath == other.imagePath &&
            thumbPath == other.thumbPath &&
            angleErrorDeg == other.angleErrorDeg &&
            blurScore == other.blurScore &&
            exposureWarning == other.exposureWarning &&
            textureWarning == other.textureWarning &&
            qualityNote == other.qualityNote &&
            timestamp == other.timestamp &&
            captureOrder == other.captureOrder
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + status.hashCode()
        result = 31 * result + (imagePath?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

data class CameraFovInfo(
    val hFovDeg: Float = 52.0f,
    val vFovDeg: Float = 68.0f,
    val captureWidth: Int = 4000,
    val captureHeight: Int = 3000,
    val isEstimated: Boolean = true,
    val lensSummary: String = "Default 52° × 68°"
)

data class CoverageReport(
    val coverageFraction: Float = 0f,
    val minNeighborOverlap: Float = 0f,
    val gapCount: Int = 0,
    val gapTargetIds: List<Int> = emptyList(),
    val integrityOk: Boolean = false,
    val validFileCount: Int = 0,
    val missingIds: List<Int> = emptyList()
) {
    val coveragePercent: Float get() = coverageFraction * 100f
}

data class CaptureSettings(
    val guidanceMode: GuidanceMode = GuidanceMode.GUIDED,
    val sensorMode: SensorMode = SensorMode.AUTO_GAME_VECTOR,
    val holdDurationMs: Long = 800L,
    val blurPreset: BlurThresholdPreset = BlurThresholdPreset.STANDARD,
    val customHFovDeg: Float? = null,
    val customVFovDeg: Float? = null
)

data class CaptureSessionSummary(
    val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val yawOffsetDeg: Float,
    val status: SessionStateStatus,
    val completedCount: Int,
    val flaggedCount: Int,
    val coveragePercent: Float,
    val thumbnailPath: String?,
    val sessionDirPath: String,
    val fovHDeg: Float,
    val fovVDeg: Float
)

data class DeviceDiagnostics(
    val androidVersion: String,
    val apiLevel: Int,
    val hasBackCamera: Boolean,
    val hasGameRotationVector: Boolean,
    val hasRotationVector: Boolean,
    val hasGyroscope: Boolean,
    val hasAccelerometerAndMag: Boolean,
    val freeStorageMb: Long,
    val cameraFov: CameraFovInfo
)

object SphereTargetBuilder {

    /**
     * Exact 30 targets matching Sections 3, 15, and 17.2:
     * ID 1: Zenith (+90°)
     * IDs 2..9: Upper (+45°, every 45°)
     * IDs 10..21: Centre (0°, every 30°)
     * IDs 22..29: Lower (-45°, every 45°)
     * ID 30: Nadir (-90°)
     */
    fun buildTargets(): List<CaptureTarget> {
        val t = mutableListOf<CaptureTarget>()
        t += CaptureTarget(1, SphereLayer.ZENITH, 0f, 90f)
        repeat(8) { t += CaptureTarget(2 + it, SphereLayer.UPPER, it * 45f, 45f) }
        repeat(12) { t += CaptureTarget(10 + it, SphereLayer.CENTRE, it * 30f, 0f) }
        repeat(8) { t += CaptureTarget(22 + it, SphereLayer.LOWER, it * 45f, -45f) }
        t += CaptureTarget(30, SphereLayer.NADIR, 0f, -90f)
        return t
    }

    /**
     * Recommended capture order (Section 15):
     * Centre ring (10..21) -> Upper ring (2..9) -> Zenith (1) -> Lower ring (22..29) -> Nadir (30)
     */
    val recommendedOrderIds: List<Int> = buildList {
        addAll(10..21)
        addAll(2..9)
        add(1)
        addAll(22..29)
        add(30)
    }

    fun getNextGuidedTarget(targets: List<CaptureTarget>): CaptureTarget? {
        val byId = targets.associateBy { it.id }
        // First pick any remaining or needs-retake in recommended order
        for (id in recommendedOrderIds) {
            val target = byId[id]
            if (target != null && target.status != TargetStatus.COMPLETED) {
                return target
            }
        }
        return null
    }

    fun getNearestUncompletedTarget(
        targets: List<CaptureTarget>,
        cameraForward: FloatArray
    ): CaptureTarget? {
        return targets
            .filter { it.status != TargetStatus.COMPLETED }
            .minByOrNull { angleErrorDeg(cameraForward, it.directionVector()) }
    }

    /**
     * World frame (Android): X = east, Y = north, Z = up (Section 17.2)
     */
    fun dir(yawDeg: Float, pitchDeg: Float): FloatArray {
        val y = Math.toRadians(yawDeg.toDouble())
        val p = Math.toRadians(pitchDeg.toDouble())
        return floatArrayOf(
            (cos(p) * sin(y)).toFloat(),
            (cos(p) * cos(y)).toFloat(),
            sin(p).toFloat()
        )
    }

    fun angleErrorDeg(a: FloatArray, b: FloatArray): Float {
        val d = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(d.toDouble())).toFloat()
    }

    /**
     * Computes signed yaw and pitch guidance deltas from camera forward vector to target.
     * Positive yawDelta = turn right; positive pitchDelta = tilt up.
     * Near poles, yaw delta is suppressed (Section 17.2).
     */
    fun computeGuidanceDeltas(fwd: FloatArray, target: CaptureTarget): Pair<Float, Float> {
        val camPitch = Math.toDegrees(kotlin.math.asin(fwd[2].coerceIn(-1f, 1f).toDouble())).toFloat()
        val pitchDelta = target.pitchDeg - camPitch

        if (kotlin.math.abs(target.pitchDeg) >= 85f) {
            return 0f to pitchDelta
        }

        val camYaw = ((Math.toDegrees(atan2(fwd[0].toDouble(), fwd[1].toDouble())).toFloat()) + 360f) % 360f
        var yawDelta = (target.yawDeg - camYaw + 540f) % 360f - 180f
        if (yawDelta < -180f) yawDelta += 360f
        return yawDelta to pitchDelta
    }

    fun formatGuidanceHint(yawDeltaDeg: Float, pitchDeltaDeg: Float, targetPitchDeg: Float): String {
        val absYaw = kotlin.math.abs(yawDeltaDeg).toInt()
        val absPitch = kotlin.math.abs(pitchDeltaDeg).toInt()

        val pitchPart = when {
            absPitch < 4 -> null
            pitchDeltaDeg > 0 -> "tilt up $absPitch°"
            else -> "tilt down $absPitch°"
        }

        val yawPart = when {
            kotlin.math.abs(targetPitchDeg) >= 85f -> null
            absYaw < 4 -> null
            yawDeltaDeg > 0 -> "Turn right $absYaw°"
            else -> "Turn left $absYaw°"
        }

        return listOfNotNull(yawPart, pitchPart).joinToString(", ")
            .replaceFirstChar { it.uppercaseChar() }
            .ifEmpty { "Center on target" }
    }

    fun cross(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )

    fun normalize(v: FloatArray): FloatArray {
        val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (len < 1e-6f) return floatArrayOf(0f, 0f, 1f)
        return floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
    }
}
