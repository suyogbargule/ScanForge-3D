package com.example.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    fun distanceTo(other: Vector3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0.0001f) Vector3(x / len, y / len, z / len) else Vector3(0f, 1f, 0f)
    }
}

data class Quaternion(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val w: Float = 1f
)

data class Pose(
    val position: Vector3 = Vector3(0f, 0f, 1.2f),
    val rotation: Quaternion = Quaternion(),
    val azimuthDeg: Float = 0f,      // 0..360 around object
    val elevationDeg: Float = 0f,    // -90 (bottom) to +90 (top)
    val distance: Float = 0.5f,      // distance to object center (meters)
    val confidence: Float = 0.95f,
    val source: String = "IMU + Optical Fusion"
) {
    companion object {
        fun fromSpherical(azimuthDeg: Float, elevationDeg: Float, radius: Float = 0.5f): Pose {
            val azRad = Math.toRadians(azimuthDeg.toDouble()).toFloat()
            val elRad = Math.toRadians(elevationDeg.toDouble()).toFloat()
            val x = radius * cos(elRad) * sin(azRad)
            val y = radius * sin(elRad)
            val z = radius * cos(elRad) * cos(azRad)
            return Pose(
                position = Vector3(x, y, z),
                azimuthDeg = azimuthDeg,
                elevationDeg = elevationDeg,
                distance = radius
            )
        }
    }
}
