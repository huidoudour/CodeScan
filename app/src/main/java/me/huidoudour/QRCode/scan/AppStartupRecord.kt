package me.huidoudour.QRCode.scan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_startup_records")
data class AppStartupRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "startup_page")
    val startupPage: String // "main" 或 "quick"
)
