package com.example.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.data.model.PhotoItem
import com.example.data.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

data class BackupProgress(
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val bytesCopiedCurrentFile: Long = 0L,
    val totalBytesCopied: Long = 0L,
    val totalBytesToCopy: Long = 0L,
    val speedMbPerSec: Double = 0.0,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

class UsbHardDiskManager(
    private val context: Context,
    private val repository: PhotoRepository
) {
    suspend fun backupPhotosToHardDisk(
        treeUri: Uri,
        hardDiskName: String,
        photos: List<PhotoItem>,
        onProgress: (BackupProgress) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

            val totalBytes = photos.sumOf { it.sizeBytes }
            var totalBytesCopied = 0L
            var copiedFilesCount = 0
            val startTime = System.currentTimeMillis()

            photos.forEachIndexed { index, photo ->
                val progress = BackupProgress(
                    currentFileIndex = index + 1,
                    totalFiles = photos.size,
                    currentFileName = photo.title,
                    bytesCopiedCurrentFile = 0L,
                    totalBytesCopied = totalBytesCopied,
                    totalBytesToCopy = totalBytes,
                    speedMbPerSec = 0.0,
                    isComplete = false
                )
                onProgress(progress)

                val sourceStream: InputStream = repository.openPhotoInputStream(photo)
                    ?: return@forEachIndexed

                // Create destination file using native DocumentsContract
                val destFileUri: Uri? = try {
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        parentDocUri,
                        photo.mimeType,
                        photo.title
                    )
                } catch (e: Exception) {
                    Log.e("UsbHardDiskManager", "Failed to create document ${photo.title}", e)
                    null
                }

                if (destFileUri == null) {
                    sourceStream.close()
                    return@forEachIndexed
                }

                val destStream: OutputStream? = context.contentResolver.openOutputStream(destFileUri)
                if (destStream == null) {
                    sourceStream.close()
                    return@forEachIndexed
                }

                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    var fileBytesCopied = 0L

                    while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                        destStream.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        fileBytesCopied += bytesRead
                        totalBytesCopied += bytesRead

                        val elapsedSec = maxOf(0.1, (System.currentTimeMillis() - startTime) / 1000.0)
                        val speedMb = (totalBytesCopied / (1024.0 * 1024.0)) / elapsedSec

                        onProgress(
                            BackupProgress(
                                currentFileIndex = index + 1,
                                totalFiles = photos.size,
                                currentFileName = photo.title,
                                bytesCopiedCurrentFile = fileBytesCopied,
                                totalBytesCopied = totalBytesCopied,
                                totalBytesToCopy = totalBytes,
                                speedMbPerSec = speedMb,
                                isComplete = false
                            )
                        )
                    }
                    destStream.flush()

                    val computedSha256 = bytesToHex(digest.digest())

                    // Record verified backup to database
                    repository.recordPhotoBackup(
                        photoId = photo.id,
                        fileName = photo.title,
                        fileSize = photo.sizeBytes,
                        sha256 = computedSha256,
                        targetDiskName = hardDiskName,
                        destinationPath = "HardDrive:/$hardDiskName/${photo.title}"
                    )
                    copiedFilesCount++
                } catch (e: Exception) {
                    Log.e("UsbHardDiskManager", "Failed copying ${photo.title}", e)
                } finally {
                    try { sourceStream.close() } catch (_: Exception) {}
                    try { destStream.close() } catch (_: Exception) {}
                }
            }

            onProgress(
                BackupProgress(
                    currentFileIndex = photos.size,
                    totalFiles = photos.size,
                    currentFileName = "Done",
                    totalBytesCopied = totalBytesCopied,
                    totalBytesToCopy = totalBytes,
                    isComplete = true
                )
            )

            Result.success(copiedFilesCount)
        } catch (e: Exception) {
            Log.e("UsbHardDiskManager", "Error during hard disk backup", e)
            Result.failure(e)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
