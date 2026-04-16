package me.huidoudour.QRCode.scan

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.navigationrail.NavigationRailView
import me.huidoudour.QRCode.scan.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置状态栏文字颜色适配
        updateStatusBarStyle()

        // 检查是否存在底部导航栏，以决定使用哪种导航方式
        if (binding.root.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView?>(R.id.bottom_navigation) != null) {
            // 小屏设备：使用底部导航栏
            binding.bottomNavigation?.setOnItemSelectedListener { item ->
                handleNavigationItemSelected(item.itemId)
                true
            }

            // Set default fragment
            if (savedInstanceState == null) {
                binding.bottomNavigation?.selectedItemId = R.id.navigation_scan
            }
        } else {
            // 大屏设备：使用侧边导航栏
            val navigationRail = binding.root.findViewById<NavigationRailView>(R.id.navigation_rail)
            navigationRail?.setOnItemSelectedListener { item ->
                handleNavigationItemSelected(item.itemId)
                true
            }

            // Set default fragment
            if (savedInstanceState == null) {
                // 找到导航菜单中的扫描项并模拟选择
                val menu = navigationRail?.menu
                val scanItem = menu?.findItem(R.id.navigation_scan)
                scanItem?.isChecked = true
                handleNavigationItemSelected(R.id.navigation_scan)
            }
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
        // 尝试使用底部导航栏
        if (binding.root.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView?>(R.id.bottom_navigation) != null) {
            binding.bottomNavigation?.selectedItemId = tabId
        } else {
            // 对于大屏设备，直接处理导航
            handleNavigationItemSelected(tabId)
            // 更新侧边导航栏的选中状态
            val navigationRail = binding.root.findViewById<NavigationRailView>(R.id.navigation_rail)
            val menu = navigationRail?.menu
            val menuSize = menu?.size() ?: 0
            for (i in 0 until menuSize) {
                val item = menu?.getItem(i)
                item?.isChecked = item?.itemId == tabId
            }
        }
    }
}