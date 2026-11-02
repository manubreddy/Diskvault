package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_records")
data class BackupRecord(
    @PrimaryKey
    val photoId: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val backedUpAt: Long,
    val targetDiskName: String,
    val destinationPath: String,
    val isVerified: Boolean = true
)

@Entity(tableName = "hard_disk_targets")
data class HardDiskTargetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val uriString: String,
    val targetType: String,
    val lastBackupTimestamp: Long,
    val totalFilesBackedUp: Int,
    val totalBytesBackedUp: Long
)

@Entity(tableName = "transfer_audit_logs")
data class TransferAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val fileName: String,
    val destination: String,
    val bytes: Long,
    val sha256: String,
    val isSuccess: Boolean,
    val message: String
)
