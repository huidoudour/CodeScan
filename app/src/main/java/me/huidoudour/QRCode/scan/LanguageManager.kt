package me.huidoudour.QRCode.scan

import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

class LanguageManager {
    companion object {
        // 支持的语言列表（Android 资源限定符格式：语言-r地区）
        const val LANGUAGE_SYSTEM = ""
        const val LANGUAGE_ENGLISH = "en-rUS"
        const val LANGUAGE_CHINESE_SIMPLIFIED = "zh-rCN"
        const val LANGUAGE_CHINESE_TRADITIONAL = "zh-rTW"
        const val LANGUAGE_JAPANESE = "ja-rJP"
        const val LANGUAGE_RUSSIAN = "ru-rRU"
        
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
                    val newConfig = android.content.res.Configuration(configuration)
                    @Suppress("DEPRECATION")
                    newConfig.locale = java.util.Locale.getDefault()
                    baseContext.createConfigurationContext(newConfig)
                }
            } else {
                // 将 Android 格式 (zh-rCN) 转为 BCP 47 格式 (zh-CN) 以创建 Locale
                val bcp47 = languageCode.replace("-r", "-")
                val locale = Locale.forLanguageTag(bcp47)
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
            val saved = sharedPref.getString("language_preference", "") ?: ""
            // 迁移旧格式到新格式（Android 资源限定符：语言-r地区）
            return migrateLanguageCode(saved, sharedPref)
        }

        private fun migrateLanguageCode(code: String, prefs: android.content.SharedPreferences): String {
            // 兼容旧版 BCP 47 格式 (en-US) 和纯语言代码 (en)
            val migrated = when (code) {
                "en", "en-US" -> LANGUAGE_ENGLISH
                "zh", "zh-CN" -> LANGUAGE_CHINESE_SIMPLIFIED
                "zh-TW" -> LANGUAGE_CHINESE_TRADITIONAL
                "ja", "ja-JP" -> LANGUAGE_JAPANESE
                "ru", "ru-RU" -> LANGUAGE_RUSSIAN
                else -> code
            }
            if (migrated != code) {
                prefs.edit().putString("language_preference", migrated).apply()
            }
            return migrated
        }
        
        fun saveLanguage(context: Context, languageCode: String) {
            val sharedPref = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("language_preference", languageCode)
                apply()
            }
        }
        
        fun getAvailableLanguages(context: android.content.Context): List<Pair<String, String>> {
            return listOf(
                Pair("", context.getString(R.string.language_default)),
                Pair(LANGUAGE_ENGLISH, context.getString(R.string.language_en)),
                Pair(LANGUAGE_CHINESE_SIMPLIFIED, context.getString(R.string.language_zh_cn)),
                Pair(LANGUAGE_CHINESE_TRADITIONAL, context.getString(R.string.language_zh_tw)),
                Pair(LANGUAGE_JAPANESE, context.getString(R.string.language_ja)),
                Pair(LANGUAGE_RUSSIAN, context.getString(R.string.language_ru))
            )
        }
        
        /**
         * 获取显示用的语言名称
         */
        fun getLanguageDisplayName(context: android.content.Context, languageCode: String): String {
            return when (languageCode) {
                LANGUAGE_ENGLISH -> context.getString(R.string.language_en)
                LANGUAGE_CHINESE_SIMPLIFIED -> context.getString(R.string.language_zh_cn)
                LANGUAGE_CHINESE_TRADITIONAL -> context.getString(R.string.language_zh_tw)
                LANGUAGE_JAPANESE -> context.getString(R.string.language_ja)
                LANGUAGE_RUSSIAN -> context.getString(R.string.language_ru)
                else -> context.getString(R.string.language_default)
            }
        }
    }
}
