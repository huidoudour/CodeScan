package me.huidoudour.QRCode.scan

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class StartupRecordDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "startup_records.db"
        private const val DATABASE_VERSION = 1
        
        const val TABLE_NAME = "app_startup_records"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_STARTUP_PAGE = "startup_page"
        
        private const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_STARTUP_PAGE TEXT NOT NULL
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 如果需要升级，可以在这里处理
        if (oldVersion < newVersion) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    /**
     * 插入启动记录
     */
    fun insertRecord(timestamp: Long, startupPage: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_STARTUP_PAGE, startupPage)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    /**
     * 查询所有记录（按时间倒序）
     */
    fun getAllRecords(): List<AppStartupRecord> {
        val records = mutableListOf<AppStartupRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null, // 所有列
            null, // WHERE子句
            null, // WHERE参数
            null, // GROUP BY
            null, // HAVING
            "$COLUMN_TIMESTAMP DESC" // ORDER BY
        )

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID))
                val timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val startupPage = it.getString(it.getColumnIndexOrThrow(COLUMN_STARTUP_PAGE))
                records.add(AppStartupRecord(id, timestamp, startupPage))
            }
        }

        return records
    }

    /**
     * 删除所有记录
     */
    fun deleteAllRecords() {
        val db = writableDatabase
        db.delete(TABLE_NAME, null, null)
        // 重置自增ID
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='$TABLE_NAME'")
    }

    /**
     * 获取记录总数
     */
    fun getRecordCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        return cursor.use {
            if (it.moveToFirst()) {
                it.getInt(0)
            } else {
                0
            }
        }
    }
}
