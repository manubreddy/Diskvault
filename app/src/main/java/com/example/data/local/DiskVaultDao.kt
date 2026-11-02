package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiskVaultDao {
    // Backup Records
    @Query("SELECT * FROM backup_records ORDER BY backedUpAt DESC")
    fun getAllBackupRecords(): Flow<List<BackupRecord>>

    @Query("SELECT * FROM backup_records WHERE photoId = :photoId LIMIT 1")
    suspend fun getRecordForPhoto(photoId: String): BackupRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupRecord(record: BackupRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupRecords(records: List<BackupRecord>)

    @Query("DELETE FROM backup_records WHERE photoId = :photoId")
    suspend fun deleteBackupRecord(photoId: String)

    @Query("SELECT COUNT(*) FROM backup_records")
    fun getBackupCount(): Flow<Int>

    // Hard Disk Targets
    @Query("SELECT * FROM hard_disk_targets ORDER BY lastBackupTimestamp DESC")
    fun getAllTargets(): Flow<List<HardDiskTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTarget(target: HardDiskTargetEntity)

    @Query("DELETE FROM hard_disk_targets WHERE id = :id")
    suspend fun deleteTarget(id: String)

    // Audit Logs
    @Query("SELECT * FROM transfer_audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAuditLogs(): Flow<List<TransferAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: TransferAuditLogEntity)

    @Query("DELETE FROM transfer_audit_logs")
    suspend fun clearAuditLogs()
}
