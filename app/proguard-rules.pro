# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── 剥离未使用的 Google Play Services / datatransport 残留 ──
# 如果 Gradle exclude 未能完全阻止传递依赖，R8 会在 release 构建时彻底移除
-dontwarn com.google.android.datatransport.**
-dontwarn com.google.android.gms.common.api.GoogleApiActivity