package me.huidoudour.QRCode.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StartupRecordDao {
    @Insert
    suspend fun insert(record: AppStartupRecord)

    @Query("SELECT * FROM app_startup_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<AppStartupRecord>

    @Query("DELETE FROM app_startup_records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM app_startup_records")
    suspend fun getCount(): Int
}
