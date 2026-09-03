package me.huidoudour.qrcode.scan

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_results")
data class ScanResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val remark: String? = null,
    val codeType: String = "UNKNOWN", // 码类型：QR_CODE, LINEAR_CODE 等
    val timestamp: Long = System.currentTimeMillis()
)