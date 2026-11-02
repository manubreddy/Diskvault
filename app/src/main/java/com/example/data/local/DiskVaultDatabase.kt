package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BackupRecord::class,
        HardDiskTargetEntity::class,
        TransferAuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DiskVaultDatabase : RoomDatabase() {
    abstract fun diskVaultDao(): DiskVaultDao

    companion object {
        @Volatile
        private var INSTANCE: DiskVaultDatabase? = null

        fun getDatabase(context: Context): DiskVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DiskVaultDatabase::class.java,
                    "disk_vault_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
