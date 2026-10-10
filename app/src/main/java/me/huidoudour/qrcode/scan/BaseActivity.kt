package me.huidoudour.qrcode.scan

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

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

    /**
     * 给指定视图补上系统栏（状态栏 / 导航栏 / 刘海）的内边距。
     *
     * 窗口是 edge-to-edge 的（主题里状态栏、导航栏都设为透明），如果页面自己不处理 inset，
     * 工具栏会顶到屏幕最上沿、和状态栏图标重叠，底部内容也会被手势条压住。
     * 只调用一次：监听器在视图首次布局前设置好，先记录布局自身的内边距作为基准，之后每次分发都按基准重算。
     */
    protected fun applySystemBarInsets(view: View) {
        val basePadding = Padding(
            left = view.paddingLeft,
            top = view.paddingTop,
            right = view.paddingRight,
            bottom = view.paddingBottom
        )

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            target.updatePadding(
                left = basePadding.left + bars.left,
                top = basePadding.top + bars.top,
                right = basePadding.right + bars.right,
                bottom = basePadding.bottom + bars.bottom
            )
            windowInsets
        }
        // 触发一次分发，避免首帧缺少内边距导致闪烁
        ViewCompat.requestApplyInsets(view)
    }

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)
    
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
