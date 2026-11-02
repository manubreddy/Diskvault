package com.example.data.model

data class PhotoItem(
    val id: String,
    val title: String,
    val uriString: String,
    val dateAdded: Long,
    val sizeBytes: Long,
    val mimeType: String = "image/jpeg",
    val width: Int = 1920,
    val height: Int = 1080,
    val sha256Checksum: String = "",
    val isSample: Boolean = false,
    val isBackedUp: Boolean = false,
    val backupDestination: String = "",
    val backedUpAt: Long = 0L,
    val isVerified: Boolean = false
) {
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                String.format("%.1f KB", kb)
            }
        }
}

enum class BackupDestinationType {
    USB_HARD_DISK,
    LOCAL_WEB_DESKTOP,
    LAN_NETWORK_SHARE
}

data class HardDiskTarget(
    val id: String,
    val name: String,
    val uriString: String,
    val type: BackupDestinationType,
    val lastBackupTimestamp: Long,
    val totalFilesBackedUp: Int,
    val totalBytesBackedUp: Long
)

data class TransferLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val fileName: String,
    val destination: String,
    val bytes: Long,
    val sha256: String,
    val isSuccess: Boolean = true,
    val message: String = ""
)
