package com.example.engine.pose

import com.example.model.Pose
import kotlinx.coroutines.flow.StateFlow

enum class TrackingState {
    INITIALIZING,
    TRACKING_GOOD,
    TRACKING_DEGRADED,
    LOST
}

interface PoseProvider {
    val currentPose: StateFlow<Pose>
    val trackingState: StateFlow<TrackingState>

    fun startTracking()
    fun stopTracking()
    fun resetReference(objectDistanceMeters: Float = 0.5f)
    fun setManualPose(azimuthDeg: Float, elevationDeg: Float, distance: Float)
}
