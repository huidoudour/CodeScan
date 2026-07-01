package me.huidoudour.QRCode.scan

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScanResult::class, AppStartupRecord::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanResultDao(): ScanResultDao
    abstract fun startupRecordDao(): StartupRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_results ADD COLUMN remark TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加 codeType 列
                db.execSQL("ALTER TABLE scan_results ADD COLUMN codeType TEXT NOT NULL DEFAULT 'UNKNOWN'")
                
                // 根据内容推断码类型：
                // QR码通常较长且包含多种字符
                // EAN-13 是12-13位数字
                // 其他纯数字可能是条形码
                db.execSQL("""
                    UPDATE scan_results SET codeType = CASE
                        WHEN length(content) = 12 AND content GLOB '[0-9]*' THEN 'EAN_13'
                        WHEN length(content) = 13 AND content GLOB '[0-9]*' THEN 'EAN_13'
                        WHEN length(content) >= 20 AND content NOT GLOB '[0-9]*' THEN 'QR_CODE'
                        WHEN length(content) BETWEEN 8 AND 20 AND content GLOB '[0-9]*' THEN 'CODE_128'
                        ELSE 'UNKNOWN'
                    END
                """)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 版本3到版本4的空迁移（无数据库结构变更）
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建应用启动记录表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_startup_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        startup_page TEXT NOT NULL
                    )
                """)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scan_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}