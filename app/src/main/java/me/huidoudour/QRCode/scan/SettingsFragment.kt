package me.huidoudour.QRCode.scan

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.huidoudour.QRCode.scan.databinding.FragmentSettingsBinding


class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        updateLanguageDisplay()
        updateVersionInfo()
        updateThemeDisplay()
        setupQuickScanIconSwitch()
        setupAppIconLongClick()

        binding.languageSettingItem.setOnClickListener {
            showLanguageSelectionDialog()
        }
        
        binding.themeSettingItem.setOnClickListener {
            showThemeSelectionDialog()
        }

        binding.aboutCard.setOnClickListener {
            val intent = Intent(requireContext(), MeActivity::class.java)
            startActivity(intent)
        }
        
        // 长按关于按钮进入启动记录页面
        binding.aboutCard.setOnLongClickListener {
            val intent = Intent(requireContext(), StartupRecordsActivity::class.java)
            startActivity(intent)
            true
        }
    }

    private fun updateLanguageDisplay() {
        val currentLanguage = getCurrentLanguage()
        binding.currentLanguage.text = currentLanguage
    }
    
    private fun updateThemeDisplay() {
        val sharedPref = requireContext().getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
        val themeMode = sharedPref.getString("theme_mode", "system") ?: "system"
        
        val themeText = when (themeMode) {
            "light" -> getString(R.string.theme_light)
            "dark" -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
        
        binding.currentTheme.text = themeText
    }

    private fun updateVersionInfo() {
        val versionInfo = try {
            val packageInfo = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
            @Suppress("DEPRECATION")
            "v${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "v1.0.0 (1)"
        }
        
        // 更新版本信息显示
        binding.versionText.text = versionInfo
    }
    
    private fun setupQuickScanIconSwitch() {
        // 读取保存的设置，默认为 true（显示图标）
        val sharedPref = requireContext().getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
        val showQuickScanIcon = sharedPref.getBoolean("show_quick_scan_icon", true)
        
        // 设置开关状态
        binding.quickScanIconSwitch.isChecked = showQuickScanIcon
        
        // 监听开关变化
        binding.quickScanIconSwitch.setOnCheckedChangeListener { _, isChecked ->
            setQuickScanIconEnabled(isChecked)
            
            // 显示提示
            val message = if (isChecked) {
                getString(R.string.quick_scan_icon_enabled)
            } else {
                getString(R.string.quick_scan_icon_disabled)
            }
            android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
            
            // 保存设置
            with(sharedPref.edit()) {
                putBoolean("show_quick_scan_icon", isChecked)
                apply()
            }
        }
    }
    
    private fun setupAppIconLongClick() {
        binding.appIconImage.setOnLongClickListener {
            showAppIconSelectionDialog()
            true
        }
    }
    
    private fun showAppIconSelectionDialog() {
        val iconThemes = arrayOf(
            "默认绿色",
            "多彩主题"
        )
        
        val sharedPref = requireContext().getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
        val currentIcon = sharedPref.getString("app_icon_theme", "default") ?: "default"
        
        val selectedIndex = when (currentIcon) {
            "default" -> 0
            "colorful" -> 1
            else -> 0
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle("选择应用图标")
            .setSingleChoiceItems(iconThemes, selectedIndex) { dialog, which ->
                val selectedIcon = when (which) {
                    0 -> "default"
                    1 -> "colorful"
                    else -> "default"
                }
                
                // 保存图标设置
                with(sharedPref.edit()) {
                    putString("app_icon_theme", selectedIcon)
                    apply()
                }
                
                // 应用图标
                applyAppIcon(selectedIcon)
                
                // 显示提示
                android.widget.Toast.makeText(
                    requireContext(), 
                    "图标已更改，可能需要几秒钟生效", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
                
                dialog.dismiss()
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }
    
    private fun applyAppIcon(iconTheme: String) {
        val packageManager = requireContext().packageManager
        
        // 禁用所有图标别名
        val aliases = listOf(
            "me.huidoudour.QRCode.scan.MainActivityAliasDefault",
            "me.huidoudour.QRCode.scan.MainActivityAliasColorful"
        )
        
        aliases.forEach { alias ->
            try {
                val componentName = ComponentName(requireContext(), alias)
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                // 忽略错误
            }
        }
        
        // 启用选中的图标
        val selectedAlias = when (iconTheme) {
            "colorful" -> "me.huidoudour.QRCode.scan.MainActivityAliasColorful"
            else -> "me.huidoudour.QRCode.scan.MainActivityAliasDefault"
        }
        
        try {
            val componentName = ComponentName(requireContext(), selectedAlias)
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun showThemeSelectionDialog() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        
        val sharedPref = requireContext().getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
        val currentTheme = sharedPref.getString("theme_mode", "system") ?: "system"
        
        val selectedIndex = when (currentTheme) {
            "system" -> 0
            "light" -> 1
            "dark" -> 2
            else -> 0
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                val selectedTheme = when (which) {
                    0 -> "system"
                    1 -> "light"
                    2 -> "dark"
                    else -> "system"
                }
                
                // 保存主题设置
                with(sharedPref.edit()) {
                    putString("theme_mode", selectedTheme)
                    apply()
                }
                
                // 应用主题
                applyTheme(selectedTheme)
                
                // 更新显示
                updateThemeDisplay()
                
                // 显示提示
                val message = getString(R.string.theme_changed)
                android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
                
                // 重新创建 Activity 以应用主题更改
                activity?.recreate()
                
                dialog.dismiss()
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }
    
    private fun applyTheme(themeMode: String) {
        val nightMode = when (themeMode) {
            "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }
    
    private fun setQuickScanIconEnabled(enabled: Boolean) {
        val componentName = ComponentName(
            requireContext(),
            "me.huidoudour.QRCode.scan.QuickScanActivity"
        )
        
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        
        requireContext().packageManager.setComponentEnabledSetting(
            componentName,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun getCurrentLanguage(): String {
        val languageCode = LanguageManager.getCurrentLanguage(requireActivity())
        
        return when (languageCode) {
            LanguageManager.LANGUAGE_ENGLISH -> getString(R.string.language_en)
            LanguageManager.LANGUAGE_CHINESE_SIMPLIFIED -> getString(R.string.language_zh_cn)
            LanguageManager.LANGUAGE_CHINESE_TRADITIONAL -> getString(R.string.language_zh_tw)
            LanguageManager.LANGUAGE_JAPANESE -> getString(R.string.language_ja)
            LanguageManager.LANGUAGE_RUSSIAN -> getString(R.string.language_ru)
            else -> getString(R.string.language_default)
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf(
            getString(R.string.language_default),
            getString(R.string.language_en),
            getString(R.string.language_zh_cn),
            getString(R.string.language_zh_tw),
            getString(R.string.language_ja),
            getString(R.string.language_ru)
        )
        
        val currentLanguageCode = LanguageManager.getCurrentLanguage(requireActivity())
        
        val selectedIndex = when (currentLanguageCode) {
            LanguageManager.LANGUAGE_ENGLISH -> 1
            LanguageManager.LANGUAGE_CHINESE_SIMPLIFIED -> 2
            LanguageManager.LANGUAGE_CHINESE_TRADITIONAL -> 3
            LanguageManager.LANGUAGE_JAPANESE -> 4
            LanguageManager.LANGUAGE_RUSSIAN -> 5
            else -> 0
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val selectedLanguage = when (which) {
                    0 -> LanguageManager.LANGUAGE_SYSTEM
                    1 -> LanguageManager.LANGUAGE_ENGLISH
                    2 -> LanguageManager.LANGUAGE_CHINESE_SIMPLIFIED
                    3 -> LanguageManager.LANGUAGE_CHINESE_TRADITIONAL
                    4 -> LanguageManager.LANGUAGE_JAPANESE
                    5 -> LanguageManager.LANGUAGE_RUSSIAN
                    else -> LanguageManager.LANGUAGE_SYSTEM
                }
                
                // 只有当语言真正改变时才重启
                if (selectedLanguage != currentLanguageCode) {
                    setLanguage(selectedLanguage)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }

    private fun setLanguage(languageCode: String) {
        // 保存语言设置
        LanguageManager.saveLanguage(requireActivity(), languageCode)
        
        // 更新显示
        updateLanguageDisplay()
        
        // 显示提示
        val message = getString(R.string.language_changed)
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
        
        // 重新创建 Activity 以应用语言更改（不退出应用）
        activity?.recreate()
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}