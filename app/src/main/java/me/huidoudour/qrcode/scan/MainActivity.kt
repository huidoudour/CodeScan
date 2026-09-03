package me.huidoudour.qrcode.scan

import android.os.Build
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import me.huidoudour.qrcode.scan.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置状态栏文字颜色适配
        updateStatusBarStyle()

        // 底部导航栏
        binding.bottomNavigation?.setOnItemSelectedListener { item ->
            handleNavigationItemSelected(item.itemId)
            true
        }

        // Set default fragment
        if (savedInstanceState == null) {
            binding.bottomNavigation?.selectedItemId = R.id.navigation_scan
        }
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
}