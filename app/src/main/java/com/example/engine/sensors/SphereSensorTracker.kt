package com.example.engine.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import com.example.model.SensorMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class OrientationSnapshot(
    val viewMatrix4x4: FloatArray,      // 16-element OpenGL view matrix (with -yawOffset applied)
    val rotationMatrix3x3: FloatArray,  // 9-element row-major device->world matrix (with -yawOffset applied)
    val cameraForward: FloatArray,      // [-R[2], -R[5], -R[8]] unit vector in yaw-offset world frame
    val quaternion: FloatArray,         // [x, y, z, w]
    val rawHeadingDeg: Float,           // un-offset compass/game heading in degrees 0..360
    val pitchDeg: Float,                // camera elevation (-90..+90)
    val rollDeg: Float,                 // phone horizon tilt (-180..+180)
    val gyroMagnitudeRadSec: Float,     // angular speed rad/s (Section 17.3 steady < 0.05)
    val isLevel: Boolean,               // gravity-based level check for Start screen
    val activeSensorLabel: String,
    val hasLiveSensor: Boolean
)

/**
 * Implements Section 17 & 22.4 sensor logic with full device-diversity fallback chain:
 * 1. TYPE_GAME_ROTATION_VECTOR (preferred indoors, avoids magnetic interference)
 * 2. TYPE_ROTATION_VECTOR (or forced when ABSOLUTE_NORTH selected)
 * 3. TYPE_ACCELEROMETER + TYPE_MAGNETIC_FIELD
 * 4. Manual mode fallback when no orientation sensors exist
 */
class SphereSensorTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val gameRotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var isTracking = false
    private var currentMode = SensorMode.AUTO_GAME_VECTOR
    private var yawOffsetDeg: Float = 0f

    private val accelReading = FloatArray(3)
    private val magReading = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private var lastGyroMagnitude = 0f
    private var lastRawFwd: FloatArray? = null
    private var lastSensorTimestampNs = 0L

    private val rawR4 = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val rawR3 = FloatArray(9).apply {
        this[0] = 1f; this[4] = 1f; this[8] = 1f
    }
    private val rawQuat = floatArrayOf(0f, 0f, 0f, 1f)

    // Manual drag fallback orientation (yaw, pitch)
    private var manualYawDeg = 0f
    private var manualPitchDeg = 0f

    private var onSnapshotListener: ((OrientationSnapshot) -> Unit)? = null

    fun hasGameRotationVector(): Boolean = gameRotationSensor != null
    fun hasRotationVector(): Boolean = rotationSensor != null
    fun hasGyroscope(): Boolean = gyroSensor != null
    fun hasAccelAndMag(): Boolean = accelSensor != null && magSensor != null

    fun setYawOffset(offsetDeg: Float) {
        yawOffsetDeg = ((offsetDeg % 360f) + 360f) % 360f
        emitSnapshot()
    }

    fun getYawOffset(): Float = yawOffsetDeg

    fun anchorYawToCurrentHeading(): Float {
        val fwd = floatArrayOf(-rawR3[2], -rawR3[5], -rawR3[8])
        val horizLen = sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1])
        val heading = if (horizLen > 0.1f) {
            ((Math.toDegrees(atan2(fwd[0].toDouble(), fwd[1].toDouble())).toFloat()) + 360f) % 360f
        } else {
            yawOffsetDeg
        }
        yawOffsetDeg = heading
        emitSnapshot()
        return heading
    }

    fun setManualDelta(yawDeltaDeg: Float, pitchDeltaDeg: Float) {
        manualYawDeg = (manualYawDeg + yawDeltaDeg + 360f) % 360f
        manualPitchDeg = (manualPitchDeg + pitchDeltaDeg).coerceIn(-89.5f, 89.5f)
        updateRawFromManualAngles(manualYawDeg, manualPitchDeg)
        lastGyroMagnitude = 0f
        emitSnapshot()
    }

    fun start(mode: SensorMode, listener: (OrientationSnapshot) -> Unit) {
        stop()
        currentMode = mode
        onSnapshotListener = listener
        isTracking = true

        if (sensorManager == null || mode == SensorMode.MANUAL) {
            updateRawFromManualAngles(manualYawDeg, manualPitchDeg)
            emitSnapshot()
            return
        }

        // Register gyroscope if present (for steady check < 0.05 rad/s)
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Register orientation sensor per fallback policy (Section 22.4)
        val registeredPrimary = when (mode) {
            SensorMode.AUTO_GAME_VECTOR -> {
                val primary = gameRotationSensor ?: rotationSensor
                if (primary != null) {
                    sensorManager.registerListener(this, primary, SensorManager.SENSOR_DELAY_GAME)
                    true
                } else false
            }
            SensorMode.ABSOLUTE_NORTH -> {
                val primary = rotationSensor ?: gameRotationSensor
                if (primary != null) {
                    sensorManager.registerListener(this, primary, SensorManager.SENSOR_DELAY_GAME)
                    true
                } else false
            }
            SensorMode.MANUAL -> false
        }

        if (!registeredPrimary) {
            if (accelSensor != null && magSensor != null) {
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
                sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME)
            } else {
                // No usable orientation sensor -> Manual mode
                updateRawFromManualAngles(manualYawDeg, manualPitchDeg)
                emitSnapshot()
            }
        }
    }

    fun stop() {
        isTracking = false
        sensorManager?.unregisterListener(this)
        onSnapshotListener = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTracking || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val wx = event.values[0]
                val wy = event.values[1]
                val wz = event.values[2]
                lastGyroMagnitude = sqrt(wx * wx + wy * wy + wz * wz)
            }

            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rawR4, event.values)
                SensorManager.getRotationMatrixFromVector(rawR3, event.values)
                SensorManager.getQuaternionFromVector(rawQuat, event.values)

                // If phone lacks hardware gyroscope, estimate angular speed from forward vector delta (Section 22.4)
                if (gyroSensor == null) {
                    estimateAngularRateFromMatrix(event.timestamp)
                }
                emitSnapshot()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelReading, 0, 3)
                hasAccel = true
                if (hasMag && gameRotationSensor == null && rotationSensor == null) {
                    if (SensorManager.getRotationMatrix(rawR3, null, accelReading, magReading)) {
                        SensorManager.getRotationMatrix(rawR4, null, accelReading, magReading)
                        rotationMatrix3ToQuaternion(rawR3, rawQuat)
                        if (gyroSensor == null) {
                            estimateAngularRateFromMatrix(event.timestamp)
                        }
                        emitSnapshot()
                    }
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magReading, 0, 3)
                hasMag = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not required
    }

    private fun estimateAngularRateFromMatrix(timestampNs: Long) {
        val fwd = floatArrayOf(-rawR3[2], -rawR3[5], -rawR3[8])
        val prev = lastRawFwd
        if (prev != null && lastSensorTimestampNs > 0L && timestampNs > lastSensorTimestampNs) {
            val dtSec = (timestampNs - lastSensorTimestampNs) / 1_000_000_000f
            if (dtSec > 0.001f && dtSec < 0.5f) {
                val dot = (fwd[0] * prev[0] + fwd[1] * prev[1] + fwd[2] * prev[2]).coerceIn(-1f, 1f)
                val angleRad = kotlin.math.acos(dot)
                // Loosen steady estimate slightly via smoothing for non-gyro phones
                lastGyroMagnitude = (lastGyroMagnitude * 0.5f) + ((angleRad / dtSec) * 0.5f)
            }
        }
        lastRawFwd = fwd
        lastSensorTimestampNs = timestampNs
    }

    private fun emitSnapshot() {
        val listener = onSnapshotListener ?: return

        // Apply -yawOffsetDeg rotation around world Z axis (Section 23.2 Step 3)
        // world-to-device view matrix: view = rawR4 * Rz(yawOffset)
        val rotZ4 = FloatArray(16)
        Matrix.setIdentityM(rotZ4, 0)
        // Rotating target world by -yawOffset is equivalent to rotating camera view by +yawOffset around Z
        // Notice in OpenGL Matrix.setRotateM takes degrees around axis (0,0,1)
        Matrix.rotateM(rotZ4, 0, yawOffsetDeg, 0f, 0f, 1f)

        val viewMatrix = FloatArray(16)
        Matrix.multiplyMM(viewMatrix, 0, rawR4, 0, rotZ4, 0)

        // Also compute offset 3x3 row-major matrix R_offset = Rz(-yawOffset) * rawR3
        val rad = Math.toRadians(-yawOffsetDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        // Wait: rotating world vector v by -yawOffset around Z:
        // Wait! Let's verify sign: dir(0, 0) = (0, 1, 0) [North]. dir(90, 0) = (1, 0, 0) [East].
        // So positive yaw is CLOCKWISE from +Y toward +X!
        // Clockwise rotation by angle theta around +Z maps (x, y) -> (x*cos(t) + y*sin(t), -x*sin(t) + y*cos(t)).
        // Let's check: for t = 90°, (0, 1) -> (1, 0). Correct!
        // Therefore, subtracting yawOffsetDeg (clockwise by -yawOffsetDeg) maps:
        // x' = x*cos(-off) + y*sin(-off) = x*c - y*s (where c=cos(off), s=sin(off))
        // y' = -x*sin(-off) + y*cos(-off) = x*s + y*c
        val offRad = Math.toRadians(yawOffsetDeg.toDouble())
        val oc = cos(offRad).toFloat()
        val os = sin(offRad).toFloat()

        // R_offset = R_cw(-yawOffset) * rawR3
        val offsetR3 = FloatArray(9)
        // Row 0 of R_cw(-off) is [oc, -os, 0]
        offsetR3[0] = oc * rawR3[0] - os * rawR3[3]
        offsetR3[1] = oc * rawR3[1] - os * rawR3[4]
        offsetR3[2] = oc * rawR3[2] - os * rawR3[5]
        // Row 1 of R_cw(-off) is [os, oc, 0]
        offsetR3[3] = os * rawR3[0] + oc * rawR3[3]
        offsetR3[4] = os * rawR3[1] + oc * rawR3[4]
        offsetR3[5] = os * rawR3[2] + oc * rawR3[5]
        // Row 2 is [0, 0, 1]
        offsetR3[6] = rawR3[6]
        offsetR3[7] = rawR3[7]
        offsetR3[8] = rawR3[8]

        // Build exact matching 4x4 column-major view matrix for OpenGL from offsetR3
        // OpenGL view = transpose(offsetR3) padded to 4x4. Because offsetR3 is row-major, passing its
        // rows directly as OpenGL column-major gives transpose(offsetR3)!
        val glView = floatArrayOf(
            offsetR3[0], offsetR3[1], offsetR3[2], 0f,
            offsetR3[3], offsetR3[4], offsetR3[5], 0f,
            offsetR3[6], offsetR3[7], offsetR3[8], 0f,
            0f, 0f, 0f, 1f
        )

        // Back camera looks along device -Z: fwd = [-R[2], -R[5], -R[8]]
        val fwd = floatArrayOf(-offsetR3[2], -offsetR3[5], -offsetR3[8])

        // Raw un-offset heading for anchor
        val rawFwd = floatArrayOf(-rawR3[2], -rawR3[5], -rawR3[8])
        val rawHeading = ((Math.toDegrees(atan2(rawFwd[0].toDouble(), rawFwd[1].toDouble())).toFloat()) + 360f) % 360f

        // Pitch (elevation of camera forward vector)
        val pitchDeg = Math.toDegrees(kotlin.math.asin(fwd[2].coerceIn(-1f, 1f).toDouble())).toFloat()

        // Roll: orientation of device +X (right) relative to horizontal plane
        val rightZ = offsetR3[6]
        val upZ = offsetR3[7]
        val rollDeg = Math.toDegrees(atan2(rightZ.toDouble(), upZ.toDouble())).toFloat()

        val isLevel = abs(pitchDeg) < 12f && abs(rollDeg) < 10f

        val hasLive = currentMode != SensorMode.MANUAL &&
            (gameRotationSensor != null || rotationSensor != null || (accelSensor != null && magSensor != null))

        val label = when {
            currentMode == SensorMode.MANUAL -> "Manual Mode"
            currentMode == SensorMode.AUTO_GAME_VECTOR && gameRotationSensor != null -> "Game Rotation Vector"
            rotationSensor != null -> "Rotation Vector"
            accelSensor != null && magSensor != null -> "Accel + Mag Fallback"
            else -> "Manual Mode"
        }

        val offsetQuat = FloatArray(4)
        rotationMatrix3ToQuaternion(offsetR3, offsetQuat)

        listener(
            OrientationSnapshot(
                viewMatrix4x4 = glView,
                rotationMatrix3x3 = offsetR3,
                cameraForward = fwd,
                quaternion = offsetQuat,
                rawHeadingDeg = rawHeading,
                pitchDeg = pitchDeg,
                rollDeg = rollDeg,
                gyroMagnitudeRadSec = lastGyroMagnitude,
                isLevel = isLevel,
                activeSensorLabel = label,
                hasLiveSensor = hasLive
            )
        )
    }

    private fun updateRawFromManualAngles(yawDeg: Float, pitchDeg: Float) {
        val fwd = com.example.model.SphereTargetBuilder.dir(yawDeg, pitchDeg)
        val upRef = if (abs(pitchDeg) > 85f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(0f, 0f, 1f)
        val right = com.example.model.SphereTargetBuilder.normalize(
            com.example.model.SphereTargetBuilder.cross(fwd, upRef)
        )
        val up = com.example.model.SphereTargetBuilder.normalize(
            com.example.model.SphereTargetBuilder.cross(right, fwd)
        )
        rawR3[0] = right[0]; rawR3[1] = up[0]; rawR3[2] = -fwd[0]
        rawR3[3] = right[1]; rawR3[4] = up[1]; rawR3[5] = -fwd[1]
        rawR3[6] = right[2]; rawR3[7] = up[2]; rawR3[8] = -fwd[2]
        rotationMatrix3ToQuaternion(rawR3, rawQuat)
    }

    private fun rotationMatrix3ToQuaternion(m: FloatArray, q: FloatArray) {
        val trace = m[0] + m[4] + m[8]
        if (trace > 0f) {
            val s = 0.5f / sqrt(trace + 1f)
            q[3] = 0.25f / s
            q[0] = (m[7] - m[5]) * s
            q[1] = (m[2] - m[6]) * s
            q[2] = (m[3] - m[1]) * s
        } else if (m[0] > m[4] && m[0] > m[8]) {
            val s = 2f * sqrt(1f + m[0] - m[4] - m[8])
            q[3] = (m[7] - m[5]) / s
            q[0] = 0.25f * s
            q[1] = (m[1] + m[3]) / s
            q[2] = (m[2] + m[6]) / s
        } else if (m[4] > m[8]) {
            val s = 2f * sqrt(1f + m[4] - m[0] - m[8])
            q[3] = (m[2] - m[6]) / s
            q[0] = (m[1] + m[3]) / s
            q[1] = 0.25f * s
            q[2] = (m[5] + m[7]) / s
        } else {
            val s = 2f * sqrt(1f + m[8] - m[0] - m[4])
            q[3] = (m[3] - m[1]) / s
            q[0] = (m[2] + m[6]) / s
            q[1] = (m[5] + m[7]) / s
            q[2] = 0.25f * s
        }
    }
}
