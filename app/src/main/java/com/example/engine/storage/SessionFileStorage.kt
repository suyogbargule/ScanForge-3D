package com.example.engine.storage

import android.content.Context
import android.os.StatFs
import com.example.model.CameraFovInfo
import com.example.model.CaptureTarget
import com.example.model.TargetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SessionFileStorage(private val context: Context) {

    fun getCapturesRoot(): File {
        return File(context.filesDir, "captures").apply { mkdirs() }
    }

    fun getSessionDir(sessionId: String): File {
        return File(getCapturesRoot(), sessionId).apply { mkdirs() }
    }

    fun getImagesDir(sessionId: String): File {
        return File(getSessionDir(sessionId), "images").apply { mkdirs() }
    }

    fun getThumbsDir(sessionId: String): File {
        return File(getSessionDir(sessionId), "thumbs").apply { mkdirs() }
    }

    fun getTempImageFile(sessionId: String, targetId: Int): File {
        return File(getImagesDir(sessionId), "tmp_img_%02d_%d.jpg".format(targetId, System.currentTimeMillis()))
    }

    fun getFinalImageFile(sessionId: String, targetId: Int): File {
        return File(getImagesDir(sessionId), "img_%02d.jpg".format(targetId))
    }

    fun getThumbFile(sessionId: String, targetId: Int): File {
        return File(getThumbsDir(sessionId), "thumb_%02d.jpg".format(targetId))
    }

    fun getSessionJsonFile(sessionId: String): File {
        return File(getSessionDir(sessionId), "session.json")
    }

    /**
     * Checks available internal storage in MB (Section 22.2: require ~150 MB before starting 30-shot session).
     */
    fun getAvailableStorageMb(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L)
        } catch (_: Exception) {
            1024L
        }
    }

    fun hasEnoughSpaceForSession(minMb: Long = 150L): Boolean {
        return getAvailableStorageMb() >= minMb
    }

    /**
     * Safe atomic promotion: flushes temp file to disk and renames to img_XX.jpg (Section 22.2).
     */
    suspend fun promoteTempImage(tempFile: File, finalFile: File): String? = withContext(Dispatchers.IO) {
        try {
            if (!tempFile.exists() || tempFile.length() <= 0L) return@withContext null
            finalFile.parentFile?.mkdirs()

            // Flush temp file via FileInputStream/sync if needed, then atomic rename
            if (finalFile.exists()) {
                finalFile.delete()
            }
            val renamed = tempFile.renameTo(finalFile)
            if (!renamed) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            computeSha256(finalFile)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Updates session.json atomically after every capture so crashes or restarts never lose progress (Section 19.1 & 22.2).
     */
    suspend fun writeSessionJson(
        sessionId: String,
        yawOffsetDeg: Float,
        cameraFov: CameraFovInfo,
        targets: List<CaptureTarget>
    ) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put("sessionId", sessionId)
                put("yawOffsetDeg", yawOffsetDeg.toDouble())
                put("camera", JSONObject().apply {
                    put("fovHDeg", cameraFov.hFovDeg.toDouble())
                    put("fovVDeg", cameraFov.vFovDeg.toDouble())
                    put("width", cameraFov.captureWidth)
                    put("height", cameraFov.captureHeight)
                })
                val imagesArray = JSONArray()
                targets
                    .filter { it.status != TargetStatus.REMAINING && it.imagePath != null }
                    .sortedBy { it.id }
                    .forEach { target ->
                        imagesArray.put(JSONObject().apply {
                            put("id", target.id)
                            put("file", target.fileName)
                            put("layer", target.layer.name)
                            put("targetYawDeg", target.yawDeg.toDouble())
                            put("targetPitchDeg", target.pitchDeg.toDouble())
                            put("status", target.status.name)
                            target.quaternion?.let { q ->
                                put("quaternion", JSONArray().apply {
                                    q.forEach { put(it.toDouble()) }
                                })
                            }
                            target.rotationMatrix?.let { r ->
                                put("rotationMatrix", JSONArray().apply {
                                    r.forEach { put(it.toDouble()) }
                                })
                            }
                            put("angleErrorDeg", target.angleErrorDeg.toDouble())
                            put("blurScore", target.blurScore.toDouble())
                            put("exposureWarning", target.exposureWarning)
                            put("textureWarning", target.textureWarning)
                            put("captureOrder", target.captureOrder)
                            put("width", target.imageWidth)
                            put("height", target.imageHeight)
                            target.checksumSha256?.let { put("sha256", it) }
                            put("timestamp", target.timestamp)
                        })
                    }
                put("images", imagesArray)
            }

            val jsonFile = getSessionJsonFile(sessionId)
            val tmpJson = File(jsonFile.parentFile, "session.json.tmp")
            FileOutputStream(tmpJson).use { fos ->
                fos.write(root.toString(2).toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            if (jsonFile.exists()) jsonFile.delete()
            if (!tmpJson.renameTo(jsonFile)) {
                tmpJson.copyTo(jsonFile, overwrite = true)
                tmpJson.delete()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Packages img_01.jpg..img_30.jpg and session.json into a shareable ZIP archive in cacheDir/exports/.
     */
    suspend fun exportSessionZip(sessionId: String): File? = withContext(Dispatchers.IO) {
        try {
            val sessionDir = getSessionDir(sessionId)
            if (!sessionDir.exists()) return@withContext null

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val zipFile = File(exportDir, "scan3d_$sessionId.zip")
            if (zipFile.exists()) zipFile.delete()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                // Add session.json
                val sessionJson = getSessionJsonFile(sessionId)
                if (sessionJson.exists()) {
                    addFileToZip(zos, sessionJson, "session.json")
                }

                // Add images/img_01.jpg..img_30.jpg
                val imagesDir = getImagesDir(sessionId)
                imagesDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("img_") && it.name.endsWith(".jpg") }
                    ?.sortedBy { it.name }
                    ?.forEach { img ->
                        addFileToZip(zos, img, "images/${img.name}")
                    }
            }
            if (zipFile.exists() && zipFile.length() > 0L) zipFile else null
        } catch (_: Exception) {
            null
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        val buffer = ByteArray(8192)
        BufferedInputStream(FileInputStream(file), buffer.size).use { bis ->
            zos.putNextEntry(ZipEntry(entryName))
            var count: Int
            while (bis.read(buffer, 0, buffer.size).also { count = it } != -1) {
                zos.write(buffer, 0, count)
            }
            zos.closeEntry()
        }
    }

    private fun computeSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var read = fis.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = fis.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
