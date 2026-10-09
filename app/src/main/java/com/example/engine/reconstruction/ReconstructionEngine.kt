package com.example.engine.reconstruction

import com.example.model.Frame
import com.example.model.Model3D
import com.example.model.Pose
import com.example.model.ReconstructionEngineType
import java.io.File

interface ReconstructionEngine {
    val engineType: ReconstructionEngineType

    suspend fun prepare(sessionId: String, frames: List<Frame>): Boolean

    suspend fun estimatePoses(
        frames: List<Frame>,
        onProgress: (Float, String) -> Unit
    ): List<Pose>

    suspend fun reconstruct(
        sessionId: String,
        objectName: String,
        frames: List<Frame>,
        poses: List<Pose>,
        onProgress: (Float, String) -> Unit
    ): Model3D

    suspend fun optimize(
        model: Model3D,
        onProgress: (Float, String) -> Unit
    ): Model3D

    suspend fun exportAssets(
        model: Model3D,
        destinationDir: File
    ): Pair<File, File> // returns (plyFile, objFile)
}
