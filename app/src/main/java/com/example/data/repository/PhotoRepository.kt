package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.local.BackupRecord
import com.example.data.local.DiskVaultDao
import com.example.data.local.HardDiskTargetEntity
import com.example.data.local.TransferAuditLogEntity
import com.example.data.model.BackupDestinationType
import com.example.data.model.HardDiskTarget
import com.example.data.model.PhotoItem
import com.example.data.model.TransferLog
import com.example.util.CryptoUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class PhotoRepository(
    private val context: Context,
    private val dao: DiskVaultDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _rawPhotos = MutableStateFlow<List<PhotoItem>>(emptyList())

    // Merged state of photos with their Room DB backup status
    val photos: Flow<List<PhotoItem>> = combine(_rawPhotos, dao.getAllBackupRecords()) { photoList, records ->
        val recordMap = records.associateBy { it.photoId }
        photoList.map { photo ->
            val record = recordMap[photo.id]
            if (record != null) {
                photo.copy(
                    isBackedUp = true,
                    backupDestination = record.targetDiskName,
                    backedUpAt = record.backedUpAt,
                    isVerified = record.isVerified,
                    sha256Checksum = record.sha256.ifBlank { photo.sha256Checksum }
                )
            } else {
                photo.copy(isBackedUp = false, backupDestination = "", backedUpAt = 0L)
            }
        }
    }

    val auditLogs: Flow<List<TransferLog>> = dao.getAuditLogs().map { entities ->
        entities.map { e ->
            TransferLog(
                id = e.id,
                timestamp = e.timestamp,
                fileName = e.fileName,
                destination = e.destination,
                bytes = e.bytes,
                sha256 = e.sha256,
                isSuccess = e.isSuccess,
                message = e.message
            )
        }
    }

    val hardDiskTargets: Flow<List<HardDiskTarget>> = dao.getAllTargets().map { entities ->
        entities.map { e ->
            HardDiskTarget(
                id = e.id,
                name = e.name,
                uriString = e.uriString,
                type = try { BackupDestinationType.valueOf(e.targetType) } catch (_: Exception) { BackupDestinationType.USB_HARD_DISK },
                lastBackupTimestamp = e.lastBackupTimestamp,
                totalFilesBackedUp = e.totalFilesBackedUp,
                totalBytesBackedUp = e.totalBytesBackedUp
            )
        }
    }

    init {
        scope.launch {
            initSamplePhotosIfEmpty()
        }
    }

    suspend fun currentPhotosList(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val raw = _rawPhotos.value
        val records = mutableMapOf<String, BackupRecord>()
        raw.forEach { p ->
            val r = dao.getRecordForPhoto(p.id)
            if (r != null) records[p.id] = r
        }
        raw.map { p ->
            val r = records[p.id]
            if (r != null) {
                p.copy(
                    isBackedUp = true,
                    backupDestination = r.targetDiskName,
                    backedUpAt = r.backedUpAt,
                    isVerified = r.isVerified,
                    sha256Checksum = r.sha256.ifBlank { p.sha256Checksum }
                )
            } else {
                p
            }
        }
    }

    fun getPhotoById(id: String): PhotoItem? {
        return _rawPhotos.value.find { it.id == id }
    }

    fun openPhotoInputStream(photo: PhotoItem): InputStream? {
        return try {
            if (photo.uriString.startsWith("file://")) {
                val path = photo.uriString.removePrefix("file://")
                FileInputStream(File(path))
            } else if (photo.uriString.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(photo.uriString))
            } else {
                val f = File(photo.uriString)
                if (f.exists()) FileInputStream(f) else null
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed opening stream for photo ${photo.id}", e)
            null
        }
    }

    suspend fun addPickedPhotos(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val newItems = mutableListOf<PhotoItem>()
        val photosDir = File(context.filesDir, "vault_photos").apply { mkdirs() }

        for (uri in uris) {
            try {
                var fileName = "photo_${System.currentTimeMillis()}_${newItems.size}.jpg"
                var size = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }

                // Copy to local app private storage for persistent access without URI permission expiration
                val localFile = File(photosDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val fileSize = if (size > 0) size else localFile.length()
                val sha256 = CryptoUtil.calculateSha256(FileInputStream(localFile))

                val photoItem = PhotoItem(
                    id = "p_${System.currentTimeMillis()}_${newItems.size}",
                    title = fileName,
                    uriString = "file://${localFile.absolutePath}",
                    dateAdded = System.currentTimeMillis(),
                    sizeBytes = fileSize,
                    mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
                    width = 2400,
                    height = 1600,
                    sha256Checksum = sha256,
                    isSample = false
                )
                newItems.add(photoItem)
            } catch (e: Exception) {
                Log.e("PhotoRepository", "Failed importing uri $uri", e)
            }
        }

        if (newItems.isNotEmpty()) {
            _rawPhotos.value = newItems + _rawPhotos.value
        }
    }

    suspend fun recordPhotoBackup(
        photoId: String,
        fileName: String,
        fileSize: Long,
        sha256: String,
        targetDiskName: String,
        destinationPath: String
    ) = withContext(Dispatchers.IO) {
        val record = BackupRecord(
            photoId = photoId,
            fileName = fileName,
            fileSize = fileSize,
            sha256 = sha256,
            backedUpAt = System.currentTimeMillis(),
            targetDiskName = targetDiskName,
            destinationPath = destinationPath,
            isVerified = true
        )
        dao.insertBackupRecord(record)

        val log = TransferAuditLogEntity(
            timestamp = System.currentTimeMillis(),
            fileName = fileName,
            destination = targetDiskName,
            bytes = fileSize,
            sha256 = sha256,
            isSuccess = true,
            message = "Bit-for-bit local copy verified to $destinationPath"
        )
        dao.insertAuditLog(log)
    }

    suspend fun saveHardDiskTarget(
        id: String,
        name: String,
        uriString: String,
        type: BackupDestinationType
    ) = withContext(Dispatchers.IO) {
        val entity = HardDiskTargetEntity(
            id = id,
            name = name,
            uriString = uriString,
            targetType = type.name,
            lastBackupTimestamp = System.currentTimeMillis(),
            totalFilesBackedUp = 0,
            totalBytesBackedUp = 0L
        )
        dao.insertTarget(entity)
    }

    suspend fun clearAllAuditLogs() = withContext(Dispatchers.IO) {
        dao.clearAuditLogs()
    }

    private suspend fun initSamplePhotosIfEmpty() = withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "vault_photos").apply { mkdirs() }
        val sampleItems = mutableListOf<PhotoItem>()

        val samples = listOf(
            SampleDef("DSC_8042_Alpine_Sunset.jpg", "Alpine Sunset & Peaks", 2800, 1800, 0xFF0D1B2A.toInt(), 0xFF00E5FF.toInt()),
            SampleDef("IMG_2026_Family_Holiday.jpg", "Family Holiday Beach", 2560, 1440, 0xFF0A2540.toInt(), 0xFF10B981.toInt()),
            SampleDef("PANO_9901_Night_Sky.jpg", "Milky Way Long Exposure", 3840, 2160, 0xFF050811.toInt(), 0xFF6366F1.toInt()),
            SampleDef("PORTRAIT_4021_Golden_Hour.jpg", "Golden Hour Forest Trail", 1920, 2400, 0xFF1E293B.toInt(), 0xFFF59E0B.toInt()),
            SampleDef("RAW_5512_Architecture_Tokyo.jpg", "Modern Glass Architecture", 3000, 2000, 0xFF111827.toInt(), 0xFF06B6D4.toInt())
        )

        samples.forEachIndexed { idx, def ->
            val file = File(photosDir, def.filename)
            if (!file.exists()) {
                val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(def.bgColor)

                val paint = Paint().apply {
                    color = def.accentColor
                    isAntiAlias = true
                    textSize = 28f
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(300f, 160f, 70f, paint)

                paint.color = Color.WHITE
                paint.textSize = 22f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(def.title, 300f, 280f, paint)

                paint.textSize = 14f
                paint.color = Color.LTGRAY
                canvas.drawText("DiskVault Private Sample Photo #${idx + 1}", 300f, 320f, paint)

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }

            val sha256 = CryptoUtil.calculateSha256(FileInputStream(file))
            sampleItems.add(
                PhotoItem(
                    id = "sample_$idx",
                    title = def.filename,
                    uriString = "file://${file.absolutePath}",
                    dateAdded = System.currentTimeMillis() - (idx * 3600000L * 24),
                    sizeBytes = file.length(),
                    mimeType = "image/jpeg",
                    width = def.width,
                    height = def.height,
                    sha256Checksum = sha256,
                    isSample = true
                )
            )
        }

        _rawPhotos.value = sampleItems
    }

    private data class SampleDef(
        val filename: String,
        val title: String,
        val width: Int,
        val height: Int,
        val bgColor: Int,
        val accentColor: Int
    )
}
