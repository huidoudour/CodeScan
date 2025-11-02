package me.huidoudour.QRCode.scan

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ScanResultDao {
    @Insert
    suspend fun insert(scanResult: ScanResult)

    @Query("SELECT * FROM scan_results ORDER BY timestamp DESC")
    suspend fun getAll(): List<ScanResult>

    @Update
    suspend fun update(scanResult: ScanResult)

    @Delete
    suspend fun delete(scanResult: ScanResult)
}