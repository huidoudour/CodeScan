plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

kapt {
    correctErrorTypes = true
}

android {
    namespace = "me.huidoudour.QRCode.scan"
    compileSdk = 34

    defaultConfig {
        applicationId = "me.huidoudour.QRCode.scan"
        minSdk = 28
        targetSdk = 34
        versionCode = 313
        versionName = "3.1.3"

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

    // 配置输出文件名称
    bundle {
        density {
            enableSplit = false
        }
        abi {
            enableSplit = false
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
    kotlinOptions {
        jvmTarget = "17"
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
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // 必要的CameraX依赖
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Room数据库依赖
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // ZXing二维码生成库
    implementation("com.google.zxing:core:3.5.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    //MT管理器文件提供器
    debugImplementation("com.github.L-JINBIN:MTDataFilesProvider:v1.0.0")
    implementation("com.github.L-JINBIN:MTDataFilesProvider:v1.0.0")
}