package me.huidoudour.QRCode.scan

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

/**
 *  BaseActivity - 所有 Activity 的基类
 *  确保语言设置在每个 Activity 中正确应用
 */
open class BaseActivity : AppCompatActivity() {
    
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
