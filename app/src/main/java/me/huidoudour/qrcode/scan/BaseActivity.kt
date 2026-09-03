package me.huidoudour.qrcode.scan

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 *  BaseActivity - 所有 Activity 的基类
 *  确保语言设置和主题设置在每个 Activity 中正确应用
 */
open class BaseActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 onCreate 中应用主题设置
        applyThemeSetting()
        super.onCreate(savedInstanceState)
    }
    
    private fun applyThemeSetting() {
        val sharedPref = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val themeMode = sharedPref.getString("theme_mode", "system") ?: "system"
        
        val nightMode = when (themeMode) {
            "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }
    
    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        
        // 获取保存的语言设置
        val languageCode = LanguageManager.getCurrentLanguage(newBase)
        
        // 应用语言设置并获取新的 Context
        val context = LanguageManager.setLocale(newBase, languageCode)
        
        // 使用新的 Context
        super.attachBaseContext(context)
    }
}
