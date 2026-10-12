@file:Suppress("DEPRECATION")
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

// ===== 按项目修改 =====
val appName = "CodeScan"         // 产物名称前缀
val baseVersionCode = 300        // 基础版本号
val baseVersionName = "4.8"      // 基础版本名
val backVersionCode = 384        // git 不可用时的回退提交数量
// =====================

fun Project.gitCommitCount(): Int = try {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
} catch (_: Exception) { backVersionCode }

fun Project.gitHash(): String = try {
    providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }
        .standardOutput.asText.get().trim()
} catch (_: Exception) {
    SimpleDateFormat("MMddHHmm").format(Date())
}

val appVersionCode = baseVersionCode + gitCommitCount()
val appVersionName = "${baseVersionName}.${gitCommitCount()}.${gitHash()}"

tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
    // 先取局部变量：doLast 闭包直接引用脚本级属性会捕获脚本对象引用，导致配置缓存序列化校验失败
    val logTaskName = name
    val logAppName = appName
    val logVersionName = appVersionName
    val logVersionCode = appVersionCode
    doLast {
        println(">>> $logAppName-[$logTaskName]: $logVersionName($logVersionCode) <<<")
    }
}

android {
    namespace = "me.huidoudour.qrcode.scan"
    compileSdk = 37

    defaultConfig {
        applicationId = "me.huidoudour.QRCode.scan"
        minSdk = 28
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 不分包：单个 APK 同时内置 arm64-v8a 与 x86_64 原生库（二者必须都存在）
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    val useSignKey = rootProject.hasProperty("storeFile") &&
        rootProject.hasProperty("storePassword") &&
        rootProject.hasProperty("keyAlias") &&
        rootProject.hasProperty("keyPassword")

    signingConfigs {
        if (useSignKey) {
            create("sign_key") {
                storeFile = file(rootProject.property("storeFile") as String)
                storePassword = rootProject.property("storePassword") as String
                keyAlias = rootProject.property("keyAlias") as String
                keyPassword = rootProject.property("keyPassword") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("sign_key")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("sign_key")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf(
            "HardcodedText",           // 允许硬编码文本(调试阶段)
            "SetTextI18n",             // 允许文本拼接
            "DefaultLocale",           // 允许默认Locale
            "SdCardPath",              // 允许硬编码路径(系统工具)
            "UseTomlInstead",          // 暂不强制使用版本目录
            "ObsoleteSdkInt",          // 允许过时的SDK版本检查
            "UnusedResources",         // 允许未使用资源(可能被动态引用)
            "Overdraw",                // 允许过度绘制
            "UselessParent",           // 允许冗余父布局
            "Autofill",                // 不强制自动填充提示
            "FragmentTagUsage",        // 允许使用fragment标签
            "GradleDependency",        // 不强制更新依赖
            "NewerVersionAvailable"    // 不强制更新到最新版本
        )
        checkOnly += setOf(
            "NotSibling",              // 必须检查布局引用错误
            "DuplicateIds",            // 必须检查重复ID
            "UnknownId"                // 必须检查未知ID引用
        )
    }
}

androidComponents {
    onVariants { variant ->
        // <名称>-<versionName>-<versionCode>-<Release/Debug>.apk
        val buildTypeLabel = variant.buildType.orEmpty().replaceFirstChar { it.uppercaseChar() }
        val outputName = "${appName}-${appVersionName}-${appVersionCode}-${buildTypeLabel}.apk"
        variant.outputs.forEach { output ->
            output.outputFileName.set(outputName)
        }
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    
    // 二维码扫描库
    implementation(libs.mlkit.barcode.scanning)
    
    // Guava for ListenableFuture (CameraX required)
    implementation(libs.guava)
    implementation(libs.androidx.concurrent.futures)
    
    // 必要的CameraX依赖
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    
    // Room数据库依赖
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    
    // ZXing二维码生成库
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    //MT管理器文件提供器
    implementation(libs.mt.data.files.provider)
}