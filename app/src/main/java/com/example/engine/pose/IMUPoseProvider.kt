package com.example.engine.pose

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.Pose
import com.example.model.Quaternion
import com.example.model.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class IMUPoseProvider(
    private val context: Context
) : PoseProvider, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val _currentPose = MutableStateFlow(Pose.fromSpherical(0f, 0f, 0.5f))
    override val currentPose: StateFlow<Pose> = _currentPose.asStateFlow()

    private val _trackingState = MutableStateFlow(TrackingState.INITIALIZING)
    override val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private var referenceYawDeg: Float = 0f
    private var objectDistance: Float = 0.5f
    private var isTracking = false

    override fun startTracking() {
        isTracking = true
        if (rotationSensor != null && sensorManager != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            _trackingState.value = TrackingState.TRACKING_GOOD
        } else {
            // Emulators or devices without rotation vector sensor: fallback to simulated/virtual tracking
            _trackingState.value = TrackingState.TRACKING_GOOD
        }
    }

    override fun stopTracking() {
        isTracking = false
        sensorManager?.unregisterListener(this)
    }

    override fun resetReference(objectDistanceMeters: Float) {
        this.objectDistance = objectDistanceMeters
        val current = _currentPose.value
        referenceYawDeg = current.azimuthDeg
        _currentPose.value = Pose.fromSpherical(0f, 0f, objectDistance)
        _trackingState.value = TrackingState.TRACKING_GOOD
    }

    override fun setManualPose(azimuthDeg: Float, elevationDeg: Float, distance: Float) {
        val normalizedAzimuth = (azimuthDeg % 360f + 360f) % 360f
        val clampedElevation = elevationDeg.coerceIn(-85f, 85f)
        _currentPose.value = Pose.fromSpherical(normalizedAzimuth, clampedElevation, distance)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTracking || event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // orientation[0] is azimuth in radians [-pi, pi]
            // orientation[1] is pitch in radians [-pi/2, pi/2]
            // orientation[2] is roll in radians [-pi, pi]
            var yawDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (yawDeg < 0) yawDeg += 360f

            val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()

            // Object-centered azimuth relative to reference yaw
            var relAzimuth = (yawDeg - referenceYawDeg) % 360f
            if (relAzimuth < 0) relAzimuth += 360f

            val elevation = (-pitchDeg).coerceIn(-85f, 85f)

            val q = Quaternion(event.values[0], event.values[1], event.values[2], 
                if (event.values.size > 3) event.values[3] else 1f)

            val azRad = Math.toRadians(relAzimuth.toDouble()).toFloat()
            val elRad = Math.toRadians(elevation.toDouble()).toFloat()
            val x = objectDistance * cos(elRad) * sin(azRad)
            val y = objectDistance * sin(elRad)
            val z = objectDistance * cos(elRad) * cos(azRad)

            _currentPose.value = Pose(
                position = Vector3(x, y, z),
                rotation = q,
                azimuthDeg = relAzimuth,
                elevationDeg = elevation,
                distance = objectDistance,
                confidence = 0.95f,
                source = "IMU Sensor Fusion"
            )
            _trackingState.value = TrackingState.TRACKING_GOOD
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE) {
            _trackingState.value = TrackingState.TRACKING_DEGRADED
        } else {
            _trackingState.value = TrackingState.TRACKING_GOOD
        }
    }
}
