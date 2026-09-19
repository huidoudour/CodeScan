package me.huidoudour.qrcode.scan

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import me.huidoudour.qrcode.scan.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastHistoryNavigationTapTime = NO_HISTORY_NAVIGATION_TAP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置状态栏文字颜色适配
        updateStatusBarStyle()

        // 底部导航栏
        binding.bottomNavigation?.setOnItemSelectedListener { item ->
            recordNavigationTap(item.itemId)
            handleNavigationItemSelected(item.itemId)
            true
        }

        // 重选回调本身只表示“再点一次”，需结合时间窗识别真正的双击。
        binding.bottomNavigation?.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.navigation_history && isHistoryNavigationDoubleTap()) {
                (supportFragmentManager.findFragmentById(R.id.fragment_container) as? HistoryFragment)
                    ?.reloadWithLoadingAnimation()
            }
        }

        // 冷启动时显式创建扫描页，不能依赖给已选中项目重复赋值来触发导航回调。
        if (savedInstanceState == null) {
            handleNavigationItemSelected(R.id.navigation_scan)
        }
    }

    private fun recordNavigationTap(itemId: Int) {
        lastHistoryNavigationTapTime = if (itemId == R.id.navigation_history) {
            SystemClock.uptimeMillis()
        } else {
            NO_HISTORY_NAVIGATION_TAP
        }
    }

    private fun isHistoryNavigationDoubleTap(): Boolean {
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = lastHistoryNavigationTapTime != NO_HISTORY_NAVIGATION_TAP &&
            now - lastHistoryNavigationTapTime <= ViewConfiguration.getDoubleTapTimeout()
        lastHistoryNavigationTapTime = if (isDoubleTap) {
            NO_HISTORY_NAVIGATION_TAP
        } else {
            now
        }
        return isDoubleTap
    }
    
    private fun handleNavigationItemSelected(itemId: Int) {
        var selectedFragment: Fragment? = null
        when (itemId) {
            R.id.navigation_scan -> selectedFragment = ScannerFragment()
            R.id.navigation_history -> selectedFragment = HistoryFragment()
            R.id.navigation_export -> selectedFragment = ExportFragment()
            R.id.navigation_settings -> selectedFragment = SettingsFragment()
        }
        if (selectedFragment != null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, selectedFragment)
                .commit()
        }
    }
    
    private fun updateStatusBarStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isDarkMode = isDarkMode()
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDarkMode
            insetsController.isAppearanceLightNavigationBars = !isDarkMode
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isDarkMode()) {
                // 暗色模式：使用浅色文字
                0
            } else {
                // 浅色模式：使用深色文字
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
    
    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun navigateToTab(tabId: Int) {
        binding.bottomNavigation?.selectedItemId = tabId
    }

    private companion object {
        const val NO_HISTORY_NAVIGATION_TAP = Long.MIN_VALUE
    }
}
