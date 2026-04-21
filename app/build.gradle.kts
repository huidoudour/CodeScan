plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "me.huidoudour.QRCode.scan"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.huidoudour.QRCode.scan"
        minSdk = 28
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 337
        versionName = "3.3.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 多架构配置
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("config") {
            // 如果在工作流环境中使用命令行参数传入签名信息，则使用这些参数
            if (System.getProperty("android.injected.signing.store.file") != null) {
                storeFile = file(System.getProperty("android.injected.signing.store.file"))
                storePassword = System.getProperty("android.injected.signing.store.password")
                keyAlias = System.getProperty("android.injected.signing.key.alias")
                keyPassword = System.getProperty("android.injected.signing.key.password")
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("config")
        }
        debug {
            isMinifyEnabled = false
            // Debug模式只在有签名参数时才使用签名配置
            if (System.getProperty("android.injected.signing.store.file") != null) {
                signingConfig = signingConfigs.getByName("config")
            }
        }
    }
    packaging {
        // 支持 16 KB 页面大小的 ARM 二进制文件对齐
        jniLibs {
            useLegacyPackaging = false
            // 启用页面对齐工具
            keepDebugSymbols += "**/*.so"
        }
        resources.pickFirsts += "lib/arm64-v8a/libbarhopper_v3.so"
        resources.pickFirsts += "lib/arm64-v8a/libimage_processing_util_jni.so"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
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
    debugImplementation(libs.mt.data.files.provider.debug)
    implementation(libs.mt.data.files.provider)

    // SQLite Android
    debugImplementation(libs.sqlite.android)
    implementation(libs.sqlite.android)
}