package me.huidoudour.QRCode.scan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import me.huidoudour.QRCode.scan.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.navigation_scan -> selectedFragment = ScannerFragment()
                R.id.navigation_history -> selectedFragment = HistoryFragment()
                R.id.navigation_export -> selectedFragment = ExportFragment()
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, selectedFragment).commit()
            }
            true
        }

        // Set default fragment
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.navigation_scan
        }
    }

    fun navigateToTab(tabId: Int) {
        binding.bottomNavigation.selectedItemId = tabId
    }
}