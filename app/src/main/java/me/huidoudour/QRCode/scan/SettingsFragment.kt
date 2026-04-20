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
        setupQuickScanIconSwitch()

        binding.languageSettingItem.setOnClickListener {
            showLanguageSelectionDialog()
        }

        binding.aboutCard.setOnClickListener {
            val intent = Intent(requireContext(), MeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateLanguageDisplay() {
        val currentLanguage = getCurrentLanguage()
        binding.currentLanguage.text = currentLanguage
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
        
        // 重启应用以应用语言更改
        restartApp()
    }

    private fun restartApp() {
        // 使用 PackageManager 获取启动 Intent，确保完全重启
        val intent = requireContext().packageManager
            .getLaunchIntentForPackage(requireContext().packageName)
        
        if (intent != null) {
            // 清除所有 Activity 并重新启动
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // 显示提示
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.language_changed_restart),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            // 延迟启动，让用户看到提示
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startActivity(intent)
                // 退出当前应用
                activity?.finishAffinity()
                // 添加过渡动画（API 34+ 已废弃，但为了兼容性保留）
                @Suppress("DEPRECATION")
                activity?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }, 300)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}