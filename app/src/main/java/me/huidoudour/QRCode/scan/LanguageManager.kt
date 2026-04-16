package me.huidoudour.QRCode.scan

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.*

class LanguageManager {
    companion object {
        // 支持的语言列表
        const val LANGUAGE_SYSTEM = ""
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_CHINESE_SIMPLIFIED = "zh"
        const val LANGUAGE_CHINESE_TRADITIONAL = "zh-TW"
        const val LANGUAGE_JAPANESE = "ja"
        const val LANGUAGE_RUSSIAN = "ru"
        
        fun setLocale(context: Context, languageCode: String): Context {
            return if (languageCode.isEmpty()) {
                // 使用系统默认语言
                // 基于 Application Context 创建新的 Configuration，不设置任何 locale
                // 这样会真正跟随系统语言
                val baseContext = context.applicationContext ?: context
                val configuration = baseContext.resources.configuration
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // 对于 Android 7.0+，创建一个空的 LocaleList 表示使用系统默认
                    val newConfig = android.content.res.Configuration(configuration)
                    newConfig.setLocales(android.os.LocaleList.getEmptyLocaleList())
                    baseContext.createConfigurationContext(newConfig)
                } else {
                    // 对于旧版本，直接使用系统默认的 locale
                    @Suppress("DEPRECATION")
                    val newConfig = android.content.res.Configuration(configuration)
                    newConfig.locale = java.util.Locale.getDefault()
                    baseContext.createConfigurationContext(newConfig)
                }
            } else {
                val locale = when (languageCode) {
                    LANGUAGE_ENGLISH -> Locale("en")
                    LANGUAGE_CHINESE_SIMPLIFIED -> Locale.SIMPLIFIED_CHINESE
                    LANGUAGE_CHINESE_TRADITIONAL -> Locale.TRADITIONAL_CHINESE
                    LANGUAGE_JAPANESE -> Locale.JAPANESE
                    LANGUAGE_RUSSIAN -> Locale("ru")
                    else -> Locale.getDefault()
                }
                updateResources(context, locale)
            }
        }

        private fun updateResources(context: Context, locale: Locale): Context {
            // 不在全局设置 Locale，只在 Context 级别设置
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                updateResourcesLocale(context, locale)
            } else {
                updateResourcesLocaleLegacy(context, locale)
            }
        }

        @Suppress("DEPRECATION")
        private fun updateResourcesLocaleLegacy(context: Context, locale: Locale): Context {
            val resources = context.resources
            val configuration = resources.configuration
            configuration.locale = locale
            // 不在全局设置 Locale，只更新当前 context 的配置
            resources.updateConfiguration(configuration, resources.displayMetrics)
            return context
        }

        private fun updateResourcesLocale(context: Context, locale: Locale): Context {
            val configuration = context.resources.configuration
            val localeList = LocaleList(locale)
            // 不在全局设置 LocaleList，只在 Context 级别设置
            configuration.setLocales(localeList)
            return context.createConfigurationContext(configuration)
        }

        fun getCurrentLanguage(context: Context): String {
            val sharedPref = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            return sharedPref.getString("language_preference", "") ?: ""
        }
        
        fun saveLanguage(context: Context, languageCode: String) {
            val sharedPref = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("language_preference", languageCode)
                apply()
            }
        }
        
        fun getAvailableLanguages(): List<Pair<String, String>> {
            return listOf(
                Pair("", "System Default / 系统默认"),
                Pair(LANGUAGE_ENGLISH, "English"),
                Pair(LANGUAGE_CHINESE_SIMPLIFIED, "简体中文"),
                Pair(LANGUAGE_CHINESE_TRADITIONAL, "繁體中文"),
                Pair(LANGUAGE_JAPANESE, "日本語"),
                Pair(LANGUAGE_RUSSIAN, "Русский")
            )
        }
        
        /**
         * 获取显示用的语言名称
         */
        fun getLanguageDisplayName(context: android.content.Context, languageCode: String): String {
            return when (languageCode) {
                LANGUAGE_ENGLISH -> "English"
                LANGUAGE_CHINESE_SIMPLIFIED -> "简体中文"
                LANGUAGE_CHINESE_TRADITIONAL -> "繁體中文"
                LANGUAGE_JAPANESE -> "日本語"
                LANGUAGE_RUSSIAN -> "Русский"
                else -> "System Default / 系统默认"
            }
        }
    }
}
