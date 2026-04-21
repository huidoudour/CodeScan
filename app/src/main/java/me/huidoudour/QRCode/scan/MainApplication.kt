package me.huidoudour.QRCode.scan

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {
    
    override fun attachBaseContext(base: Context?) {
        if (base == null) {
            super.attachBaseContext(null)
            return
        }
        
        // 获取保存的语言设置
        val languageCode = LanguageManager.getCurrentLanguage(base)
        
        // 应用语言设置并获取新的 Context
        val newContext = LanguageManager.setLocale(base, languageCode)
        
        // 重要：必须使用返回的新 Context
        super.attachBaseContext(newContext)
    }
    
    override fun onCreate() {
        super.onCreate()
        // 记录应用启动
        recordAppStartup("main")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变化时，如果用户选择的是跟随系统，不需要做任何事
        // 如果用户选择了特定语言，需要重新应用
        val languageCode = LanguageManager.getCurrentLanguage(this)
        if (languageCode.isNotEmpty()) {
            // 只有当用户选择了特定语言时才重新应用
            LanguageManager.setLocale(this, languageCode)
        }
    }
    
    private fun recordAppStartup(page: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val dbHelper = StartupRecordDatabaseHelper(this@MainApplication)
            dbHelper.insertRecord(System.currentTimeMillis(), page)
        }
    }
}