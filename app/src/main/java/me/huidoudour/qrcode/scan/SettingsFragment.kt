package me.huidoudour.qrcode.scan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.huidoudour.qrcode.scan.databinding.FragmentSettingsBinding

/**
 * 桌面图标的唯一管理入口：主图标与快速扫描图标共用同一套偏好设置。
 *
 * 主图标别名和快速扫描别名是两组互不相同的 activity-alias，必须各自按“设置 + 当前图标主题”计算目标状态，
 * 不能因为切换彩色/默认图标就无条件启用某个快速扫描别名，否则会出现关不掉的入口图标。
 */
private object LauncherIcons {

    private const val PREFS_NAME = "app_preferences"
    private const val KEY_APP_ICON_THEME = "app_icon_theme"
    private const val KEY_SHOW_QUICK_SCAN_ICON = "show_quick_scan_icon"

    const val THEME_DEFAULT = "default"
    const val THEME_COLORFUL = "colorful"

    /**
     * 别名类的全限定名全部写成字面量常量。
     *
     * 不要用 BuildConfig::class.java.package 之类的反射去推导包名：release 构建会做 R8 压缩混淆，
     * 类被改名/重新打包后推导出来的包名可能不是 namespace，导致 ComponentName 指向不存在的组件，
     * setComponentEnabledSetting 静默失效——表现为"切换图标和开关都没反应"（debug 包却正常）。
     */
    private const val MAIN_DEFAULT = "me.huidoudour.qrcode.scan.MainActivityAliasDefault"
    private const val MAIN_COLORFUL = "me.huidoudour.qrcode.scan.MainActivityAliasColorful"
    private const val QUICK_DEFAULT = "me.huidoudour.qrcode.scan.QuickScanActivityAliasDefault"
    private const val QUICK_COLORFUL = "me.huidoudour.qrcode.scan.QuickScanActivityAliasColorful"

    private val MAIN_ALIASES = listOf(MAIN_DEFAULT, MAIN_COLORFUL)
    private val QUICK_SCAN_ALIASES = listOf(QUICK_DEFAULT, QUICK_COLORFUL)

    /**
     * Activity-alias 的组件名 = 应用包名（applicationId）+ 别名类的全限定名。
     * 两者大小写并不相同（me.huidoudour.QRCode.scan / me.huidoudour.qrcode.scan），必须分开拼接。
     */
    private fun componentName(context: Context, aliasClassName: String) =
        ComponentName(context.packageName, aliasClassName)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前图标主题（default / colorful） */
    fun currentIconTheme(context: Context): String {
        val theme = prefs(context).getString(KEY_APP_ICON_THEME, THEME_DEFAULT)
        return if (theme == THEME_COLORFUL) THEME_COLORFUL else THEME_DEFAULT
    }

    /** 用户设置的“是否显示快速扫描图标” */
    fun isQuickScanIconEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_QUICK_SCAN_ICON, false)

    /** 按图标主题在「默认 / 多彩」两个变体里选一个（列表下标 0 = 默认，1 = 多彩） */
    private fun <T> variant(aliases: List<T>, colorful: Boolean): T =
        if (colorful) aliases[1] else aliases[0]

    /**
     * 快速扫描图标当前是否真的出现在桌面上（读取系统里别名的实际状态，不依赖偏好设置，
     * 因为旧版本可能已经把彩色别名启用了）。
     */
    fun isQuickScanAliasActive(context: Context): Boolean {
        val alias = variant(QUICK_SCAN_ALIASES, currentIconTheme(context) == THEME_COLORFUL)
        return isAliasEnabled(context, alias)
    }

    private fun isAliasEnabled(context: Context, alias: String): Boolean = try {
        context.packageManager.getComponentEnabledSetting(componentName(context, alias)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } catch (e: Exception) {
        false
    }

    /**
     * 按「图标主题 + 快速扫描开关」重新计算四个别名的最终状态。
     *
     * 先把需要显示的别名启用，再禁用其余别名，避免中途出现一个入口都不存在的情况。
     */
    fun sync(context: Context) {
        val colorful = currentIconTheme(context) == THEME_COLORFUL
        val quickScanVisible = isQuickScanIconEnabled(context)

        val enabledAliases = ArrayList<String>(2)
        enabledAliases.add(variant(MAIN_ALIASES, colorful))
        if (quickScanVisible) {
            enabledAliases.add(variant(QUICK_SCAN_ALIASES, colorful))
        }

        // 1) 先启用需要显示的别名，保证桌面上始终至少有一个入口
        enabledAliases.forEach { setAliasEnabled(context, it, true) }

        // 2) 再禁用所有不该显示的别名（这里会无条件关闭另一个主题的快速扫描别名）
        (MAIN_ALIASES + QUICK_SCAN_ALIASES)
            .filterNot { enabledAliases.contains(it) }
            .forEach { setAliasEnabled(context, it, false) }
    }

    /** 切换快速扫描图标显隐并持久化设置 */
    fun setQuickScanIconVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_QUICK_SCAN_ICON, visible).apply()
        sync(context)
    }

    /** 切换主图标主题并持久化设置（图标主题同样决定快速扫描入口使用哪套彩色资源） */
    fun setIconTheme(context: Context, theme: String) {
        val normalized = if (theme == THEME_COLORFUL) THEME_COLORFUL else THEME_DEFAULT
        prefs(context).edit().putString(KEY_APP_ICON_THEME, normalized).apply()
        sync(context)
    }

    private fun setAliasEnabled(context: Context, alias: String, enabled: Boolean) {
        try {
            context.packageManager.setComponentEnabledSetting(
                componentName(context, alias),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            // 单个别名设置失败不影响其余别名
            e.printStackTrace()
        }
    }
}


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
        // 先按系统的实际别名状态刷新一次桌面图标，纠正历史版本留下的不一致状态
        LauncherIcons.sync(requireContext())

        val storedValue = LauncherIcons.isQuickScanIconEnabled(requireContext())
        val actualValue = LauncherIcons.isQuickScanAliasActive(requireContext())

        // 开关显示实际生效的状态，避免“开关是关的但桌面上还有图标”
        if (actualValue != storedValue) {
            LauncherIcons.setQuickScanIconVisible(requireContext(), actualValue)
        }

        // 先设置初始状态，再注册监听，避免初始化时就触发一次切换
        binding.quickScanIconSwitch.isChecked = actualValue

        // 监听开关变化
        binding.quickScanIconSwitch.setOnCheckedChangeListener { _, isChecked ->
            LauncherIcons.setQuickScanIconVisible(requireContext(), isChecked)
            
            // 显示提示
            val message = if (isChecked) {
                getString(R.string.quick_scan_icon_enabled)
            } else {
                getString(R.string.quick_scan_icon_disabled)
            }
            android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
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
            getString(R.string.app_icon_theme_default),
            getString(R.string.app_icon_theme_colorful)
        )
        
        val currentIcon = LauncherIcons.currentIconTheme(requireContext())
        
        val selectedIndex = when (currentIcon) {
            "default" -> 0
            "colorful" -> 1
            else -> 0
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.dialog_title_select_app_icon))
            .setSingleChoiceItems(iconThemes, selectedIndex) { dialog, which ->
                val selectedIcon = when (which) {
                    0 -> "default"
                    1 -> "colorful"
                    else -> "default"
                }
                
                // 保存图标设置，并按“图标主题 + 快速扫描开关”统一刷新桌面图标
                LauncherIcons.setIconTheme(requireContext(), selectedIcon)
                
                // 显示提示
                android.widget.Toast.makeText(
                    requireContext(), 
                    getString(R.string.toast_icon_changed), 
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